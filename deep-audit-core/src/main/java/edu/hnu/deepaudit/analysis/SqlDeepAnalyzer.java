package edu.hnu.deepaudit.analysis;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlSchemaStatVisitor;
import com.alibaba.druid.util.JdbcConstants;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlDeepAnalyzer {

    private static final Pattern USER_ID_HINT_PATTERN = Pattern.compile("/\\* user_id:.*? \\*/");

    public static SqlFeatures analyze(String sql) {
        SqlFeatures features = new SqlFeatures();
        if (sql == null || sql.trim().isEmpty()) {
            return features;
        }

        // 1. Preprocessing: Remove hints (e.g., /* user_id:xxx */) to avoid parsing errors or noise
        String cleanSql = USER_ID_HINT_PATTERN.matcher(sql).replaceAll("").trim();
        if (cleanSql.isEmpty()) return features;

        try {
            // 2. Parse AST
            List<SQLStatement> statements = SQLUtils.parseStatements(cleanSql, JdbcConstants.MYSQL);
            if (statements.isEmpty()) return features;

            SQLStatement stmt = statements.get(0);
            
            // 3. Use Druid Visitor for statistics
            MySqlSchemaStatVisitor visitor = new MySqlSchemaStatVisitor();
            stmt.accept(visitor);

            // --- Basic Structural Features ---
            features.tableCount = visitor.getTables().size();
            features.columnCount = visitor.getColumns().size();
            features.conditionCount = visitor.getConditions().size();
            features.groupByCount = visitor.getGroupByColumns().size();
            features.orderByCount = visitor.getOrderByColumns().size();
            
            // --- Advanced Risk Features ---
            
            // A. JOIN Complexity
            features.joinCount = visitor.getRelationships().size();

            // B. Dangerous Operations (Always True)
            features.hasAlwaysTrueCondition = checkAlwaysTrue(stmt);
            
            // C. Nested Depth
            features.nestedLevel = countSubQueries(stmt);

        } catch (Exception e) {
            // Parse Error is a risk signal itself (e.g. truncated SQL, non-standard syntax)
            features.parseError = true;
        }
        return features;
    }

    private static boolean checkAlwaysTrue(SQLStatement stmt) {
        String s = stmt.toString().toLowerCase();
        // Simple check for 1=1 patterns typical in injection
        // A more robust way would be visiting SQLBinaryOpExpr
        return s.contains("1 = 1") || s.contains("1=1");
    }
    
    private static int countSubQueries(SQLStatement stmt) {
        // Simple heuristic: count 'select' occurrences - 1 (main select)
        String sql = stmt.toString().toLowerCase();
        int count = 0; 
        int idx = 0; 
        while ((idx = sql.indexOf("select", idx)) != -1) { 
            count++; 
            idx += 6; 
        } 
        return Math.max(0, count - 1); 
    }

    /** 
     * Feature Carrier Class
     */ 
    public static class SqlFeatures { 
        public int tableCount = 0; 
        public int columnCount = 0; 
        public int joinCount = 0; 
        public int conditionCount = 0; 
        public int groupByCount = 0;   
        public int orderByCount = 0;   
        public int nestedLevel = 0;    
        public boolean hasAlwaysTrueCondition = false; 
        public boolean parseError = false; 
        
        // Convert to float[] for ONNX Model
        // ORDER MUST MATCH PYTHON TRAINING:
        // ['hour_of_day', 'is_workday', 'log_row_count', 'log_affected_rows', 'log_exec_time', 'freq_1min', 'sql_type_weight',
        //  'condition_count', 'join_count', 'nested_level', 'has_always_true', 'client_app_risk', 'error_code_risk']
        // This method provides only the AST part. FeatureExtractor combines them.
    } 
}

package edu.hnu.deepaudit.analysis;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlSchemaStatVisitor;
import com.alibaba.druid.stat.TableStat;
import com.alibaba.druid.util.JdbcConstants;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class SqlDeepAnalyzer {

    private static final Pattern USER_ID_HINT_PATTERN = Pattern.compile("/\\* user_id:.*? \\*/");

    public static SqlFeatures analyze(String sql) {
        SqlFeatures features = new SqlFeatures();
        if (sql == null || sql.trim().isEmpty()) {
            return features;
        }

        String cleanSql = USER_ID_HINT_PATTERN.matcher(sql).replaceAll("").trim();
        if (cleanSql.isEmpty()) return features;

        try {
            List<SQLStatement> statements = SQLUtils.parseStatements(cleanSql, JdbcConstants.MYSQL);
            if (statements.isEmpty()) return features;

            SQLStatement stmt = statements.get(0);

            MySqlSchemaStatVisitor visitor = new MySqlSchemaStatVisitor();
            stmt.accept(visitor);

            // --- 提取表名 (关键修复) ---
            for (TableStat.Name tableName : visitor.getTables().keySet()) {
                features.tableNames.add(tableName.getName());
            }

            features.tableCount = visitor.getTables().size();
            features.columnCount = visitor.getColumns().size();
            features.conditionCount = visitor.getConditions().size();
            features.groupByCount = visitor.getGroupByColumns().size();
            features.orderByCount = visitor.getOrderByColumns().size();
            features.joinCount = visitor.getRelationships().size();
            features.hasAlwaysTrueCondition = checkAlwaysTrue(stmt);
            features.nestedLevel = countSubQueries(stmt);

        } catch (Exception e) {
            features.parseError = true;
        }
        return features;
    }

    private static boolean checkAlwaysTrue(SQLStatement stmt) {
        String s = stmt.toString().toLowerCase();
        return s.contains("1 = 1") || s.contains("1=1");
    }

    private static int countSubQueries(SQLStatement stmt) {
        String sql = stmt.toString().toLowerCase();
        int count = 0;
        int idx = 0;
        while ((idx = sql.indexOf("select", idx)) != -1) {
            count++;
            idx += 6;
        }
        return Math.max(0, count - 1);
    }

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

        // 🔥 新增：存储解析出的表名
        public Set<String> tableNames = new HashSet<>();
    }
}
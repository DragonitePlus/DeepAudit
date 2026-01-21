package edu.hnu.deepaudit.interception;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlSchemaStatVisitor;
import com.alibaba.druid.stat.TableStat;
import com.alibaba.druid.util.JdbcConstants;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Perception Layer: SQL Parser
 * Uses Alibaba Druid to parse SQL into AST and extract metadata.
 */


@Component
public class SqlParserUtils {
    // 手动定义 log 变量
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SqlParserUtils.class);

    /**
     * Extract table names from SQL
     * @param sql Raw SQL string
     * @return Set of table names
     */
    public Set<String> extractTableNames(String sql) {
        try {
            // 1. Parse SQL to Statements
            List<SQLStatement> statements = SQLUtils.parseStatements(sql, JdbcConstants.MYSQL);
            
            if (statements.isEmpty()) {
                return Set.of();
            }

            // 2. Visit AST with SchemaStatVisitor
            MySqlSchemaStatVisitor visitor = new MySqlSchemaStatVisitor();
            for (SQLStatement statement : statements) {
                statement.accept(visitor);
            }

            // 3. Get Tables
            Map<TableStat.Name, TableStat> tables = visitor.getTables();
            return tables.keySet().stream()
                    .map(TableStat.Name::getName)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("Failed to parse SQL: {}", sql, e);
            return Set.of();
        }
    }

    /**
     * Get SQL Operation Type (SELECT, UPDATE, INSERT, DELETE)
     * @param sql Raw SQL string
     * @return Operation Type String
     */
    public String getOperationType(String sql) {
        try {
             List<SQLStatement> statements = SQLUtils.parseStatements(sql, JdbcConstants.MYSQL);
             if (!statements.isEmpty()) {
                 return statements.get(0).getClass().getSimpleName().replace("SQL", "").replace("Statement", "").toUpperCase();
             }
        } catch (Exception e) {
            // ignore
        }
        return "UNKNOWN";
    }
}

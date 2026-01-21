INSERT INTO sys_user (username, email, phone) VALUES ('Alice', 'alice@example.com', '13800138000');
INSERT INTO sys_user (username, email, phone) VALUES ('Bob', 'bob@example.com', '13900139000');
INSERT INTO sys_user (username, email, phone) VALUES ('Charlie', 'charlie@example.com', '13700137000');

INSERT INTO sys_risk_rule (rule_name, regex, score) VALUES ('Phone Number', '1[3-9]\\d{9}', 10);
INSERT INTO sys_risk_rule (rule_name, regex, score) VALUES ('Email', '[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}', 5);

package edu.hnu.deepaudit.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import edu.hnu.deepaudit.interception.SqlAuditInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration
@MapperScan(basePackages = "edu.hnu.deepaudit.mapper.biz", sqlSessionTemplateRef = "businessSqlSessionTemplate")
public class MyBatisBizConfig {

    @Autowired
    private SqlAuditInterceptor sqlAuditInterceptor;
    
    @Autowired
    private MybatisPlusInterceptor mybatisPlusInterceptor;

    @Bean(name = "businessSqlSessionFactory")
    public SqlSessionFactory businessSqlSessionFactory(@Qualifier("businessDataSource") DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        
        // 关键：只在 Business DataSource 上注入拦截器
        // 这样 System DataSource 的查询（审计日志、风控配置）就不会被拦截，彻底解决递归问题
        // 同时加入分页拦截器
        bean.setPlugins(new Interceptor[]{sqlAuditInterceptor, mybatisPlusInterceptor});
        
        return bean.getObject();
    }

    @Bean(name = "businessTransactionManager")
    public DataSourceTransactionManager businessTransactionManager(@Qualifier("businessDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "businessSqlSessionTemplate")
    public SqlSessionTemplate businessSqlSessionTemplate(@Qualifier("businessSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}

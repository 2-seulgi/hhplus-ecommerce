package com.hhplus.be.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {
    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(System.getenv("DB_URL") != null ?
                System.getenv("DB_URL") : "jdbc:mysql://localhost:3306/ecommerce?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul");
        dataSource.setUsername(System.getenv("DB_USER") != null ?
                System.getenv("DB_USER") : "root");

        // 비밀번호가 있을 때만 설정 (환경 변수가 있을 때만)
        String password = System.getenv("DB_PASSWORD");
        if (password != null && !password.isEmpty()) {
            dataSource.setPassword(password);
        }

        return dataSource;
    }

}

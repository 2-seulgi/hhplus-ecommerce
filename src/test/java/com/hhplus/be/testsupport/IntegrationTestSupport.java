package com.hhplus.be.testsupport;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ExtendWith(SpringExtension.class)
public abstract class IntegrationTestSupport {

    @Container
    protected static final MySQLContainer<?> MYSQL_CONTAINER =
            new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("hhplus")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        System.out.println("### [DynamicPropertySource] MySQLContainer starting...");

        registry.add("spring.datasource.url", () -> {
            String url = MYSQL_CONTAINER.getJdbcUrl(); // 이 시점에 컨테이너가 떠야 함
            System.out.println("### [DynamicPropertySource] spring.datasource.url = " + url);
            return url;
        });
        registry.add("spring.datasource.username", () -> {
            String user = MYSQL_CONTAINER.getUsername();
            System.out.println("### [DynamicPropertySource] spring.datasource.username = " + user);
            return user;
        });
        registry.add("spring.datasource.password", () -> {
            String pw = MYSQL_CONTAINER.getPassword();
            System.out.println("### [DynamicPropertySource] spring.datasource.password = " + pw);
            return pw;
        });
        registry.add("spring.datasource.driver-class-name", () -> {
            String driver = MYSQL_CONTAINER.getDriverClassName();
            System.out.println("### [DynamicPropertySource] spring.datasource.driver-class-name = " + driver);
            return driver;
        });

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

}

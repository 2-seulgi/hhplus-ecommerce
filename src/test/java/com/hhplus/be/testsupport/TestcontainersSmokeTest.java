package com.hhplus.be.testsupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TestcontainersSmokeTest extends IntegrationTestSupport {

    @Autowired
    DataSource dataSource;

    @Test
    @DisplayName("Testcontainers MySQL 컨테이너가 정상 기동되고 DB 커넥션을 얻을 수 있다")
    void testcontainersRunningAndConnectable() throws Exception {
        // 1) 컨테이너가 실제로 떠 있는지 확인
        assertThat(MYSQL_CONTAINER.isRunning()).isTrue();

        // 2) 스프링 DataSource로 연결 시도
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            System.out.println("### Connected URL = " + url);
            assertThat(url).contains("jdbc:mysql://");
        }
    }
}

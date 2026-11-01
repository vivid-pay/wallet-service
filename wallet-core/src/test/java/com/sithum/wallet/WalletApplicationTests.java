package com.sithum.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@Import(WalletApplicationTests.PostgresConfig.class)
class WalletApplicationTests {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void contextLoads() {
    }

    @Test
    void flywayOwnsTheWalletSchemaNotPublic() {
        Integer history = jdbc.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_schema = 'wallet' and table_name = 'flyway_schema_history'",
                Integer.class);
        assertThat(history).isEqualTo(1);
    }
}

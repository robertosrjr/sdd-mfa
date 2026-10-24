package com.mfa.stepup.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Same concurrency/security assertions against the production database dialect when Docker is available. */
@Testcontainers(disabledWithoutDocker=true)
class PostgreSQLConcurrentExecutionTest extends ConcurrentExecutionTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",postgres::getJdbcUrl);
        registry.add("spring.datasource.username",postgres::getUsername);
        registry.add("spring.datasource.password",postgres::getPassword);
        registry.add("spring.datasource.driver-class-name",() -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform",() -> "org.hibernate.dialect.PostgreSQLDialect");
    }
}

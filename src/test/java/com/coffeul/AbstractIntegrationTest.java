package com.coffeul;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 실제 MySQL 8.4 컨테이너(Testcontainers)로 띄운 통합 테스트 베이스 (rules.py TEST_STRATEGY).
 * 컨테이너는 전체 테스트 실행에서 한 번만 뜨는 싱글턴이다 (Testcontainers 공식 권장 패턴) —
 * 클래스마다 새로 띄우면 로컬 Colima VM 메모리(약 4GB)에서 두 개가 동시에 못 버틴다.
 * JVM 종료 시 Ryuk가 정리하므로 명시적으로 stop()하지 않는다.
 */
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    // serverTimezone=UTC — application.yml의 운영 접속 문자열과 맞춘다. 없으면 JVM 로컬 시간대(KST 등)로
    // Instant를 변환해서, 테스트에서 raw JDBC로 넣은 시각과 JPA(Hibernate, UTC)로 읽은 시각이 어긋난다.
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("coffeul")
            .withUsername("coffeul")
            .withPassword("coffeul")
            .withUrlParam("serverTimezone", "UTC")
            .withUrlParam("characterEncoding", "UTF-8");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}

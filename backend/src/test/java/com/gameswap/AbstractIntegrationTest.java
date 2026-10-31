package com.gameswap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);
    private static final PostgreSQLContainer<?> POSTGIS_CONTAINER;
    private static final boolean DOCKER_AVAILABLE;

    static {
        boolean dockerUp = false;
        PostgreSQLContainer<?> container = null;
        try {
            dockerUp = DockerClientFactory.instance().isDockerAvailable();
            if (dockerUp) {
                DockerImageName postgisImage = DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                    .asCompatibleSubstituteFor("postgres");
                container = new PostgreSQLContainer<>(postgisImage)
                    .withDatabaseName("gameswap_test")
                    .withUsername("gameswap")
                    .withPassword("gameswap_test");
                container.start();
            }
        } catch (Throwable t) {
            log.info("Docker not available for Testcontainers, falling back to local PostGIS database: {}", t.getMessage());
            dockerUp = false;
        }
        DOCKER_AVAILABLE = dockerUp;
        POSTGIS_CONTAINER = container;
    }

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        if (DOCKER_AVAILABLE && POSTGIS_CONTAINER != null) {
            registry.add("spring.datasource.url", POSTGIS_CONTAINER::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGIS_CONTAINER::getUsername);
            registry.add("spring.datasource.password", POSTGIS_CONTAINER::getPassword);
        } else {
            String testDbUrl = System.getenv().getOrDefault(
                "TEST_DB_URL",
                "jdbc:postgresql://localhost:5433/gameswap_test"
            );
            String testDbUser = System.getenv().getOrDefault("TEST_DB_USER", "gameswap");
            String testDbPass = System.getenv().getOrDefault("TEST_DB_PASSWORD", "gameswap_dev_pass");
            registry.add("spring.datasource.url", () -> testDbUrl);
            registry.add("spring.datasource.username", () -> testDbUser);
            registry.add("spring.datasource.password", () -> testDbPass);
        }
        registry.add("gameswap.auth.expose-dev-otp-in-response", () -> "true");
    }
}

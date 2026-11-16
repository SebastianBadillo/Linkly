package io.github.sebastianbadillo.linkly;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
	}

	// No dedicated Valkey container class: a generic one is enough. name = "valkey" tells
	// @ServiceConnection to set spring.data.valkey.host/port to this container's random port.
	@Bean
	@ServiceConnection(name = "valkey")
	GenericContainer<?> valkeyContainer() {
		return new GenericContainer<>(DockerImageName.parse("valkey/valkey:8-alpine")).withExposedPorts(6379);
	}

}

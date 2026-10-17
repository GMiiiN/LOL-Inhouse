package com.lolinhouse;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 역할: 테스트용 MySQL 8.4 컨테이너를 띄우고 접속 정보를 앱에 자동 연결한다(@ServiceConnection).
 * 컨테이너 OS 시간대를 일부러 Asia/Seoul로 둔다: 앱이 UTC로 저장·조회하는지(서버 시간대에 영향받지 않는지) 검증하기 위해서다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer(DockerImageName.parse("mysql:8.4")).withEnv("TZ", "Asia/Seoul");
	}

}

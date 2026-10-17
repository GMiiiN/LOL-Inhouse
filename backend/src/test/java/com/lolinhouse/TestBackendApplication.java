package com.lolinhouse;

import org.springframework.boot.SpringApplication;

/**
 * 개발용 보조 진입점. 로컬 DB 없이 Testcontainers MySQL을 붙여 앱을 띄운다(./gradlew bootTestRun).
 */
public class TestBackendApplication {

	/** BackendApplication의 main에 TestcontainersConfiguration을 더해 실행한다. */
	public static void main(String[] args) {
		SpringApplication.from(BackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

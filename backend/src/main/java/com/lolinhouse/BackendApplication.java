package com.lolinhouse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 백엔드 진입점. 이 클래스가 있는 com.lolinhouse 패키지 아래를 스캔해 빈을 등록하고 자동 설정을 켠다.
 */
@SpringBootApplication
public class BackendApplication {

	/** java -jar 또는 ./gradlew bootRun으로 실행될 때 호출된다. */
	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}

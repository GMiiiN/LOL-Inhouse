# 역할: 실행용 이미지. 1단계에서 JDK 21 + Gradle로 jar를 만들고, 2단계 런타임에는 jar만 담는다(데스크탑에 Java 불필요).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# Windows에서 받은 gradlew의 줄바꿈·실행 권한 문제를 피한다
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
# 의존성 먼저 받아 두면 소스만 바뀐 재빌드가 빠르다
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test && cp "$(ls build/libs/*.jar | grep -v -- -plain.jar)" /app.jar

# JRE 이미지는 jdk.random 모듈이 없어 앱의 RandomGenerator(L32X64MixRandom)가 기동 실패한다 → JDK 이미지를 런타임으로 쓴다
FROM eclipse-temurin:21-jdk
# 앱이 기본값 ../data/images 대신 compose가 넘긴 /app/data/... 를 쓴다. 비루트 사용자로 실행
RUN useradd --system --uid 10001 --home-dir /app app && mkdir -p /app/data && chown -R app /app
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
USER app
EXPOSE 8080
# 업로드·DB 시간 처리는 앱 설정(UTC 저장, 화면 Asia/Seoul)이 정하므로 컨테이너 시간대는 건드리지 않는다
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

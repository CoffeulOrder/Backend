# 로컬 개발용 이미지 (docker-compose.yml에서 사용). 운영 배포용이 아니다 — 운영은 AWS 구성 확정 후 따로 정한다.

# 1단계: 빌드. pom과 wrapper를 먼저 복사해서 의존성 레이어를 캐시한다(소스만 바뀌면 다운로드를 다시 안 한다).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw --batch-mode --no-transfer-progress dependency:go-offline
COPY src src
RUN ./mvnw --batch-mode --no-transfer-progress -DskipTests package \
    && cp target/*.jar /workspace/app.jar

# 2단계: 실행. JRE만 담는다.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM gradle:8.10-jdk22 AS builder

# 루트 프로젝트 복사 (settings.gradle이 포함된 위치로 이동)
WORKDIR /buildroot
COPY ../.. /buildroot
WORKDIR /buildroot

# Gradle 빌드
RUN gradle --no-daemon :modules:bootstrap:api-payment-gateway:bootJar -x test

# 실행 이미지
FROM eclipse-temurin:22-jdk
WORKDIR /app
COPY --from=builder /buildroot/modules/bootstrap/api-payment-gateway/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

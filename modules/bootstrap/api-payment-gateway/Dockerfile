FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY build/libs/api-payment-gateway-*.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]

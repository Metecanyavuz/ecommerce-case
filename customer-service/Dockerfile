FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/customer-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]

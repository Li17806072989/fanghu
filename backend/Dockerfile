FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/fanghu-platform-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

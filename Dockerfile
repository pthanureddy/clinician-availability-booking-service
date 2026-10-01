FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/clinician-availability-booking-service-1.0.0.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]

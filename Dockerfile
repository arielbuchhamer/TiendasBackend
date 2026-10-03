# ─── Build ───
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q package -DskipTests && java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination target/extraido

# ─── Runtime ───
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Usuario sin privilegios: si la app se compromete, no es root dentro del contenedor
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/extraido/dependencies/ ./
COPY --from=build /app/target/extraido/spring-boot-loader/ ./
COPY --from=build /app/target/extraido/snapshot-dependencies/ ./
COPY --from=build /app/target/extraido/application/ ./
USER app
EXPOSE 8080
# La JVM usa hasta el 75% de la memoria del contenedor
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

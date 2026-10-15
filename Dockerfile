# syntax=docker/dockerfile:1
# Build: Maven + JDK 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Dependencies first so code-only changes reuse this layer
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package && cp target/*.jar /src/app.jar

# Run: JRE 21 only
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app && mkdir -p /app/uploads && chown -R app /app
COPY --from=build /src/app.jar app.jar
USER app
# Fits a 512 MB instance (Render free/starter): heap ≤ 60% of the limit, serial GC (smallest footprint),
# capped metaspace, smaller thread stacks
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -Xss512k"
# Render sets PORT; application.properties reads ${PORT:8080}
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

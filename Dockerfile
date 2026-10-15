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
# capped metaspace, smaller thread stacks; C1-only JIT starts much faster on a fraction of a CPU
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -Xss512k -XX:TieredStopAtLevel=1"
# Class Data Sharing: extract the jar and record loaded classes in a training run that stops right after the
# context refresh (no seeding, no outbound calls); the jar path must match the ENTRYPOINT exactly — cuts startup so Render's port scan does not time out
RUN java -Djarmode=tools -jar app.jar extract --destination application \
 && java -XX:ArchiveClassesAtExit=/app/application/app.jsa -Dspring.context.exit=onRefresh -jar /app/application/app.jar
# Render sets PORT; application.properties reads ${PORT:8080}
EXPOSE 8080
ENTRYPOINT ["java", "-XX:SharedArchiveFile=/app/application/app.jsa", "-jar", "/app/application/app.jar"]

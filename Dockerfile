# syntax=docker/dockerfile:1

# ---- build stage: Maven + JDK 21, so no local JDK is needed ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Resolve dependencies first so this layer is cached until a pom changes.
COPY pom.xml ./
COPY wallet-core/pom.xml wallet-core/
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -pl wallet-core dependency:go-offline

COPY wallet-core/src wallet-core/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl wallet-core package -DskipTests \
    && cp wallet-core/target/wallet-core-*.jar /workspace/app.jar

# ---- runtime stage: JRE only, non-root ----
FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 wallet \
    && useradd --system --uid 10001 --gid wallet --no-create-home --shell /usr/sbin/nologin wallet
WORKDIR /app
COPY --from=build --chown=wallet:wallet /workspace/app.jar app.jar
USER 10001:10001

# Configuration comes only from environment variables (DB_URL, DB_USER, DB_PASSWORD,
# DB_SCHEMA, SUPPORTED_CURRENCIES). Nothing is set here.
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

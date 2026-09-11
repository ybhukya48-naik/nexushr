# ============================================================
# NexusHR Backend Deployment Image
# ============================================================

FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /build

COPY backend/pom.xml ./pom.xml

RUN mvn -B -Dmaven.wagon.http.retryHandler.count=5 dependency:go-offline

COPY backend/src ./src

RUN mvn -B -DskipTests package


FROM eclipse-temurin:25-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 nexushr

COPY --from=builder /build/target/nexushr-backend-0.1.0.jar /app/app.jar

RUN chown nexushr:nexushr /app/app.jar

USER 10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

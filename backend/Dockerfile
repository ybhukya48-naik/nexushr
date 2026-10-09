# ============================================================
# NexusHR Production Docker Build
# ============================================================

FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

COPY pom.xml .

COPY src ./src

RUN mvn -B -DskipTests package


FROM eclipse-temurin:17-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --uid 10001 nexushr

COPY --from=builder /build/target/nexushr-backend-0.1.0.jar /app/app.jar

RUN chown nexushr:nexushr /app/app.jar

USER 10001

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod
ENV TZ=Asia/Kolkata
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx320m -XX:MaxMetaspaceSize=128m -Duser.timezone=Asia/Kolkata"

ENTRYPOINT ["java", "-jar", "app.jar"]

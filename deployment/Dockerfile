FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace/app

COPY gradlew gradle/ build.gradle settings.gradle gradle.properties ./
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon || true

COPY . .
RUN ./gradlew :app-service:bootJar -x test --no-daemon


FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
RUN apk add --no-cache curl tzdata && \
    addgroup -S app && adduser -S -G app app
USER app

ENV SPRING_PROFILES_ACTIVE=docker \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    APP_PORT=8081

COPY --from=builder /workspace/app/applications/app-service/build/libs/*.jar /opt/app/app.jar
EXPOSE ${APP_PORT}
ENTRYPOINT ["sh","-c","java $JAVA_TOOL_OPTIONS -jar /opt/app/app.jar --server.port=${APP_PORT}"]

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
ENV MAVEN_OPTS="-Xmx512m"
COPY docker/maven-settings.xml /root/.m2/settings.xml
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package
RUN cp target/agent-backend-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre
RUN groupadd -r app && useradd -r -g app app
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
RUN mkdir -p /data/files && chown -R app:app /app /data/files
USER app
EXPOSE 8081
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

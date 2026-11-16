# ---- Stage 1: build the jar (full JDK + Maven wrapper) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy only what Maven needs to resolve dependencies first.
# This layer is cached and reused until pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Source changes often, so it comes last. Tests are skipped: CI already runs them.
COPY src src
RUN ./mvnw -B -q package -DskipTests && cp target/*.jar app.jar

# ---- Stage 2: run it (small JRE, no Maven, no source code) ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Don't run as root: if the app is ever compromised, the attacker is not root in the container.
RUN useradd --system --uid 1001 linkly
USER linkly

COPY --from=build /workspace/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

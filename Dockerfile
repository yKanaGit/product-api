####
# Stage 1: Build
####
FROM registry.access.redhat.com/ubi8/openjdk-17:1.18 AS builder

USER root
WORKDIR /build

# Copy Maven files
COPY pom.xml .
COPY src ./src

# Build the application
RUN mvn package -DskipTests

####
# Stage 2: Runtime
####
FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:1.18

WORKDIR /deployments

# Copy the built application
COPY --from=builder /build/target/quarkus-app/lib/ ./lib/
COPY --from=builder /build/target/quarkus-app/*.jar ./
COPY --from=builder /build/target/quarkus-app/app/ ./app/
COPY --from=builder /build/target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8080
USER 185

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]

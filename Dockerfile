# Builds the frontend and the backend into one image.
#
# Three stages. Only the last one ships: the Node and Maven toolchains exist
# during the build and are then thrown away, so the compilers, caches and
# source code never reach the running container.
#
#   node  -> static assets
#   maven -> jar, with the assets baked in
#   jre   -> the image that actually runs
#
# Build:  docker build -t sift .
# Run:    docker compose up -d


# ---------------------------------------------------------------------------
# Stage 1 - frontend
# ---------------------------------------------------------------------------
FROM node:22-alpine AS frontend

WORKDIR /web

# Dependency manifests first, sources second. Docker caches each instruction
# and invalidates everything after the first change: copying sources up front
# would re-run npm ci on every edit to a component.
COPY web/package.json web/package-lock.json ./

# ci, not install: installs exactly what the lock file pins and fails if the
# two disagree. install would silently resolve new versions, which is how a
# build starts producing something different from what was tested.
RUN npm ci

COPY web/ ./
RUN npm run build


# ---------------------------------------------------------------------------
# Stage 2 - backend
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS backend

WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Same caching idea as npm ci above: dependencies change rarely, source changes
# constantly, so resolving them is its own layer.
#
# Allowed to fail. go-offline is a cache warm-up, not a correctness step, and
# it is known to trip over plugin resolution in ways that do not affect the
# actual build. Letting it break the image would be optimising into an outage.
RUN ./mvnw -B dependency:go-offline || true

COPY src/ src/

# Spring Boot serves anything under static/ from the classpath root, so the
# built frontend and the API end up on the same origin and CORS never applies.
COPY --from=frontend /web/dist/ src/main/resources/static/

# Tests are skipped here on purpose. They need Testcontainers, which needs a
# Docker daemon, which does not exist inside a Docker build.
#
# That is not a gap: CI runs the full suite before this image is ever built.
# The pipeline is the gate, not the Dockerfile.
# No -q here: if this fails, the output is the only thing that says why.
RUN ./mvnw -B package -DskipTests


# ---------------------------------------------------------------------------
# Stage 3 - runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# A JRE, not a JDK: no compiler, no debugger, nothing that helps an attacker
# who gets a shell. Roughly 180 MB instead of 450 MB.

# Run as an unprivileged user. Containers run as root unless told otherwise,
# and root in the container is root on the host kernel if anything escapes.
RUN addgroup -S sift && adduser -S sift -G sift

WORKDIR /app
COPY --from=backend --chown=sift:sift /build/target/*.jar app.jar

USER sift
EXPOSE 8080

# BusyBox provides wget, so the check needs no extra packages.
#
# start-period covers the cold start and Flyway migrations; without it the
# container is reported unhealthy during a startup that is going fine.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# The JVM reads the container's memory limit rather than the host's, so it is
# left to size its own heap. Hard-coding -Xmx means re-tuning every time the
# container limit changes.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# syntax=docker/dockerfile:1

# Global build args (usable in FROM instructions)
ARG JDK_VERSION=21
ARG DOCKER_REGISTRY=docker.io

FROM ${DOCKER_REGISTRY}/maven:3.9.11-eclipse-temurin-${JDK_VERSION} AS build
WORKDIR /workspace

ENV MAVEN_FLAGS="-P-assembly -P-test-environment -Denforcer.skip=true -Dcheckstyle.skip=true -Dlicense.skip=true -Dxml.skip=true"

COPY pom.xml ./
RUN mvn -nsu -ntp dependency:go-offline ${MAVEN_FLAGS}

COPY src ./src
RUN mvn -nsu -ntp package ${MAVEN_FLAGS}

FROM ${DOCKER_REGISTRY}/eclipse-temurin:${JDK_VERSION}-jre
WORKDIR /app

RUN apt-get update \
	&& apt-get install -y --no-install-recommends jq \
	&& rm -rf /var/lib/apt/lists/* \
	&& useradd --create-home parer \
	&& chown -R parer:parer /app
COPY --from=build --chown=parer:parer /workspace/target/*.jar /app/app.jar

COPY --chown=parer:parer config/ ./config/
ENV CONFIG_DIR="/app/config"

RUN mkdir logs && chown parer:parer logs
ENV LOG_FILE="/app/logs/parer.log"

# Schema SQL del worker — consumato dal PreSync Job del chart parer
# (helm-charts/parer/templates/db-schema-job.yaml) tramite initContainer che
# legge questa path dalla stessa immagine. Single source of truth: schema e
# codice Java versionati insieme nello stesso commit/tag.
COPY --chown=parer:parer etc/sql /app/etc/sql

USER parer

# No default command: provide it at runtime (e.g. via Kubernetes command/args or kubectl exec).


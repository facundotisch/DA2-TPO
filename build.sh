#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
COMPOSE_FILE="$ROOT_DIR/backend/docker-compose.build.yml"
COMPOSE_PROJECT_NAME=logired-build
BUILD_MODE=none
BUILD_DB="logired_build_$$"
export COMPOSE_PROJECT_NAME

cleanup() {
    case "$BUILD_MODE" in
        docker)
            docker compose -f "$COMPOSE_FILE" down --remove-orphans
            ;;
        local-postgres)
            PGPASSWORD=12345 dropdb -h 127.0.0.1 -U postgres --if-exists "$BUILD_DB"
            ;;
    esac
}

trap cleanup EXIT
if docker info >/dev/null 2>&1; then
    BUILD_MODE=docker
    docker compose -f "$COMPOSE_FILE" up --detach --wait
    DATABASE_URL=jdbc:postgresql://localhost:5433/logired
elif command -v pg_isready >/dev/null 2>&1 \
        && command -v createdb >/dev/null 2>&1 \
        && command -v dropdb >/dev/null 2>&1 \
        && pg_isready -h 127.0.0.1 -p 5432 >/dev/null 2>&1; then
    BUILD_MODE=local-postgres
    PGPASSWORD=12345 createdb -h 127.0.0.1 -U postgres "$BUILD_DB"
    DATABASE_URL="jdbc:postgresql://localhost:5432/$BUILD_DB"
else
    echo "Se requiere Docker activo o PostgreSQL local con herramientas client para crear una base temporal." >&2
    exit 1
fi

cd "$ROOT_DIR/backend"
SPRING_DATASOURCE_URL="$DATABASE_URL" \
SPRING_DATASOURCE_USERNAME=postgres \
SPRING_DATASOURCE_PASSWORD=12345 \
    ./mvnw --batch-mode --no-transfer-progress clean verify

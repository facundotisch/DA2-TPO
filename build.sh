#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
COMPOSE_FILE="$ROOT_DIR/backend/docker-compose.build.yml"
COMPOSE_PROJECT_NAME=logired-build
BUILD_MODE=none
BUILD_DB="logired_build_$$"
export COMPOSE_PROJECT_NAME

# Base efímera solo para los tests: contraseña aleatoria, nunca escrita en el repositorio.
BUILD_DB_PASSWORD=$(openssl rand -hex 16 2>/dev/null || od -An -tx1 -N16 /dev/urandom | tr -d ' \n')
export BUILD_DB_PASSWORD

cleanup() {
    case "$BUILD_MODE" in
        docker)
            docker compose -f "$COMPOSE_FILE" down --remove-orphans
            ;;
        local-postgres)
            dropdb -h 127.0.0.1 -U "${PGUSER:-postgres}" --if-exists "$BUILD_DB"
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
    # PostgreSQL local: la contraseña se toma de PGPASSWORD (variable de entorno del desarrollador).
    : "${PGPASSWORD:?Definí PGPASSWORD con la contraseña de tu PostgreSQL local}"
    BUILD_MODE=local-postgres
    BUILD_DB_PASSWORD="$PGPASSWORD"
    createdb -h 127.0.0.1 -U "${PGUSER:-postgres}" "$BUILD_DB"
    DATABASE_URL="jdbc:postgresql://localhost:5432/$BUILD_DB"
else
    echo "Se requiere Docker activo o PostgreSQL local con herramientas client para crear una base temporal." >&2
    exit 1
fi

cd "$ROOT_DIR/backend"
DB_URL="$DATABASE_URL" \
DB_USER="${PGUSER:-postgres}" \
DB_PASSWORD="$BUILD_DB_PASSWORD" \
SPRING_DATASOURCE_URL="$DATABASE_URL" \
SPRING_DATASOURCE_USERNAME="${PGUSER:-postgres}" \
SPRING_DATASOURCE_PASSWORD="$BUILD_DB_PASSWORD" \
    ./mvnw --batch-mode --no-transfer-progress clean verify

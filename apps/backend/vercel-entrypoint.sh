#!/bin/sh
set -eu

export SERVER_PORT="${PORT:-80}"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-production}"
export DATABASE_SCHEMA="${DATABASE_SCHEMA:-erp_v2}"
export DATABASE_SSL_MODE="${DATABASE_SSL_MODE:-require}"
export SESSION_COOKIE_SECURE="true"
export OIDC_PUBLIC_URL="${APP_ORIGIN%/}/auth"
export OIDC_INTERNAL_URL="${KEYCLOAK_INTERNAL_URL%/}/auth"

exec java -XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/urandom -jar /app/app.jar

#!/bin/bash
set -euo pipefail

db_host="${PGHOST_UNPOOLED:-${POSTGRES_HOST:?PostgreSQL host is required}}"
db_name="${PGDATABASE:-${POSTGRES_DATABASE:?PostgreSQL database is required}}"
db_user="${PGUSER:-${POSTGRES_USER:?PostgreSQL user is required}}"
db_password="${PGPASSWORD:-${POSTGRES_PASSWORD:?PostgreSQL password is required}}"
admin_user="${KEYCLOAK_ADMIN_USER:-${BOOTSTRAP_ADMIN_EMAIL:?Keycloak admin user is required}}"
admin_password="${KEYCLOAK_ADMIN_PASSWORD:-${BOOTSTRAP_ADMIN_PASSWORD:?Keycloak admin password is required}}"

export KC_DB=postgres
export KC_DB_URL="jdbc:postgresql://${db_host}:${PGPORT:-5432}/${db_name}?sslmode=require"
export KC_DB_USERNAME="${db_user}"
export KC_DB_PASSWORD="${db_password}"
export KC_BOOTSTRAP_ADMIN_USERNAME="${admin_user}"
export KC_BOOTSTRAP_ADMIN_PASSWORD="${admin_password}"
export KC_HTTP_PORT="${PORT:-80}"
export KC_HTTP_RELATIVE_PATH=/auth
export KC_HOSTNAME="${APP_ORIGIN%/}/auth"
export KC_HOSTNAME_STRICT=false
export KC_HEALTH_ENABLED=true
export KC_METRICS_ENABLED=true

exec /opt/keycloak/bin/kc.sh start --optimized --import-realm --http-enabled=true --proxy-headers=xforwarded

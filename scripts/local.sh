#!/usr/bin/env bash
# Levanta el back en local con las variables de .env.<nombre> en lugar de .env.
#
#   ./scripts/local.sh <nombre>            # levanta el back
#   ./scripts/local.sh <nombre> --solo-db  # solo levanta la base propia
#
# Si el archivo tiene spring.docker.compose.enabled=false, se usa un contenedor <nombre>-postgres con PGPORT,
# PGDATABASE, PGUSER y PGPASSWORD del mismo archivo (se crea la primera vez y queda prendido al cortar el back).
set -euo pipefail

cd "$(dirname "$0")/.."

NOMBRE="${1:-}"
ARCHIVO=".env.$NOMBRE"

if [[ -z "$NOMBRE" || ! -f "$ARCHIVO" ]]; then
  [[ -n "$NOMBRE" ]] && echo "No existe $ARCHIVO"
  echo "Uso: $0 <nombre> [--solo-db]"
  exit 1
fi

valor() { grep -E "^$1=" "$ARCHIVO" | tail -1 | cut -d= -f2-; }

if [[ "$(valor spring.docker.compose.enabled)" == "false" ]]; then
  CONTENEDOR="$NOMBRE-postgres"
  PUERTO="$(valor PGPORT)"; BASE="$(valor PGDATABASE)"; USUARIO="$(valor PGUSER)"; CLAVE="$(valor PGPASSWORD)"
  if [[ -z "$PUERTO" || -z "$BASE" || -z "$USUARIO" || -z "$CLAVE" ]]; then
    echo "Faltan PGPORT, PGDATABASE, PGUSER o PGPASSWORD en $ARCHIVO"
    exit 1
  fi
  if ! docker inspect "$CONTENEDOR" >/dev/null 2>&1; then
    docker run -d --name "$CONTENEDOR" \
      -e POSTGRES_DB="$BASE" -e POSTGRES_USER="$USUARIO" -e POSTGRES_PASSWORD="$CLAVE" \
      -p "127.0.0.1:$PUERTO:5432" -v "$CONTENEDOR-datos:/var/lib/postgresql" \
      postgres:18-alpine >/dev/null
  else
    docker start "$CONTENEDOR" >/dev/null
  fi
  until docker exec "$CONTENEDOR" pg_isready -U "$USUARIO" -d "$BASE" >/dev/null 2>&1; do sleep 1; done
fi

[[ "${2:-}" == "--solo-db" ]] && exit 0

export SPRING_PROFILES_ACTIVE=local
export SPRING_CONFIG_IMPORT="optional:file:$ARCHIVO[.properties]"
exec ./mvnw -q spring-boot:run

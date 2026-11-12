#!/bin/sh
# Una base por servicio en el mismo Postgres. Solo corre la primera vez (con el volumen vacio).
set -e
for base in c15_salas c15_gameplay c15_social; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -c "CREATE DATABASE $base"
done

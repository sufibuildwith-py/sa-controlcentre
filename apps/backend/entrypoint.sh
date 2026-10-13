#!/bin/sh
set -e

if [ -n "$DATABASE_URL" ] && [ "${DATABASE_URL#jdbc:}" = "$DATABASE_URL" ]; then
  export DATABASE_URL="jdbc:$DATABASE_URL"
fi

exec java $JAVA_OPTS -Dserver.port="${PORT:-8080}" -Dserver.address=0.0.0.0 -jar /app/app.jar

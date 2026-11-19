#!/usr/bin/env bash
# =============================================================================
# reset.sh - Resetea la base de datos y reinicia el backend (Sprint 3)
# =============================================================================
# Bugs corregidos respecto a la copia de sprint2:
#   1. Ruta del repo: apuntaba a un directorio inexistente. Ahora REPO_DIR es
#      configurable y por defecto apunta al repo real (backend + cf, sin barra).
#   2. Contenedor: era "backendcf-test-db" (no existe). El real es "backendcf-db".
#   3. Credenciales: era role "postgres" / db "backendcf". El real es el rol
#      "bookly_test" y la base "bookly_test" (puerto 55432 en el host).
#   4. "kill -9 de todos los java" mataba procesos ajenos: ahora se usa un
#      PID file propio y, como respaldo, se mata solo lo que escucha en el
#      puerto de la app Y cuyo cmdline contenga "backendcf".
#   5. El JAR se levanta con JWT_SECRET y MFA_ENCRYPTION_KEY leidos del .env
#      del repo (no constantes inventadas) y --notification.email.provider=logging.
# =============================================================================
set -u

# ---------- Configuracion ---------------------------------------------------
REPO_DIR="${REPO_DIR:-/home/mista/Escritorio/udea/2026-II/calidad-software/Proyecto-FE/backendcf}"
DB_CONTAINER="${DB_CONTAINER:-backendcf-db}"
DB_ROLE="${DB_ROLE:-bookly_test}"
DB_NAME="${DB_NAME:-bookly_test}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-55432}"
APP_PORT="${APP_PORT:-8080}"
PID_FILE="${PID_FILE:-/tmp/backendcf-sprint3.pid}"
APP_LOG="${APP_LOG:-/tmp/backendcf-sprint3.log}"
JAR_PATH="${JAR_PATH:-$REPO_DIR/target/backendcf-0.0.1-SNAPSHOT.jar}"

# ---------- Funciones --------------------------------------------------------
psql_qa() {
    docker exec -i "$DB_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$DB_ROLE" -d "$DB_NAME" "$@"
}

read_env_value() {
    # read_env_value <CLAVE> <archivo .env>
    local key="$1" file="$2"
    [[ -f "$file" ]] || return 1
    grep -E "^${key}=" "$file" | head -1 | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'$//"
}

# Detiene solo la instancia previa de este script (nunca otros procesos java).
stop_previous_app() {
    if [[ -f "$PID_FILE" ]]; then
        local old_pid
        old_pid=$(cat "$PID_FILE" 2>/dev/null || true)
        if [[ -n "$old_pid" ]] && kill -0 "$old_pid" 2>/dev/null; then
            kill "$old_pid" 2>/dev/null || true
            for _ in {1..20}; do
                kill -0 "$old_pid" 2>/dev/null || break
                sleep 0.5
            done
            kill -9 "$old_pid" 2>/dev/null || true
            echo "  proceso previo $old_pid detenido (PID file)"
        fi
        rm -f "$PID_FILE"
    fi

    # Respaldo: matar SOLO el java que escucha en el puerto de la app.
    local pids pid cmd
    pids=$(ss -ltnp 2>/dev/null | awk -v p=":$APP_PORT" '$4 ~ p {print}' \
        | grep -oP 'pid=\K[0-9]+' | sort -u)
    for pid in $pids; do
        cmd=$(ps -p "$pid" -o args= 2>/dev/null || true)
        if [[ "$cmd" == *backendcf* ]]; then
            kill -9 "$pid" 2>/dev/null || true
            echo "  proceso $pid en el puerto $APP_PORT detenido"
        fi
    done
}

wait_for_app() {
    for i in {1..90}; do
        local code
        code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${APP_PORT}/api/v1/servicios" 2>/dev/null)
        if [[ "$code" == "200" ]]; then
            echo "App arriba despues de ${i}s"
            return 0
        fi
        sleep 1
    done
    return 1
}

# ---------- 1) Reset de la base de datos -------------------------------------
echo "[1/4] Limpiando el esquema public de '$DB_NAME' (contenedor $DB_CONTAINER)..."
if ! docker ps --format '{{.Names}}' | grep -qx "$DB_CONTAINER"; then
    echo "ERROR: el contenedor '$DB_CONTAINER' no esta corriendo."
    echo "       Contenedores disponibles:"
    docker ps -a --format '{{.Names}} ({{.Status}})' | sed 's/^/       - /'
    exit 1
fi
psql_qa -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;" 2>&1 | tail -2

# ---------- 2) Detener la app previa ----------------------------------------
echo "[2/4] Deteniendo la app previa..."
stop_previous_app
sleep 1

# ---------- 3) Levantar el JAR ----------------------------------------------
echo "[3/4] Arrancando el JAR..."
if [[ ! -f "$JAR_PATH" ]]; then
    echo "ERROR: no existe el JAR '$JAR_PATH'."
    echo "       Compila con: cd '$REPO_DIR' && ./mvnw -DskipTests package"
    exit 1
fi

ENV_FILE="$REPO_DIR/.env"
JWT_SECRET_VALUE=$(read_env_value JWT_SECRET "$ENV_FILE")
MFA_KEY_VALUE=$(read_env_value MFA_ENCRYPTION_KEY "$ENV_FILE")

if [[ -z "$JWT_SECRET_VALUE" ]]; then
    echo "ERROR: no se encontro JWT_SECRET en '$ENV_FILE'."
    exit 1
fi
if [[ -z "$MFA_KEY_VALUE" ]]; then
    echo "ERROR: no se encontro MFA_ENCRYPTION_KEY en '$ENV_FILE'."
    exit 1
fi

cd "$REPO_DIR" || exit 1
nohup env \
  JWT_SECRET="$JWT_SECRET_VALUE" \
  MFA_ENCRYPTION_KEY="$MFA_KEY_VALUE" \
  DB_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}" \
  DB_USERNAME="$DB_ROLE" \
  DB_PASSWORD="${DB_PASSWORD:-bookly_test_local_2026}" \
  java -jar "$JAR_PATH" \
  --server.port="$APP_PORT" \
  --notification.email.provider=logging \
  > "$APP_LOG" 2>&1 &
APP_PID=$!
echo "$APP_PID" > "$PID_FILE"
disown 2>/dev/null || true
echo "  app arrancada (pid $APP_PID), log en $APP_LOG"

# ---------- 4) Esperar la app ----------------------------------------------
echo "[4/4] Esperando a que la app responda..."
if wait_for_app; then
    echo "RESET OK"
    exit 0
fi

echo "ERROR: la app no respondio en el puerto $APP_PORT. Ultimas lineas del log:"
tail -40 "$APP_LOG"
exit 1

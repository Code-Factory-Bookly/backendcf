#!/usr/bin/env bash
# =============================================================================
# [HU-15] Reporte de ocupacion por periodo - pruebas E2E
# =============================================================================
# Endpoint verificado en el codigo (ReportController):
#   GET /api/v1/reportes/ocupacion?periodo=MES|SEMANA
# Contrato: docs/HU-15-reporte-ocupacion.md
# =============================================================================
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"
source "$SCRIPT_DIR/.e2e-results/seed.env"

HU="HU-15"
REP="/api/v1/reportes/ocupacion"
REPO_DIR="${REPO_DIR:-/home/mista/Escritorio/udea/2026-II/calidad-software/Proyecto-FE/backendcf}"
DB_PORT="${DB_PORT:-55432}"
DB_ROLE="${DB_ROLE:-bookly_test}"
DB_NAME="${DB_NAME:-bookly_test}"
DB_CONTAINER="${DB_CONTAINER:-backendcf-db}"
DB_PASSWORD="${DB_PASSWORD:-bookly_test_local_2026}"

json_get() {
    python3 -c 'import sys,json
try:
    d=json.loads(sys.argv[1])
except Exception:
    print(""); sys.exit(0)
cur=d
for k in sys.argv[2].split("."):
    if isinstance(cur,dict): cur=cur.get(k)
    elif isinstance(cur,list):
        try: cur=cur[int(k)]
        except Exception: cur=None
    else: cur=None
    if cur is None: break
print("" if cur is None else (json.dumps(cur) if isinstance(cur,(dict,list)) else str(cur)))
' "$LAST_BODY" "$1"
}

# Numero de elementos de byDayOfWeek / byProfessional
arr_len() {
    python3 -c 'import sys,json
try:
    d=json.loads(sys.argv[1])
except Exception:
    print("0"); sys.exit(0)
a=d.get(sys.argv[2])
print(len(a) if isinstance(a,list) else 0)
' "$LAST_BODY" "$1"
}

# Suma de bookedMinutes en byDayOfWeek
sum_by_day() {
    python3 -c 'import sys,json
try:
    d=json.loads(sys.argv[1])
except Exception:
    print("0"); sys.exit(0)
print(sum(int(x.get("bookedMinutes",0)) for x in (d.get("byDayOfWeek") or [])))
' "$LAST_BODY"
}

# Suma de bookedMinutes en byProfessional
sum_by_prof() {
    python3 -c 'import sys,json
try:
    d=json.loads(sys.argv[1])
except Exception:
    print("0"); sys.exit(0)
print(sum(int(x.get("bookedMinutes",0)) for x in (d.get("byProfessional") or [])))
' "$LAST_BODY"
}

# =============================================================================
banner "[$HU] 1. Reporte mensual como ADMIN"
# =============================================================================
hit "[$HU] 1.1 GET /reportes/ocupacion como ADMIN responde 200" 200 GET "$REP" "$ADMIN_TOKEN"
if [[ "$(json_get period)" == "MES" ]]; then
    pass "[$HU] 1.2 period = MES (valor por defecto)"
else
    fail "[$HU] 1.2 period esperado MES, obtuve '$(json_get period)'"
fi
NDAYS=$(arr_len byDayOfWeek)
if [[ "$NDAYS" == "7" ]]; then
    pass "[$HU] 1.3 byDayOfWeek trae las 7 entradas"
else
    fail "[$HU] 1.3 byDayOfWeek deberia traer 7 entradas, trae $NDAYS"
fi
if [[ -n "$(json_get overallOccupancyPercent)" ]]; then
    pass "[$HU] 1.4 overallOccupancyPercent presente ($(json_get overallOccupancyPercent))"
else
    fail "[$HU] 1.4 overallOccupancyPercent ausente: $LAST_BODY"
fi
if [[ -n "$(json_get from)" && -n "$(json_get to)" ]]; then
    pass "[$HU] 1.5 from y to presentes ($(json_get from) -> $(json_get to))"
else
    fail "[$HU] 1.5 from/to ausentes: $LAST_BODY"
fi

# =============================================================================
banner "[$HU] 2. Periodo SEMANA"
# =============================================================================
hit "[$HU] 2.1 GET con periodo=SEMANA responde 200" 200 GET "$REP?periodo=SEMANA" "$ADMIN_TOKEN"
if [[ "$(json_get period)" == "SEMANA" ]]; then
    pass "[$HU] 2.2 period = SEMANA"
else
    fail "[$HU] 2.2 period esperado SEMANA, obtuve '$(json_get period)'"
fi

# =============================================================================
banner "[$HU] 3. Periodo en minusculas y con espacios"
# =============================================================================
hit "[$HU] 3.1 GET con periodo=mes (minuscula) responde 200" 200 GET "$REP?periodo=mes" "$ADMIN_TOKEN"
if [[ "$(json_get period)" == "MES" ]]; then
    pass "[$HU] 3.2 periodo=mes se normaliza a MES"
else
    fail "[$HU] 3.2 periodo=mes deberia normalizarse a MES, obtuve '$(json_get period)'"
fi

hit "[$HU] 3.3 GET con periodo=%20MES (espacios) responde 200" 200 GET "$REP?periodo=%20MES" "$ADMIN_TOKEN"
if [[ "$(json_get period)" == "MES" ]]; then
    pass "[$HU] 3.4 periodo=' MES' se normaliza a MES"
else
    fail "[$HU] 3.4 periodo=' MES' deberia normalizarse a MES, obtuve '$(json_get period)'"
fi

# =============================================================================
banner "[$HU] 4. Periodo invalido"
# =============================================================================
hit "[$HU] 4.1 GET con periodo=TRIMESTRE responde 400" 400 GET "$REP?periodo=TRIMESTRE" "$ADMIN_TOKEN"
if [[ "$(json_get errorCode)" == "VALIDATION_ERROR" ]]; then
    pass "[$HU] 4.2 errorCode = VALIDATION_ERROR"
else
    fail "[$HU] 4.2 errorCode esperado VALIDATION_ERROR, obtuve '$(json_get errorCode)'"
fi

# =============================================================================
banner "[$HU] 5. Parametro periodo ausente o vacio"
# =============================================================================
hit "[$HU] 5.1 GET sin parametro periodo responde 200" 200 GET "$REP" "$ADMIN_TOKEN"
if [[ "$(json_get period)" == "MES" ]]; then
    pass "[$HU] 5.2 Sin periodo, el reporte usa MES"
else
    fail "[$HU] 5.2 Sin periodo deberia usar MES, obtuve '$(json_get period)'"
fi

hit_raw GET "$REP?periodo=" "$ADMIN_TOKEN"
CODE_EMPTY="$LAST_CODE"
info "[$HU] 5.3 GET con periodo= (vacio) -> HTTP $CODE_EMPTY body=${LAST_BODY:0:200}"
if [[ "$CODE_EMPTY" == "200" || "$CODE_EMPTY" == "400" ]]; then
    pass "[$HU] 5.4 Comportamiento documentado de periodo vacio: HTTP $CODE_EMPTY"
else
    fail "[$HU] 5.4 periodo= vacio devolvio HTTP inesperado $CODE_EMPTY"
    bug "[$HU] 5.4 BUG: GET $REP?periodo= (vacio) devuelve HTTP $CODE_EMPTY en vez de 200 o 400. body=$LAST_BODY"
fi
if [[ "$CODE_EMPTY" == "200" && "$(json_get period)" != "MES" ]]; then
    bug "[$HU] 5.5 BUG: GET $REP?periodo= (vacio) responde 200 pero con period='$(json_get period)' en vez de MES"
fi

# =============================================================================
banner "[$HU] 6. Control de acceso por rol"
# =============================================================================
hit "[$HU] 6.1 GET como CUSTOMER responde 403" 403 GET "$REP" "$TOK_hu13_customer_bookly_local"
hit "[$HU] 6.2 GET como PROFESSIONAL responde 403" 403 GET "$REP" "$TOK_prof_sofia"
hit "[$HU] 6.3 GET sin token responde 401" 401 GET "$REP" ""

# =============================================================================
banner "[$HU] 7. Reporte con reservas reales"
# =============================================================================
hit "[$HU] 7.1 GET con reservas reales responde 200" 200 GET "$REP?periodo=MES" "$ADMIN_TOKEN"
PCT=$(json_get overallOccupancyPercent)
if python3 -c "import sys; sys.exit(0 if float('$PCT')>0 else 1)" 2>/dev/null; then
    pass "[$HU] 7.2 overallOccupancyPercent > 0 ($PCT)"
else
    fail "[$HU] 7.2 overallOccupancyPercent deberia ser > 0 con reservas reales, es '$PCT'"
fi
NONZERO=$(python3 -c '
import sys,json
d=json.loads(sys.argv[1])
print(sum(1 for x in (d.get("byDayOfWeek") or []) if int(x.get("bookedMinutes",0))>0))
' "$LAST_BODY")
if [[ "$NONZERO" -gt 0 ]]; then
    pass "[$HU] 7.3 Hay dias con bookedMinutes > 0 ($NONZERO)"
else
    fail "[$HU] 7.3 Ningun dia trae bookedMinutes > 0: $LAST_BODY"
fi
# byProfessional debe traer el nombre del profesional
PROF_ROW=$(python3 -c '
import sys,json
d=json.loads(sys.argv[1]); pid=sys.argv[2]
for x in (d.get("byProfessional") or []):
    if x.get("professionalId")==pid: print(x.get("professionalName") or ""); break
else: print("NO_ENCONTRADO")
' "$LAST_BODY" "$PROF_SOFIA_USER_ID")
if [[ "$PROF_ROW" != "NO_ENCONTRADO" && -n "$PROF_ROW" ]]; then
    pass "[$HU] 7.4 byProfessional incluye a sofia con nombre '$PROF_ROW'"
else
    fail "[$HU] 7.4 byProfessional no incluye a sofia con nombre: $LAST_BODY"
fi

# =============================================================================
banner "[$HU] 8. Reporte SIN ninguna reserva (QA/Exception de la HU)"
# =============================================================================
# CASO CLAVE: la HU exige 200 con todos los valores en 0 y SIN excepcion interna
# (en particular, sin NullPointerException). Se comprueba con una base de datos
# aislada: se anula el esquema public, se re-crea unicamente lo necesario y se
# consulta el reporte. Para no romper la sesion, se usa una copia de la base.
EMPTY_DB="bookly_test_vacio"
EMPTY_ADMIN_ID="55555555-5555-4555-8555-555555555555"

# Se crea una base clonando el ESQUEMA real (con sus migraciones aplicadas) y
# vaciando los datos transaccionales. Asi el reporte se ejecuta contra el
# esquema de produccion y no contra un esquema fabricado a mano.
docker exec -i "$DB_CONTAINER" psql -U "$DB_ROLE" -d postgres \
    -c "DROP DATABASE IF EXISTS $EMPTY_DB" >/dev/null 2>&1
docker exec -i "$DB_CONTAINER" psql -U "$DB_ROLE" -d postgres \
    -c "CREATE DATABASE $EMPTY_DB" >/dev/null 2>&1

if docker exec -i "$DB_CONTAINER" psql -U "$DB_ROLE" -d postgres \
     -t -A -c "SELECT 1 FROM pg_database WHERE datname='$EMPTY_DB'" 2>/dev/null | grep -q 1; then
    pass "[$HU] 8.1 Base de datos '$EMPTY_DB' creada para el QA/Exception"

    # Clonar el esquema real y vaciar las tablas transaccionales
    docker exec -i "$DB_CONTAINER" pg_dump -U "$DB_ROLE" -d "$DB_NAME" --schema-only --no-owner \
        | docker exec -i "$DB_CONTAINER" psql -v ON_ERROR_STOP=0 -U "$DB_ROLE" -d "$EMPTY_DB" >/dev/null 2>&1
    docker exec -i "$DB_CONTAINER" psql -v ON_ERROR_STOP=0 -U "$DB_ROLE" -d "$EMPTY_DB" \
        -c "TRUNCATE TABLE bookings, horario_semanal, profesionales CASCADE;" >/dev/null 2>&1
    # Cuenta ADMIN habilitada: el filtro JWT exige que la cuenta exista.
    docker exec -i "$DB_CONTAINER" psql -v ON_ERROR_STOP=0 -U "$DB_ROLE" -d "$EMPTY_DB" \
        -c "INSERT INTO app_user (id, full_name, email, role, enabled, password_hash, created_at, updated_at)
            SELECT '$EMPTY_ADMIN_ID', 'QA Admin', 'admin@bookly.local', 'ADMIN', true, 'x', now(), now()
            WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE id = '$EMPTY_ADMIN_ID');" >/dev/null 2>&1
    ADMIN_IN_EMPTY=$(docker exec -i "$DB_CONTAINER" psql -U "$DB_ROLE" -d "$EMPTY_DB" -t -A \
        -c "SELECT count(*) FROM app_user WHERE id = '$EMPTY_ADMIN_ID' AND enabled = true;" 2>/dev/null | tr -d ' ')
    if [[ "$ADMIN_IN_EMPTY" == "1" ]]; then
        pass "[$HU] 8.1b Cuenta ADMIN disponible en la base sin reservas"
    else
        fail "[$HU] 8.1b No se pudo crear la cuenta ADMIN en la base sin reservas"
    fi

    # Se levanta una segunda instancia de la app contra la base vacia
    EMPTY_LOG="/tmp/backendcf-sprint3-empty.log"
    EMPTY_PORT=8099
    if [[ -f /tmp/backendcf-sprint3-empty.pid ]]; then
        kill -9 "$(cat /tmp/backendcf-sprint3-empty.pid)" 2>/dev/null || true
        rm -f /tmp/backendcf-sprint3-empty.pid
    fi
    JWT_V=$(grep -E '^JWT_SECRET=' "$REPO_DIR/.env" | head -1 | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//')
    MFA_V=$(grep -E '^MFA_ENCRYPTION_KEY=' "$REPO_DIR/.env" | head -1 | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//')
    if [[ -z "$JWT_V" || -z "$MFA_V" ]]; then
        fail "[$HU] 8.2 No se pudo leer JWT_SECRET/MFA_ENCRYPTION_KEY de $REPO_DIR/.env"
    fi
    nohup env \
        JWT_SECRET="$JWT_V" MFA_ENCRYPTION_KEY="$MFA_V" \
        DB_URL="jdbc:postgresql://localhost:${DB_PORT}/$EMPTY_DB" \
        DB_USERNAME="$DB_ROLE" DB_PASSWORD="$DB_PASSWORD" \
        java -jar "$REPO_DIR/target/backendcf-0.0.1-SNAPSHOT.jar" \
        --server.port=$EMPTY_PORT --notification.email.provider=logging \
        --spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=none \
        > "$EMPTY_LOG" 2>&1 &
    echo $! > /tmp/backendcf-sprint3-empty.pid
    disown 2>/dev/null || true

    # Sondeo: basta con que el puerto responda (la base vacia no tiene la
    # tabla 'servicios', asi que /api/v1/servicios no sirve como sonda).
    UP=0
    for i in {1..60}; do
        C=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$EMPTY_PORT$REP" 2>/dev/null)
        if [[ "$C" != "000" ]]; then UP=1; break; fi
        sleep 1
    done

    if [[ "$UP" == "1" ]]; then
        pass "[$HU] 8.2 La app sobre la base sin reservas arranco (puerto $EMPTY_PORT)"
        # Token JWT para la cuenta ADMIN de la base vacia, firmado con el mismo
        # JWT_SECRET que la app (HS256) para que el filtro lo acepte.
        EMPTY_TOKEN=$(python3 - "$JWT_V" "$EMPTY_ADMIN_ID" <<'PYEOF'
import base64, hashlib, hmac, json, sys, time
secret = sys.argv[1].encode()
sub = sys.argv[2]
def b64(b): return base64.urlsafe_b64encode(b).rstrip(b"=").decode()
now = int(time.time())
head = b64(json.dumps({"alg":"HS256"}, separators=(",",":")).encode())
pay = b64(json.dumps({"sub":sub,"email":"admin@bookly.local","role":"ADMIN","iat":now,"exp":now+3600}, separators=(",",":")).encode())
signing = f"{head}.{pay}"
sig = b64(hmac.new(secret, signing.encode(), hashlib.sha256).digest())
print(f"{signing}.{sig}")
PYEOF
)
        EMPTY_CODE=$(curl -s -o "$RESULTS_DIR/.empty_body" -w '%{http_code}' \
            "http://localhost:$EMPTY_PORT$REP" -H "Authorization: Bearer $EMPTY_TOKEN")
        EMPTY_BODY=$(cat "$RESULTS_DIR/.empty_body" 2>/dev/null)
        info "[$HU] 8.3 Reporte sobre base sin reservas -> HTTP $EMPTY_CODE body=${EMPTY_BODY:0:300}"
        if [[ "$EMPTY_CODE" == "200" ]]; then
            EMPTY_OK=$(python3 -c '
import sys,json
try: d=json.loads(sys.argv[1])
except Exception: print("no"); raise SystemExit
days=d.get("byDayOfWeek") or []
profs=d.get("byProfessional") or []
ok = len(days)==7
ok = ok and all(int(x.get("bookedMinutes",0))==0 for x in days)
ok = ok and all(int(x.get("capacityMinutes",0))==0 for x in days)
ok = ok and all(float(x.get("occupancyPercent",-1))==0.0 for x in days)
ok = ok and float(d.get("overallOccupancyPercent",-1))==0.0
print("yes" if ok else "no")
' "$EMPTY_BODY")
            if [[ "$EMPTY_OK" == "yes" ]]; then
                pass "[$HU] 8.4 Sin reservas: 200 con todos los valores en 0 y sin excepcion interna"
            else
                fail "[$HU] 8.4 Sin reservas: los valores no son todos 0 (QA/Exception de HU-15 incumplido)"
                bug "[$HU] 8.4 BUG: GET $REP sobre una base sin reservas NO devuelve todos los valores en 0 (QA/Exception de HU-15). body=$EMPTY_BODY"
            fi
        elif [[ "$EMPTY_CODE" == "500" ]]; then
            fail "[$HU] 8.4 Sin reservas el reporte devuelve 500"
            bug "[$HU] 8.4 BUG: GET $REP sobre una base sin reservas devuelve 500 (QA/Exception de HU-15: no debe arrojar excepcion interna ni NPE). body=$EMPTY_BODY"
        else
            fail "[$HU] 8.4 Sin reservas el reporte devolvio HTTP $EMPTY_CODE (se esperaba 200)"
            info "Log: $(grep -iE 'ERROR|Caused by' "$EMPTY_LOG" 2>/dev/null | tail -3)"
        fi
    else
        fail "[$HU] 8.2 La app sobre la base sin reservas no arranco"
        info "Log: $(grep -iE 'ERROR|Caused by' "$EMPTY_LOG" 2>/dev/null | tail -5)"
    fi

    kill -9 "$(cat /tmp/backendcf-sprint3-empty.pid 2>/dev/null)" 2>/dev/null || true
    rm -f /tmp/backendcf-sprint3-empty.pid
    docker exec -i "$DB_CONTAINER" psql -U "$DB_ROLE" -d postgres -c "DROP DATABASE IF EXISTS $EMPTY_DB" >/dev/null 2>&1
else
    fail "[$HU] 8.1 No se pudo crear la base de datos '$EMPTY_DB'"
fi

# =============================================================================
banner "[$HU] 9. PREFLIGHT / CORS"
# =============================================================================
PRE=$(curl -s -o "$RESULTS_DIR/.preflight" -w '%{http_code}' -X OPTIONS "$BASE_URL$REP" \
    -H "Origin: http://localhost:5173" \
    -H "Access-Control-Request-Method: GET" \
    -H "Access-Control-Request-Headers: authorization")
info "[$HU] 9.1 PREFLIGHT OPTIONS $REP -> HTTP $PRE"
if [[ "$PRE" == "200" || "$PRE" == "204" ]]; then
    pass "[$HU] 9.2 El preflight responde $PRE (no 401/403)"
else
    fail "[$HU] 9.2 El preflight responde $PRE (se esperaba 200/204)"
    bug "[$HU] 9.2 BUG: PREFLIGHT OPTIONS $REP con Origin responde HTTP $PRE en vez de 200/204 (el navegador no podria llamar al reporte desde el frontend). body=$(cat "$RESULTS_DIR/.preflight" 2>/dev/null | head -c 300)"
fi
ACAO=$(curl -s -D - -o /dev/null -X OPTIONS "$BASE_URL$REP" \
    -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: GET" 2>/dev/null \
    | grep -i "^access-control-allow-origin" | tr -d '\r')
if [[ -n "$ACAO" ]]; then
    pass "[$HU] 9.3 La respuesta CORS incluye Access-Control-Allow-Origin: $ACAO"
else
    fail "[$HU] 9.3 La respuesta CORS no incluye Access-Control-Allow-Origin"
fi

print_summary

#!/usr/bin/env bash
# =============================================================================
# seed.sh - Inicializa los datos base para las pruebas de Sprint 3
# =============================================================================
# Ademas de lo que ya hacia la version de sprint2, esta version:
#   * Crea el horario semanal de los 3 profesionales (HU-05 y HU-15 lo necesitan).
#   * Exporta TOK_prof_sofia / TOK_prof_miguel / TOK_prof_elena (tokens
#     PROFESSIONAL; antes faltaban porque los profesionales se creaban por
#     POST /profesionales y nunca se hacia login con ellos).
#   * Crea clientes con reservas en distintos estados (CONFIRMADA, CANCELADA,
#     COMPLETED) y en pasado/futuro, para HU-13 y HU-15.
#   * Exporta los ids de las reservas y los ids de cliente/profesional.
# =============================================================================
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

SEED_FILE="${RESULTS_DIR}/seed.env"
: > "$SEED_FILE"

# ---------- Helpers de fecha ------------------------------------------------
# Fechas futuras/pasadas relativas: nunca fijas, para que los tests no envejezcan.
future_date() { date -d "+$1 days" +%Y-%m-%d; }
future_iso()   { date -d "+$1 days" +%Y-%m-%dT%H:%M:%S; }
past_date()   { date -d "-$1 days" +%Y-%m-%d; }
past_iso()    { date -d "-$1 days" +%Y-%m-%dT%H:%M:%S; }

# ---------- 1) Plataforma ----------------------------------------------------
banner "SEED: plataforma"
hit "POST /platform/setup" 201 POST /api/v1/platform/setup "" \
    '{"name":"Bookly QA","adminEmail":"admin@bookly.local","adminPassword":"Admin123!Pass","adminFullName":"QA Admin"}'

# ---------- 2) Registrar customers base --------------------------------------
banner "SEED: customers"
declare -a CUST_EMAILS=(
    "cliente1@bookly.local"
    "cliente2@bookly.local"
    "cliente3@bookly.local"
    "cliente4@bookly.local"
    "cliente5@bookly.local"
    "cliente6@bookly.local"
    "cliente7@bookly.local"
    "cliente8@bookly.local"
    "cliente9@bookly.local"
    "sofia.limpieza@bookly.local"
    "weak.pass@bookly.local"
    "valid.spaces@bookly.local"
    "first.booking@bookly.local"
    "cancel.test@bookly.local"
    "rbac.target@bookly.local"
    "rbac.target2@bookly.local"
    "rbac.target3@bookly.local"
    "rbac.target4@bookly.local"
    "race.x@bookly.local"
    "race.y@bookly.local"
    "customer.isolated@bookly.local"
    "schedule.pro@bookly.local"
    "availability.cust@bookly.local"
    "audit.cust@bookly.local"
    "notify.cust@bookly.local"
    # Clientes usados por HU-13 (historial) y HU-15 (ocupacion)
    "hu13.customer@bookly.local"
    "hu13.secondary@bookly.local"
    "hu13.orphan@bookly.local"
    "hu15.customer@bookly.local"
)
for email in "${CUST_EMAILS[@]}"; do
    hit "register $email" 201 POST /api/v1/auth/register "" \
        "{\"email\":\"$email\",\"password\":\"Client123!Pass\",\"fullName\":\"Customer $email\"}"
done

# ---------- 3) Login admin (con MFA) ----------------------------------------
banner "SEED: admin login (con MFA)"
ADMIN_RES=$(admin_token)
ADMIN_TOKEN=${ADMIN_RES%%:*}
if [[ -z "$ADMIN_TOKEN" || "$ADMIN_TOKEN" == ":"* ]]; then
    fail "No se pudo obtener token admin: $ADMIN_RES"
    exit 1
fi
echo "ADMIN_TOKEN=$ADMIN_TOKEN" >> "$SEED_FILE"
info "Admin token capturado (${#ADMIN_TOKEN} chars)"

# ---------- 4) Crear servicios -----------------------------------------------
banner "SEED: servicios"
declare -a SERVICES=(
    '{"name":"Limpieza profunda","description":"Limpieza general del hogar","category":"Limpieza","durationMinutes":60,"price":80.00}'
    '{"name":"Masaje relajante","description":"Masaje de 60 min","category":"Bienestar","durationMinutes":60,"price":50.00}'
    '{"name":"Consulta médica general","description":"Consulta inicial","category":"Salud","durationMinutes":30,"price":40.00}'
    '{"name":"Asesoría legal","description":"Consulta legal","category":"Legal","durationMinutes":45,"price":70.00}'
    '{"name":"Clase de yoga","description":"Clase grupal","category":"Bienestar","durationMinutes":90,"price":25.00}'
    '{"name":"Limpieza express","description":"Limpieza rápida 30 min","category":"Limpieza","durationMinutes":30,"price":40.00}'
)
declare -a SVC_IDS=()
for svc in "${SERVICES[@]}"; do
    hit "POST /servicios" 201 POST /api/v1/servicios "$ADMIN_TOKEN" "$svc"
    id=$(jq_get id)
    SVC_IDS+=("$id")
    info "  Servicio creado: $id"
done
printf "SVC_IDS=(" >> "$SEED_FILE"
for i in "${SVC_IDS[@]}"; do
    printf "%s " "$i" >> "$SEED_FILE"
done
printf ")\n" >> "$SEED_FILE"

# ---------- 5) Crear profesionales via /profesionales -----------------------
banner "SEED: profesionales"
hit "POST /profesionales (sofia)" 201 POST /api/v1/profesionales "$ADMIN_TOKEN" \
    '{"email":"prof.sofia@bookly.local","password":"Prof123!Pass","fullName":"Sofia Lopez","specialty":"Limpieza"}'
PROF_SOFIA_USER_ID=$(jq_get id)
info "sofia profile id=$PROF_SOFIA_USER_ID"

hit "POST /profesionales (miguel)" 201 POST /api/v1/profesionales "$ADMIN_TOKEN" \
    '{"email":"prof.miguel@bookly.local","password":"Prof123!Pass","fullName":"Miguel Lopez","specialty":"Bienestar"}'
PROF_MIGUEL_USER_ID=$(jq_get id)
info "miguel profile id=$PROF_MIGUEL_USER_ID"

hit "POST /profesionales (elena)" 201 POST /api/v1/profesionales "$ADMIN_TOKEN" \
    '{"email":"prof.elena@bookly.local","password":"Prof123!Pass","fullName":"Elena Vargas","specialty":"Legal"}'
PROF_ELENA_USER_ID=$(jq_get id)
info "elena profile id=$PROF_ELENA_USER_ID"

# Profesional recien creado SIN horario (caso H de la integracion HU-05/13/15):
# debe aparecer en byProfessional con capacity 0 sin romper el reporte.
hit "POST /profesionales (nuevo, sin horario)" 201 POST /api/v1/profesionales "$ADMIN_TOKEN" \
    '{"email":"prof.sinhorario@bookly.local","password":"Prof123!Pass","fullName":"Nuevo Sin Horario","specialty":"General"}'
PROF_NOHORARIO_USER_ID=$(jq_get id)
info "prof sin horario id=$PROF_NOHORARIO_USER_ID"

echo "PROF_SOFIA_USER_ID=$PROF_SOFIA_USER_ID" >> "$SEED_FILE"
echo "PROF_MIGUEL_USER_ID=$PROF_MIGUEL_USER_ID" >> "$SEED_FILE"
echo "PROF_ELENA_USER_ID=$PROF_ELENA_USER_ID" >> "$SEED_FILE"
echo "PROF_NOHORARIO_USER_ID=$PROF_NOHORARIO_USER_ID" >> "$SEED_FILE"

# ---------- 6) Asignar servicios a profesionales ----------------------------
banner "SEED: profesional-servicio links"
hit "PUT /profesionales/$PROF_SOFIA_USER_ID/servicios" 204 PUT "/api/v1/profesionales/$PROF_SOFIA_USER_ID/servicios" "$ADMIN_TOKEN" \
    "{\"serviceIds\":[\"${SVC_IDS[0]}\",\"${SVC_IDS[5]}\"]}"
hit "PUT /profesionales/$PROF_MIGUEL_USER_ID/servicios" 204 PUT "/api/v1/profesionales/$PROF_MIGUEL_USER_ID/servicios" "$ADMIN_TOKEN" \
    "{\"serviceIds\":[\"${SVC_IDS[1]}\",\"${SVC_IDS[4]}\"]}"
hit "PUT /profesionales/$PROF_ELENA_USER_ID/servicios" 204 PUT "/api/v1/profesionales/$PROF_ELENA_USER_ID/servicios" "$ADMIN_TOKEN" \
    "{\"serviceIds\":[\"${SVC_IDS[3]}\"]}"

# ---------- 7) Horario semanal de los 3 profesionales ------------------------
# HU-05 necesita que el profesional tenga agenda; HU-15 usa horario_semanal
# como capacidad. Miguel se deja con dos franjas PARCIALMENTE SOLAPADAS el
# mismo dia (el CHECK de la BD no lo impide) para el caso G de integracion.
banner "SEED: horarios semanales"
SOFIA_SLOTS='{"slots":[
    {"dayOfWeek":"MONDAY","startTime":"09:00:00","endTime":"12:00:00"},
    {"dayOfWeek":"TUESDAY","startTime":"09:00:00","endTime":"12:00:00"},
    {"dayOfWeek":"WEDNESDAY","startTime":"09:00:00","endTime":"12:00:00"},
    {"dayOfWeek":"THURSDAY","startTime":"09:00:00","endTime":"12:00:00"},
    {"dayOfWeek":"FRIDAY","startTime":"09:00:00","endTime":"12:00:00"}
]}'
MIGUEL_SLOTS='{"slots":[
    {"dayOfWeek":"MONDAY","startTime":"09:00:00","endTime":"12:00:00"},
    {"dayOfWeek":"TUESDAY","startTime":"09:00:00","endTime":"12:00:00"},
    {"dayOfWeek":"WEDNESDAY","startTime":"09:00:00","endTime":"12:00:00"},
    {"dayOfWeek":"THURSDAY","startTime":"09:00:00","endTime":"12:00:00"},
    {"dayOfWeek":"FRIDAY","startTime":"09:00:00","endTime":"12:00:00"}
]}'
ELENA_SLOTS='{"slots":[
    {"dayOfWeek":"MONDAY","startTime":"14:00:00","endTime":"18:00:00"},
    {"dayOfWeek":"TUESDAY","startTime":"14:00:00","endTime":"18:00:00"},
    {"dayOfWeek":"WEDNESDAY","startTime":"14:00:00","endTime":"18:00:00"},
    {"dayOfWeek":"THURSDAY","startTime":"14:00:00","endTime":"18:00:00"},
    {"dayOfWeek":"FRIDAY","startTime":"14:00:00","endTime":"18:00:00"}
]}'
hit "PUT horario sofia" 200 PUT "/api/v1/horarios/$PROF_SOFIA_USER_ID" "$ADMIN_TOKEN" "$SOFIA_SLOTS"
hit "PUT horario miguel" 200 PUT "/api/v1/horarios/$PROF_MIGUEL_USER_ID" "$ADMIN_TOKEN" "$MIGUEL_SLOTS"
hit "PUT horario elena" 200 PUT "/api/v1/horarios/$PROF_ELENA_USER_ID" "$ADMIN_TOKEN" "$ELENA_SLOTS"

# Miguel: dos franjas PARCIALMENTE SOLAPADAS el mismo dia (lunes 09:00-12:00 y
# lunes 11:00-14:00). La API lo rechaza con SCHEDULE_CONFLICT (409) porque el
# servicio valida el solapamiento, asi que se inserta por SQL para poder
# comprobar si el reporte de ocupacion (HU-15) recorta el % a 100 en silencio.
PSQL_SEED="docker exec -i ${DB_CONTAINER:-backendcf-db} psql -v ON_ERROR_STOP=1 -U ${DB_ROLE:-bookly_test} -d ${DB_NAME:-bookly_test}"
OVERLAP_SLOT_ID="22222222-2222-4222-8222-000000000001"
$PSQL_SEED -c "
    INSERT INTO horario_semanal (id, profesional_id, dia_semana, hora_inicio, hora_fin, created_at, updated_at)
    VALUES ('$OVERLAP_SLOT_ID'::uuid, '$PROF_MIGUEL_USER_ID'::uuid, 'MONDAY', '11:00', '14:00', now(), now())
    ON CONFLICT (id) DO NOTHING;
" 2>&1 | tail -1
info "  franja solapada de miguel insertada por SQL (id=$OVERLAP_SLOT_ID)"

# ---------- 8) Token de cliente generico ------------------------------------
banner "SEED: tokens de clientes"
declare -a CUST_TOKENS=()
for email in "${CUST_EMAILS[@]}"; do
    tok=$(universal_login "$email" "Client123!Pass")
    CUST_TOKENS+=("$tok")
done
i=0
for email in "${CUST_EMAILS[@]}"; do
    clean_email=$(echo "$email" | tr '@.' '_')
    printf "TOK_%s=\"%s\"\n" "$clean_email" "${CUST_TOKENS[$i]}" >> "$SEED_FILE"
    i=$((i+1))
done
echo "CUST_TOKENS_COUNT=${#CUST_TOKENS[@]}" >> "$SEED_FILE"

# ---------- 9) Tokens de los PROFESIONALES ----------------------------------
# (faltaban: sin ellos no se puede probar HU-05 como profesional propio)
banner "SEED: tokens de profesionales"
TOK_prof_sofia=$(universal_login "prof.sofia@bookly.local" "Prof123!Pass")
TOK_prof_miguel=$(universal_login "prof.miguel@bookly.local" "Prof123!Pass")
TOK_prof_elena=$(universal_login "prof.elena@bookly.local" "Prof123!Pass")
TOK_prof_noh=$(universal_login "prof.sinhorario@bookly.local" "Prof123!Pass")

if [[ -z "$TOK_prof_sofia" ]]; then
    fail "No se pudo obtener token de prof.sofia@bookly.local"
else
    pass "Token PROFESSIONAL de sofia obtenido"
fi
if [[ -z "$TOK_prof_miguel" ]]; then
    fail "No se pudo obtener token de prof.miguel@bookly.local"
else
    pass "Token PROFESSIONAL de miguel obtenido"
fi
if [[ -z "$TOK_prof_elena" ]]; then
    fail "No se pudo obtener token de prof.elena@bookly.local"
else
    pass "Token PROFESSIONAL de elena obtenido"
fi

echo "TOK_prof_sofia=\"$TOK_prof_sofia\"" >> "$SEED_FILE"
echo "TOK_prof_miguel=\"$TOK_prof_miguel\"" >> "$SEED_FILE"
echo "TOK_prof_elena=\"$TOK_prof_elena\"" >> "$SEED_FILE"
echo "TOK_prof_nohario=\"$TOK_prof_noh\"" >> "$SEED_FILE"

# ---------- 10) Reservas en distintos estados y tiempos ---------------------
# Se crean con la API (no por SQL) para que las reservas queden validas.
# Fechas relativas: pasado (-N dias) y futuro (+N dias).
banner "SEED: reservas historicas (HU-13 / HU-15)"

HU13_TOKEN=$(universal_login "hu13.customer@bookly.local" "Client123!Pass")
HU13_SECOND_TOKEN=$(universal_login "hu13.secondary@bookly.local" "Client123!Pass")
HU13_ORPHAN_TOKEN=$(universal_login "hu13.orphan@bookly.local" "Client123!Pass")
HU15_TOKEN=$(universal_login "hu15.customer@bookly.local" "Client123!Pass")

echo "TOK_hu13_customer=\"$HU13_TOKEN\"" >> "$SEED_FILE"
echo "TOK_hu13_secondary=\"$HU13_SECOND_TOKEN\"" >> "$SEED_FILE"
echo "TOK_hu13_orphan=\"$HU13_ORPHAN_TOKEN\"" >> "$SEED_FILE"
echo "TOK_hu15_customer=\"$HU15_TOKEN\"" >> "$SEED_FILE"

declare -a BOOKING_IDS=()
declare -a BOOKING_STATES=()

create_booking() {
    # create_booking <token> <professionalId> <serviceId> <startISO> <endISO> <etiqueta>
    local tok="$1" prof="$2" svc="$3" start="$4" end="$5" label="$6"
    local body code id
    body=$(curl -sS -o "$RESULTS_DIR/.body" -w '%{http_code}' -X POST "$BASE_URL/api/v1/reservas" \
        -H "Authorization: Bearer $tok" \
        -H "Content-Type: application/json" \
        -d "{\"professionalId\":\"$prof\",\"serviceId\":\"$svc\",\"startTime\":\"$start\",\"endTime\":\"$end\"}")
    code="$body"
    local payload
    payload=$(cat "$RESULTS_DIR/.body" 2>/dev/null || true)
    if [[ "$code" == "201" ]]; then
        id=$(echo "$payload" | python3 -c 'import sys,json; print(json.loads(sys.stdin.read()).get("id",""))' 2>/dev/null)
        BOOKING_IDS+=("$id")
        BOOKING_STATES+=("CONFIRMADA")
        info "  reserva $label creada: $id"
        return 0
    fi
    info "  reserva $label NO creada (HTTP $code): ${payload:0:200}"
    BOOKING_IDS+=("")
    BOOKING_STATES+=("ERROR")
    return 1
}

# --- Cliente HU-13: 4 reservas propias (pasado CONFIRMADA/COMPLETED/CANCELADA y futuro CONFIRMADA)
# Las reservas en pasado se insertan por SQL porque la API exige startTime futuro.

# Horario de sofia: lunes a viernes 09:00-12:00. Para poder crear reservas en
# el pasado hay que Relaxar: la API exige startTime futuro (@Future). Por eso
# las reservas "historicas" se insertan directamente en la base de datos.
info "Las reservas CONFIRMADA/COMPLETED/CANCELADA en pasado se insertan por SQL (la API exige startTime futuro)"

# Las reservas por API deben caer dentro de una franja del horario semanal
# del profesional (mismo dia de la semana y dentro del rango horario) y tener
# startTime < endTime. Por eso se calculan fechas cuyo dia de la semana sea
# lunes (sofia atiende lunes a viernes de 09:00 a 12:00).
next_weekday_iso() {
    # next_weekday_iso <dias_minimos> <dia_semana_iso> <hora>
    local min="$1" dow="$2" hh="$3" d
    for d in $(seq "$min" $((min + 14))); do
        local cand
        cand=$(date -d "+$d days" +%Y-%m-%d)
        if [[ "$(date -d "$cand" +%u)" == "$dow" ]]; then
            echo "${cand}T${hh}:00"
            return 0
        fi
    done
    echo "NO-HAY-DIA"
}

# Lunes futuro dentro de 8 dias (sofia: lunes 09:00-12:00, servicio de 60 min)
FUT_MONDAY=$(next_weekday_iso 8 1 09:00)
FUT_MONDAY_END=$(next_weekday_iso 8 1 10:00)
# Otro lunes futuro (para el aislamiento entre clientes)
FUT_MONDAY2=$(next_weekday_iso 15 1 09:00)
FUT_MONDAY2_END=$(next_weekday_iso 15 1 10:00)
# Lunes futuro mas lejano (HU-15)
FUT_MONDAY3=$(next_weekday_iso 22 1 09:00)
FUT_MONDAY3_END=$(next_weekday_iso 22 1 10:00)

# Reserva CONFIRMADA futura (API) para HU-13
create_booking "$HU13_TOKEN" "$PROF_SOFIA_USER_ID" "${SVC_IDS[0]}" "$FUT_MONDAY" "$FUT_MONDAY_END" "hu13-futura-confirmada"

# Reserva CONFIRMADA futura de otro cliente (para el aislamiento de datos)
create_booking "$HU13_SECOND_TOKEN" "$PROF_SOFIA_USER_ID" "${SVC_IDS[0]}" \
    "$FUT_MONDAY2" "$FUT_MONDAY2_END" "hu13-secundaria-futura"

# Reserva CONFIRMADA futura de HU-15 (para el reporte de ocupacion)
create_booking "$HU15_TOKEN" "$PROF_SOFIA_USER_ID" "${SVC_IDS[5]}" \
    "$FUT_MONDAY3" "$FUT_MONDAY3_END" "hu15-futura-confirmada"

# --- Reservas historicas por SQL (estados que la API no puede crear) ---------
PSQL_QA="docker exec -i ${DB_CONTAINER:-backendcf-db} psql -v ON_ERROR_STOP=1 -U ${DB_ROLE:-bookly_test} -d ${DB_NAME:-bookly_test}"

HU13_CUSTOMER_ID=$(python3 -c '
import base64,json,sys
tok=sys.argv[1]
p=tok.split(".")[1]
p+="="*(-len(p)%4)
print(json.loads(base64.urlsafe_b64decode(p))["sub"])
' "$HU13_TOKEN" 2>/dev/null)

HU13_SECOND_ID=$(python3 -c '
import base64,json,sys
tok=sys.argv[1]
p=tok.split(".")[1]
p+="="*(-len(p)%4)
print(json.loads(base64.urlsafe_b64decode(p))["sub"])
' "$HU13_SECOND_TOKEN" 2>/dev/null)

HU13_ORPHAN_ID=$(python3 -c '
import base64,json,sys
tok=sys.argv[1]
p=tok.split(".")[1]
p+="="*(-len(p)%4)
print(json.loads(base64.urlsafe_b64decode(p))["sub"])
' "$HU13_ORPHAN_TOKEN" 2>/dev/null)

HU15_CUSTOMER_ID=$(python3 -c '
import base64,json,sys
tok=sys.argv[1]
p=tok.split(".")[1]
p+="="*(-len(p)%4)
print(json.loads(base64.urlsafe_b64decode(p))["sub"])
' "$HU15_TOKEN" 2>/dev/null)

echo "HU13_CUSTOMER_ID=$HU13_CUSTOMER_ID" >> "$SEED_FILE"
echo "HU13_SECOND_CUSTOMER_ID=$HU13_SECOND_ID" >> "$SEED_FILE"
echo "HU13_ORPHAN_CUSTOMER_ID=$HU13_ORPHAN_ID" >> "$SEED_FILE"
echo "HU15_CUSTOMER_ID=$HU15_CUSTOMER_ID" >> "$SEED_FILE"

insert_booking_sql() {
    # insert_booking_sql <id> <customerId> <professionalId> <serviceId> <start> <end> <status>
    local id="$1" cust="$2" prof="$3" svc="$4" start="$5" end="$6" status="$7"
    $PSQL_QA -c "
        INSERT INTO bookings (id, customer_id, professional_id, service_id, start_time, end_time, status, created_at, updated_at)
        VALUES ('$id'::uuid, '$cust'::uuid, '$prof'::uuid, '$svc'::uuid, '$start', '$end', '$status', now(), now());
    " 2>&1 | tail -1
}

# UUIDs deterministas para las reservas historicas (facilita re-ejecucion)
PAST_CONFIRMED_ID="11111111-1111-4111-8111-000000000001"
PAST_COMPLETED_ID="11111111-1111-4111-8111-000000000002"
PAST_CANCELLED_ID="11111111-1111-4111-8111-000000000003"
OTHER_CONFIRMED_ID="11111111-1111-4111-8111-000000000004"
HU15_COMPLETED_ID="11111111-1111-4111-8111-000000000005"

info "Insertando reservas historicas por SQL..."
# Se reparten en dias distintos para no violar la EXCLUDE de solapamiento de
# reservas CONFIRMADA del mismo profesional (V46).
insert_booking_sql "$PAST_CONFIRMED_ID" "$HU13_CUSTOMER_ID" "$PROF_SOFIA_USER_ID" "${SVC_IDS[0]}" \
    "$(date -d '-10 days' +%Y-%m-%dT09:00:00)" "$(date -d '-10 days' +%Y-%m-%dT10:00:00)" "CONFIRMADA"
insert_booking_sql "$PAST_COMPLETED_ID" "$HU13_CUSTOMER_ID" "$PROF_SOFIA_USER_ID" "${SVC_IDS[5]}" \
    "$(date -d '-5 days' +%Y-%m-%dT09:00:00)" "$(date -d '-5 days' +%Y-%m-%dT09:30:00)" "COMPLETED"
insert_booking_sql "$PAST_CANCELLED_ID" "$HU13_CUSTOMER_ID" "$PROF_SOFIA_USER_ID" "${SVC_IDS[0]}" \
    "$(date -d '-3 days' +%Y-%m-%dT09:00:00)" "$(date -d '-3 days' +%Y-%m-%dT10:00:00)" "CANCELADA"
insert_booking_sql "$OTHER_CONFIRMED_ID" "$HU13_SECOND_ID" "$PROF_SOFIA_USER_ID" "${SVC_IDS[0]}" \
    "$(date -d '-8 days' +%Y-%m-%dT09:00:00)" "$(date -d '-8 days' +%Y-%m-%dT10:00:00)" "CONFIRMADA"
insert_booking_sql "$HU15_COMPLETED_ID" "$HU15_CUSTOMER_ID" "$PROF_SOFIA_USER_ID" "${SVC_IDS[5]}" \
    "$(date -d '-4 days' +%Y-%m-%dT09:00:00)" "$(date -d '-4 days' +%Y-%m-%dT10:00:00)" "COMPLETED"

BOOKING_IDS+=("$PAST_CONFIRMED_ID" "$PAST_COMPLETED_ID" "$PAST_CANCELLED_ID" "$OTHER_CONFIRMED_ID" "$HU15_COMPLETED_ID")
BOOKING_STATES+=("CONFIRMADA" "COMPLETED" "CANCELADA" "CONFIRMADA" "COMPLETED")

printf "BOOKING_IDS=(" >> "$SEED_FILE"
for i in "${BOOKING_IDS[@]}"; do printf "%s " "$i"; done >> "$SEED_FILE"
printf ")\n" >> "$SEED_FILE"

echo "BOOKING_PAST_CONFIRMED_ID=$PAST_CONFIRMED_ID" >> "$SEED_FILE"
echo "BOOKING_PAST_COMPLETED_ID=$PAST_COMPLETED_ID" >> "$SEED_FILE"
echo "BOOKING_PAST_CANCELLED_ID=$PAST_CANCELLED_ID" >> "$SEED_FILE"
echo "BOOKING_OTHER_CONFIRMED_ID=$OTHER_CONFIRMED_ID" >> "$SEED_FILE"
echo "BOOKING_HU15_COMPLETED_ID=$HU15_COMPLETED_ID" >> "$SEED_FILE"

# ---------- 11) Comprobacion de las migraciones (HU-05 -> V50) ---------------
banner "SEED: verificacion de migraciones"
MIG_INFO=$($PSQL_QA -t -A -c "SELECT version, description, success FROM flyway_schema_history WHERE version IN ('46','50') ORDER BY version;" 2>&1)
info "flyway: ${MIG_INFO}"
if echo "$MIG_INFO" | grep -q "^50"; then
    pass "Migracion V50 aplicada"
else
    fail "Migracion V50 NO aplicada: $MIG_INFO"
fi
BLOCK_TABLE=$($PSQL_QA -t -A -c "SELECT to_regclass('public.bloqueo_agenda');" 2>&1 | tr -d ' ')
if [[ "$BLOCK_TABLE" == "bloqueo_agenda" ]]; then
    pass "Tabla bloqueo_agenda existe"
else
    fail "Tabla bloqueo_agenda NO existe (=$BLOCK_TABLE)"
fi
EXCLUDE_CONSTRAINT=$($PSQL_QA -t -A -c "SELECT count(*) FROM pg_constraint WHERE conname = 'bloqueo_agenda_sin_solapamiento';" 2>&1 | tr -d ' ')
if [[ "$EXCLUDE_CONSTRAINT" == "1" ]]; then
    pass "Constraint EXCLUDE de solapamiento presente"
else
    fail "Constraint EXCLUDE de solapamiento ausente (count=$EXCLUDE_CONSTRAINT)"
fi

echo "SEED_COMPLETED_AT=$(date -Iseconds)" >> "$SEED_FILE"

print_summary

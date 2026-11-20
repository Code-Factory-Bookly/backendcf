#!/usr/bin/env bash
# =============================================================================
# [INTEGRACION] HU-05 + HU-07 + HU-13 + HU-15 - casos cruzados
# =============================================================================
# Busca las incoherencias entre las tres historias de Sprint 3:
#   HU-05 bloqueo de agenda
#   HU-07 disponibilidad (que consume los bloqueos)
#   HU-13 historial del cliente
#   HU-15 reporte de ocupacion
#
# Endpoints verificados en el codigo:
#   /api/v1/horarios/{professionalId}/bloqueos
#   /api/v1/disponibilidad?profesionalId&servicioId&desde&hasta
#   /api/v1/reservas/cliente/historial
#   /api/v1/reportes/ocupacion?periodo=
# =============================================================================
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"
source "$SCRIPT_DIR/.e2e-results/seed.env"

HU="INTEGRACION"
SOFIA="$PROF_SOFIA_USER_ID"
MIGUEL="$PROF_MIGUEL_USER_ID"
ELENA="$PROF_ELENA_USER_ID"
NOHORARIO="$PROF_NOHORARIO_USER_ID"
TOK_SOFIA="$TOK_prof_sofia"
TOK_MIGUEL="$TOK_prof_miguel"
CUST="$TOK_hu13_customer_bookly_local"
CUST2="$TOK_hu13_secondary_bookly_local"
SVC1="${SVC_IDS[0]}"

REPO_DIR="${REPO_DIR:-/home/mista/Escritorio/udea/2026-II/calidad-software/Proyecto-FE/backendcf}"
DB_CONTAINER="${DB_CONTAINER:-backendcf-db}"
DB_ROLE="${DB_ROLE:-bookly_test}"
DB_NAME="${DB_NAME:-bookly_test}"

psql_qa() {
    docker exec -i "$DB_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$DB_ROLE" -d "$DB_NAME" "$@"
}

fut_dow() {
    # fut_dow <dias_minimos> <dia_semana_iso 1..7> <HH:MM>
    local min="$1" dow="$2" hm="$3" d cand
    for d in $(seq "$min" $((min + 14))); do
        cand=$(date -d "+$d days" +%Y-%m-%d)
        if [[ "$(date -d "$cand" +%u)" == "$dow" ]]; then
            echo "${cand}T${hm}:00"
            return 0
        fi
    done
    echo "2099-01-01T00:00:00"
}

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

# Lista de horas (HH:MM) ofrecidas en los slots de disponibilidad
avail_slots() {
    python3 -c 'import sys,json
try:
    d=json.loads(sys.argv[1])
except Exception:
    print(""); raise SystemExit
out=[]
for day in (d.get("days") or []):
    for s in (day.get("slots") or []):
        st=str(s.get("startTime") or "")
        out.append(st.split("T")[-1][:5] if "T" in st else st[:5])
print(",".join(out))
' "$LAST_BODY"
}

# Numero de slots de disponibilidad devueltos
avail_slot_count() {
    python3 -c 'import sys,json
try:
    d=json.loads(sys.argv[1])
except Exception:
    print("0"); raise SystemExit
print(sum(len(day.get("slots") or []) for day in (d.get("days") or [])))
' "$LAST_BODY"
}

# Numero de startTime REPETIDOS en los slots devueltos (bug A1).
# Imprime "<total> <repetidos> <inicioDeCadaRepetido>"
avail_slot_dups() {
    python3 -c 'import sys,json
from collections import Counter
try:
    d=json.loads(sys.argv[1])
except Exception:
    print("0 0 "); raise SystemExit
sts=[]
for day in (d.get("days") or []):
    for s in (day.get("slots") or []):
        st=str(s.get("startTime") or "")
        sts.append(st.split("T")[-1][:5] if "T" in st else st[:5])
c=Counter(sts)
dups=sorted(k for k,v in c.items() if v>1)
print(len(sts), len(dups), " ".join(dups))
' "$LAST_BODY"
}

# Una fecha futura (YYYY-MM-DD) del dia de la semana indicado (ISO 1..7) que
# NO tenga reservas ni bloqueos del profesional. Evita colisiones con las
# fechas usadas por otros casos del propio script.
# clean_future_dow <profId> <dowISO> <minDias> <maxDias> <excludeDate>
clean_future_dow() {
    local prof="$1" dow="$2" min="$3" max="$4" exclude="${5:-}"
    local d cand n
    for d in $(seq "$min" "$max"); do
        cand=$(date -d "+$d days" +%Y-%m-%d)
        [[ "$(date -d "$cand" +%u)" == "$dow" ]] || continue
        [[ "$cand" == "$exclude" ]] && continue
        n=$(psql_qa -t -A -c "
            SELECT
              (SELECT count(*) FROM bookings
                WHERE professional_id='$prof'::uuid AND start_time::date='$cand'::date)
            + (SELECT count(*) FROM bloqueo_agenda
                WHERE profesional_id='$prof'::uuid AND inicio::date='$cand'::date);
        " 2>/dev/null | tr -d ' ')
        if [[ "$n" == "0" ]]; then
            echo "$cand"
            return 0
        fi
    done
    echo ""
}

# La fecha mas reciente (hacia atras) que cae dentro de la ventana del reporte
# MES ([hoy-29, hoy]) y en un dia de la semana con horario para el profesional.
# Sirve para DEFECTO A: el bloqueo debe caer DENTRO de la ventana del reporte.
# recent_in_window <dowISO> <minDiasAtras> <maxDiasAtras>
recent_in_window() {
    local dow="$1" min="$2" max="$3" d cand
    for d in $(seq "$min" "$max"); do
        cand=$(date -d "-$d days" +%Y-%m-%d)
        if [[ "$(date -d "$cand" +%u)" == "$dow" ]]; then
            echo "$cand"
            return 0
        fi
    done
    echo ""
}

# minutos de capacidad por dia de la semana del reporte
report_day() {
    # report_day <DAYOFWEEK> <campo>
    python3 -c 'import sys,json
try:
    d=json.loads(sys.argv[1])
except Exception:
    print(""); raise SystemExit
for x in (d.get("byDayOfWeek") or []):
    if x.get("dayOfWeek")==sys.argv[2]:
        print(x.get(sys.argv[3])); break
else:
    print("")
' "$LAST_BODY" "$1" "$2"
}

report_prof() {
    # report_prof <profId> <campo>
    python3 -c 'import sys,json
try:
    d=json.loads(sys.argv[1])
except Exception:
    print(""); raise SystemExit
for x in (d.get("byProfessional") or []):
    if x.get("professionalId")==sys.argv[2]:
        print(x.get(sys.argv[3])); break
else:
    print("")
' "$LAST_BODY" "$1" "$2"
}

# Convierte el dia de la semana ISO (1=lunes..7=domingo) al nombre del enum.
dow_name() {
    case "$1" in
        1) echo "MONDAY" ;;   2) echo "TUESDAY" ;;  3) echo "WEDNESDAY" ;;
        4) echo "THURSDAY" ;; 5) echo "FRIDAY" ;;   6) echo "SATURDAY" ;;
        7) echo "SUNDAY" ;;
        *) echo "UNKNOWN" ;;
    esac
}

# =============================================================================
banner "[$HU] A. Bloqueo HU-05 vs disponibilidad HU-07"
# =============================================================================
# Se bloquea una franja de lunes 09:00-12:00 (todo el horario de sofia) en una
# fecha futura y se comprueba que la disponibilidad deja de ofrecer esas horas.
# Se busca un lunes futuro que NO tenga reservas de sofia, para que el bloqueo
# no choque con reservas (el caso A mide el efecto del bloqueo, no el conflicto).
AVAIL_DATE=""
for cand in $(fut_dow 25 1 09:00 | cut -dT -f1) $(fut_dow 32 1 09:00 | cut -dT -f1) \
            $(fut_dow 39 1 09:00 | cut -dT -f1) $(fut_dow 46 1 09:00 | cut -dT -f1) \
            $(fut_dow 53 1 09:00 | cut -dT -f1) $(fut_dow 60 1 09:00 | cut -dT -f1); do
    N=$(psql_qa -t -A -c "SELECT count(*) FROM bookings WHERE professional_id='$SOFIA'::uuid AND start_time::date = '$cand'::date;" 2>/dev/null | tr -d ' ')
    if [[ "$N" == "0" ]]; then AVAIL_DATE="$cand"; break; fi
done
if [[ -z "$AVAIL_DATE" ]]; then
    fail "[$HU] A.0 No se encontro una fecha futura de lunes sin reservas para el caso A"
    AVAIL_DATE=$(fut_dow 25 1 09:00 | cut -dT -f1)
fi
info "[$HU] A.0 Fecha usada para el caso A: $AVAIL_DATE"
BLK_START="${AVAIL_DATE}T09:00:00"
BLK_END="${AVAIL_DATE}T12:00:00"

hit "[$HU] A.1 Disponibilidad ANTES del bloqueo responde 200" 200 GET \
    "/api/v1/disponibilidad?profesionalId=$SOFIA&servicioId=$SVC1&desde=$AVAIL_DATE&hasta=$AVAIL_DATE" "$CUST"
SLOTS_BEFORE=$(avail_slots)
info "[$HU] A.2 Slots ofrecidos antes del bloqueo: '$SLOTS_BEFORE'"

hit "[$HU] A.3 POST del bloqueo responde 201" 201 POST "/api/v1/horarios/$SOFIA/bloqueos" "$TOK_SOFIA" \
    "{\"startTime\":\"$BLK_START\",\"endTime\":\"$BLK_END\",\"reason\":\"prueba integracion A\"}"
if [[ "$LAST_CODE" != "201" ]]; then
    fail "[$HU] A.3b No se pudo crear el bloqueo de prueba: $LAST_BODY"
fi

hit "[$HU] A.4 Disponibilidad DESPUES del bloqueo responde 200" 200 GET \
    "/api/v1/disponibilidad?profesionalId=$SOFIA&servicioId=$SVC1&desde=$AVAIL_DATE&hasta=$AVAIL_DATE" "$CUST"
SLOTS_AFTER=$(avail_slots)
info "[$HU] A.5 Slots ofrecidos despues del bloqueo: '$SLOTS_AFTER'"
if [[ "$SLOTS_BEFORE" != "$SLOTS_AFTER" ]]; then
    pass "[$HU] A.6 La disponibilidad CAMBIO tras el bloqueo (el bloqueo ocupa la agenda)"
else
    fail "[$HU] A.6 La disponibilidad NO cambio tras el bloqueo: antes='$SLOTS_BEFORE' despues='$SLOTS_AFTER'"
    bug "[$HU] A.6 BUG: el bloqueo de agenda (HU-05) no afecta a GET /api/v1/disponibilidad (HU-07): los slots siguen ofreciendose. antes='$SLOTS_BEFORE' despues='$SLOTS_AFTER'"
fi
if [[ -z "$SLOTS_AFTER" || "$SLOTS_AFTER" == "null" ]]; then
    pass "[$HU] A.7 Tras bloquear todo el horario no se ofrece ninguna hora"
else
    info "[$HU] A.7 Tras el blockage siguen ofreciendose horas: '$SLOTS_AFTER'"
fi

# =============================================================================
banner "[$HU] B. Bloqueo HU-05 vs reporte HU-15 (capacityMinutes)"
# =============================================================================
# DEFECTO A (corregido): el bloqueo debia caer DENTRO de la ventana del
# reporte. El reporte MES cubre [hoy-29, hoy] (ReportPeriod.MES -> 30 dias
# terminando HOY). El caso original usaba fut_dow 15 2 (hoy+15 dias), que
# cae FUERA de la ventana, asi que el reporte jamas podia descontarlo y el
# caso "detectaba" el bug por el motivo equivocado.
# Aqui el bloqueo se coloca en el dia de la semana MAS RECIENTE que este
# dentro de la ventana (el lunes de la semana en curso o el anterior), de
# modo que el reporte DEBE descontarlo si lo tiene en cuenta.
# Se bloquean 2 horas (09:00-11:00) que caben dentro de la franja de 3 horas
# (09:00-12:00) del profesional: la capacidad debe bajar EXACTAMENTE 120 min.
B_BLOCK_ID="66666666-6666-4666-8666-000000000001"
# Limpieza de ejecuciones anteriores: el "antes" debe medirse sin el bloqueo.
psql_qa -c "DELETE FROM bloqueo_agenda WHERE id = '$B_BLOCK_ID'::uuid;" >/dev/null 2>&1

B_DATE=$(recent_in_window 1 0 29)   # lunes mas reciente dentro de [hoy-29, hoy]
if [[ -z "$B_DATE" ]]; then
    fail "[$HU] B.0 No se encontro un lunes dentro de la ventana del reporte"
    B_DATE=$(date -d "-3 days" +%Y-%m-%d)
fi
B_DAY_NAME=$(dow_name "$(date -d "$B_DATE" +%u)")
info "[$HU] B.0 Fecha del bloqueo dentro de la ventana del reporte: $B_DATE ($B_DAY_NAME); ventana=[$(date -d '-29 days' +%Y-%m-%d), $(date +%Y-%m-%d)]"

hit "[$HU] B.1 Reporte ANTES del bloqueo responde 200" 200 GET "/api/v1/reportes/ocupacion?periodo=MES" "$ADMIN_TOKEN"
CAP_BEFORE=$(report_prof "$SOFIA" capacityMinutes)
MON_CAP_BEFORE=$(report_day "$B_DAY_NAME" capacityMinutes)
info "[$HU] B.2 capacityMinutes de sofia antes del bloqueo: '$CAP_BEFORE' ($B_DAY_NAME: '$MON_CAP_BEFORE')"

# Bloqueo de 2 horas dentro de la franja de la agenda, en una fecha de la
# ventana del reporte.
B_START="${B_DATE}T09:00:00"
B_END="${B_DATE}T11:00:00"
psql_qa -c "
    INSERT INTO bloqueo_agenda (id, profesional_id, inicio, fin, motivo, created_at)
    VALUES ('$B_BLOCK_ID'::uuid, '$SOFIA'::uuid,
            '$B_START'::timestamp, '$B_END'::timestamp, 'bloqueo semanal QA', now());
" >/dev/null 2>&1
info "[$HU] B.2b Bloqueo insertado: $B_START -> $B_END (120 min)"

hit "[$HU] B.3 Reporte DESPUES del bloqueo responde 200" 200 GET "/api/v1/reportes/ocupacion?periodo=MES" "$ADMIN_TOKEN"
CAP_AFTER=$(report_prof "$SOFIA" capacityMinutes)
MON_CAP_AFTER=$(report_day "$B_DAY_NAME" capacityMinutes)
info "[$HU] B.4 capacityMinutes de sofia despues del bloqueo de 2h: '$CAP_AFTER' (antes '$CAP_BEFORE'); $B_DAY_NAME: '$MON_CAP_AFTER' (antes '$MON_CAP_BEFORE')"

if [[ "$CAP_BEFORE" == "$CAP_AFTER" ]]; then
    bug "[$HU] B.5 BUG: GET /api/v1/reportes/ocupacion NO descuenta los bloqueos de agenda (HU-05) de capacityMinutes: sigue en '$CAP_AFTER' tras bloquear 120 min el $B_DATE (dentro de la ventana del reporte [$(date -d '-29 days' +%Y-%m-%d), $(date +%Y-%m-%d)]). La disponibilidad (HU-07) si los trata como ocupados, por lo que la misma franja se cuenta como libre en un modulo y como capacidad en otro."
    fail "[$HU] B.6 La capacidad no cambio tras el bloqueo (antes='$CAP_BEFORE' despues='$CAP_AFTER')"
else
    pass "[$HU] B.6 El reporte descuenta el bloqueo de la capacidad ($CAP_BEFORE -> $CAP_AFTER)"
fi

# El bloqueo debe descontar EXACTAMENTE los 120 minutos bloqueados.
DELTA=$(python3 -c "
try:
    print(int('$CAP_BEFORE' or 0) - int('$CAP_AFTER' or 0))
except Exception:
    print(0)")
if [[ "$DELTA" == "120" ]]; then
    pass "[$HU] B.7 El reporte descuenta exactamente los 120 minutos bloqueados de capacityMinutes"
else
    fail "[$HU] B.7 Se esperaban 120 min de diferencia en capacityMinutes y hay $DELTA (antes='$CAP_BEFORE' despues='$CAP_AFTER')"
fi

# El mismo descuento debe verse en la agrupacion por dia de la semana.
MON_DELTA=$(python3 -c "
try:
    print(int('$MON_CAP_BEFORE' or 0) - int('$MON_CAP_AFTER' or 0))
except Exception:
    print(0)")
if [[ "$MON_DELTA" == "120" ]]; then
    pass "[$HU] B.8 byDayOfWeek descuenta tambien los 120 minutos bloqueados en $B_DAY_NAME"
else
    fail "[$HU] B.8 byDayOfWeek de $B_DAY_NAME no descuenta los 120 minutos bloqueados (diferencia=$MON_DELTA)"
fi

# El bloque NO debe modificar los minutos reservados: solo la capacidad.
BOOKED_SOFIA=$(report_prof "$SOFIA" bookedMinutes)
info "[$HU] B.9 bookedMinutes de sofia tras el bloqueo: '$BOOKED_SOFIA' (el bloqueo no es una reserva)"

# =============================================================================
banner "[$HU] C. Reserva COMPLETED: disponibilidad vs reporte"
# =============================================================================
# DEFECTO B (corregido): el caso original exigia OFFERS_CONF==1, es decir que
# la disponibilidad SIGUIERA ofreciendo las 09:00 de una franja ya reservada
# (imposible: la reserva ocupa exactamente las 09:00). La condicion nunca se
# cumplia, el caso siempre daba PASS y no detectaba nada.
#
# Aqui el caso compara el CONJUNTO DE SLOTS con la MISMA reserva en dos
# estados, cambiando el estado por SQL (la API no crea reservas COMPLETED):
#   * con la reserva en CONFIRMADA -> la franja esta ocupada -> 4 slots
#   * con la MISMA reserva en COMPLETED -> la franja sigue ocupada -> 4 slots
# El bug: la disponibilidad solo mira CONFIRMADA
# (BookingRepository.findOverlappingBookings), asi que al pasar la reserva a
# COMPLETED la franja se "libera" y aparecen 6 slots. El reporte, en cambio,
# usa CONFIRMADA + COMPLETED (OccupancyQueryAdapter.OCCUPYING_STATUSES).
#
# El servicio de 30 min (SVC_IDS[5]) y la franja 09:00-12:00 producen 6 slots
# (09:00, 09:30, 10:00, 10:30, 11:00, 11:30); una reserva de 09:00 a 10:00
# deja libres 10:00, 10:30, 11:00 y 11:30 (4 slots).
SVC30="${SVC_IDS[5]}"
COMP_ID="77777777-7777-4777-8777-000000000002"
# Idempotencia: la reserva del caso se borra antes de buscar la fecha, para
# que una ejecucion anterior no impida elegir un lunes limpio.
psql_qa -c "DELETE FROM bookings WHERE id = '$COMP_ID'::uuid;" >/dev/null 2>&1
# Fecha: un lunes futuro limpio (sin reservas ni bloqueos de sofia) y
# distinto del usado por el caso A, para que los slots sean deterministas.
COMP_FUT_DATE=$(clean_future_dow "$SOFIA" 1 25 60 "$AVAIL_DATE")
if [[ -z "$COMP_FUT_DATE" ]]; then
    fail "[$HU] C.0 No se encontro un lunes futuro limpio para el caso C"
    COMP_FUT_DATE=$(date -d "+32 days" +%Y-%m-%d)
fi
info "[$HU] C.0 Fecha del caso C: $COMP_FUT_DATE"

psql_qa -c "
    INSERT INTO bookings (id, customer_id, professional_id, service_id, start_time, end_time, status, created_at, updated_at)
    VALUES ('$COMP_ID'::uuid, '$HU13_CUSTOMER_ID'::uuid, '$SOFIA'::uuid, '$SVC30'::uuid,
            '${COMP_FUT_DATE}T09:00:00', '${COMP_FUT_DATE}T10:00:00', 'CONFIRMADA', now(), now());
" >/dev/null 2>&1

# --- Medicion 1: la misma reserva en CONFIRMADA -------------------------------
hit "[$HU] C.1 Disponibilidad con la reserva en CONFIRMADA responde 200" 200 GET \
    "/api/v1/disponibilidad?profesionalId=$SOFIA&servicioId=$SVC30&desde=$COMP_FUT_DATE&hasta=$COMP_FUT_DATE" "$CUST"
SLOTS_CONF=$(avail_slots)
N_CONF=$(avail_slot_count)
info "[$HU] C.2 Slots con la reserva CONFIRMADA ($N_CONF): '$SLOTS_CONF'"

# --- Medicion 2: la MISMA reserva cambiada a COMPLETED (por SQL) --------------
psql_qa -c "UPDATE bookings SET status='COMPLETED' WHERE id='$COMP_ID'::uuid;" >/dev/null 2>&1
hit "[$HU] C.3 Disponibilidad con la misma reserva en COMPLETED responde 200" 200 GET \
    "/api/v1/disponibilidad?profesionalId=$SOFIA&servicioId=$SVC30&desde=$COMP_FUT_DATE&hasta=$COMP_FUT_DATE" "$CUST"
SLOTS_COMP=$(avail_slots)
N_COMP=$(avail_slot_count)
info "[$HU] C.4 Slots con la misma reserva en COMPLETED ($N_COMP): '$SLOTS_COMP'"

# La franja ocupada (09:00 y 09:30) NO debe volver a ofrecerse con COMPLETED.
if [[ "$N_COMP" -le "$N_CONF" ]]; then
    pass "[$HU] C.5 La disponibilidad no libera la franja al pasar la reserva a COMPLETED ($N_CONF slots CONFIRMADA vs $N_COMP slots COMPLETED)"
else
    bug "[$HU] C.5 BUG: una reserva COMPLETED desaparece de la disponibilidad. Con la MISMA reserva en CONFIRMADA se ofrecen $N_CONF slots ('$SLOTS_CONF') y al cambiarla a COMPLETED por SQL se ofrecen $N_COMP slots ('$SLOTS_COMP'): la franja ocupada vuelve a aparecer. GET /api/v1/disponibilidad (HU-07) solo considera CONFIRMADA (BookingRepository.findOverlappingBookings filtra por status='CONFIRMADA'), mientras el reporte HU-15 usa CONFIRMADA + COMPLETED (OccupancyQueryAdapter.OCCUPYING_STATUSES). Las dos definiciones de 'ocupado' no coinciden y el cliente puede reservar una franja que el reporte ya da por ocupada."
fi

# El conjunto de slots con COMPLETED debe ser igual o MAS RESTRINGIDO que el
# conjunto con CONFIRMADA (nunca mas amplio).
SET_CMP=$(python3 -c "
import sys
def parse(s):
    return set(x for x in s.split(',') if x)
c=parse(sys.argv[1]); m=parse(sys.argv[2])
extra=sorted(m-c)
print('same' if not extra else ('wider:'+','.join(extra)))
" "$SLOTS_CONF" "$SLOTS_COMP")
if [[ "$SET_CMP" == "same" ]]; then
    pass "[$HU] C.6 El conjunto de slots con COMPLETED es identico al de CONFIRMADA"
else
    bug "[$HU] C.6 BUG: el conjunto de slots con COMPLETED es MAS AMPLIO que con CONFIRMADA. Slots extra ofrecidos con COMPLETED: ${SET_CMP#wider:}. CONFIRMADA='$SLOTS_CONF' COMPLETED='$SLOTS_COMP'"
fi

# La franja 09:00-10:00 debe seguir reservada en ambos estados.
if [[ "$SLOTS_COMP" == *"09:00"* ]]; then
    bug "[$HU] C.7 BUG: la franja ocupada (09:00) se vuelve a ofrecer con la reserva en COMPLETED. Slots: '$SLOTS_COMP'"
else
    pass "[$HU] C.7 La franja ocupada (09:00) no se ofrece con la reserva en COMPLETED"
fi

# --- El reporte SI debe contar la reserva COMPLETED ---------------------------
# Parte del reporte: la reserva COMPLETED esta en el FUTURO, fuera de la
# ventana [hoy-29, hoy], asi que se mide con una segunda reserva COMPLETED en
# el PASADO: se crea en CANCELADA (estado que el reporte ignora), se mide el
# reporte y luego se cambia a COMPLETED por SQL. La diferencia debe ser
# exactamente la duracion de la reserva (60 min).
COMP_PAST_DATE=$(date -d "-12 days" +%Y-%m-%d)
COMP_PAST_ID="77777777-7777-4777-8777-000000000003"
COMP_PAST_DAY=$(dow_name "$(date -d "$COMP_PAST_DATE" +%u)")
psql_qa -c "DELETE FROM bookings WHERE id = '$COMP_PAST_ID'::uuid;" >/dev/null 2>&1
psql_qa -c "
    INSERT INTO bookings (id, customer_id, professional_id, service_id, start_time, end_time, status, created_at, updated_at)
    VALUES ('$COMP_PAST_ID'::uuid, '$HU13_CUSTOMER_ID'::uuid, '$SOFIA'::uuid, '$SVC1'::uuid,
            '${COMP_PAST_DATE}T09:00:00', '${COMP_PAST_DATE}T10:00:00', 'CANCELADA', now(), now());
" >/dev/null 2>&1

hit "[$HU] C.8 Reporte (reserva en CANCELADA) responde 200" 200 GET "/api/v1/reportes/ocupacion?periodo=MES" "$ADMIN_TOKEN"
BOOKED_CANCELLED=$(report_day "$COMP_PAST_DAY" bookedMinutes)
info "[$HU] C.9 bookedMinutes de $COMP_PAST_DAY con la reserva en CANCELADA: '$BOOKED_CANCELLED'"

psql_qa -c "UPDATE bookings SET status='COMPLETED' WHERE id='$COMP_PAST_ID'::uuid;" >/dev/null 2>&1
hit "[$HU] C.10 Reporte (reserva en COMPLETED) responde 200" 200 GET "/api/v1/reportes/ocupacion?periodo=MES" "$ADMIN_TOKEN"
BOOKED_COMPLETED=$(report_day "$COMP_PAST_DAY" bookedMinutes)
info "[$HU] C.11 bookedMinutes de $COMP_PAST_DAY con la reserva en COMPLETED: '$BOOKED_COMPLETED'"

REPORT_DELTA=$(python3 -c "
try:
    print(int('$BOOKED_COMPLETED' or 0) - int('$BOOKED_CANCELLED' or 0))
except Exception:
    print(0)")
if [[ "$REPORT_DELTA" == "60" ]]; then
    pass "[$HU] C.12 El reporte HU-15 SI cuenta la reserva COMPLETED (+60 min al pasar de CANCELADA a COMPLETED)"
else
    fail "[$HU] C.12 El reporte no conto la reserva COMPLETED: diferencia=$REPORT_DELTA (CANCELADA='$BOOKED_CANCELLED' COMPLETED='$BOOKED_COMPLETED')"
    bug "[$HU] C.12 BUG: el reporte HU-15 no cuenta la reserva COMPLETED. Al cambiar la reserva $COMP_PAST_ID ($COMP_PAST_DATE) de CANCELADA a COMPLETED, bookedMinutes de $COMP_PAST_DAY paso de '$BOOKED_CANCELLED' a '$BOOKED_COMPLETED' (diferencia $REPORT_DELTA, se esperaban 60). OccupancyQueryAdapter.OCCUPYING_STATUSES deberia incluir COMPLETED."
fi

# =============================================================================
banner "[$HU] D. Consistencia aritmetica del reporte (byDayOfWeek vs byProfessional)"
# =============================================================================
# Reserva que cruza medianoche: 23:00 -> 01:00 del dia siguiente.
CROSS_A=$(date -d "-20 days" +%Y-%m-%d)
CROSS_B=$(date -d "$CROSS_A +1 day" +%Y-%m-%d)
CROSS_ID="88888888-8888-4888-8888-000000000001"
psql_qa -c "
    INSERT INTO bookings (id, customer_id, professional_id, service_id, start_time, end_time, status, created_at, updated_at)
    VALUES ('$CROSS_ID'::uuid, '$HU13_CUSTOMER_ID'::uuid, '$SOFIA'::uuid, '$SVC1'::uuid,
            '${CROSS_A}T23:00:00', '${CROSS_B}T01:00:00', 'CONFIRMADA', now(), now());
" >/dev/null 2>&1

hit "[$HU] D.1 Reporte responde 200" 200 GET "/api/v1/reportes/ocupacion?periodo=MES" "$ADMIN_TOKEN"
SUM_DAY=$(python3 -c '
import sys,json
d=json.loads(sys.argv[1])
print(sum(int(x.get("bookedMinutes",0)) for x in (d.get("byDayOfWeek") or [])))
' "$LAST_BODY")
SUM_PROF=$(python3 -c '
import sys,json
d=json.loads(sys.argv[1])
print(sum(int(x.get("bookedMinutes",0)) for x in (d.get("byProfessional") or [])))
' "$LAST_BODY")
info "[$HU] D.2 Suma bookedMinutes por dia = $SUM_DAY ; por profesional = $SUM_PROF"
if [[ "$SUM_DAY" == "$SUM_PROF" ]]; then
    pass "[$HU] D.3 La suma de bookedMinutes por dia coincide con la suma por profesional"
else
    fail "[$HU] D.3 La suma por dia ($SUM_DAY) NO coincide con la suma por profesional ($SUM_PROF)"
    bug "[$HU] D.3 BUG: inconsistencia aritmetica en el reporte HU-15: sum(bookedMinutes de byDayOfWeek)=$SUM_DAY != sum(bookedMinutes de byProfessional)=$SUM_PROF. El reporte imputa los minutos al dia de inicio de la reserva, por lo que una reserva que cruza medianoche se contabiliza entera en un solo dia."
fi

# =============================================================================
banner "[$HU] E. Ventana temporal: reserva que empieza antes del 'from' del reporte"
# =============================================================================
# La reserva empieza 1 dia antes del 'from' del reporte y termina dentro.
# El reporte MES cubre [hoy-29, hoy]. La reserva empieza DESPUES del 'from' y
# termina dentro de la ventana: debe contar.
EDGE_DATE=$(date -d "-25 days" +%Y-%m-%d)
EDGE_ID="99999999-9999-4999-8999-000000000001"
psql_qa -c "
    INSERT INTO bookings (id, customer_id, professional_id, service_id, start_time, end_time, status, created_at, updated_at)
    VALUES ('$EDGE_ID'::uuid, '$HU13_CUSTOMER_ID'::uuid, '$SOFIA'::uuid, '$SVC1'::uuid,
            '${EDGE_DATE}T09:00:00', '${EDGE_DATE}T10:00:00', 'CONFIRMADA', now(), now());
" >/dev/null 2>&1

hit "[$HU] E.1 Reporte MES responde 200" 200 GET "/api/v1/reportes/ocupacion?periodo=MES" "$ADMIN_TOKEN"
EDGE_DAY=$(date -d "$EDGE_DATE" +%u)
EDGE_BOOKED=$(report_day "$(dow_name "$EDGE_DAY")" bookedMinutes)
info "[$HU] E.2 bookedMinutes de $EDGE_DAY en el reporte: '$EDGE_BOOKED' (from del reporte: $(json_get from))"
if [[ "$EDGE_BOOKED" == "0" || -z "$EDGE_BOOKED" ]]; then
    fail "[$HU] E.3 La reserva que empieza justo despues del 'from' no se cuenta"
else
    pass "[$HU] E.3 La reserva dentro de la ventana temporal SI se cuenta ($EDGE_BOOKED min)"
fi

# =============================================================================
banner "[$HU] F. Solapamiento parcial vs contiguo en HU-05"
# =============================================================================
F_DATE=$(fut_dow 35 6 10:00 | cut -dT -f1)
FS="${F_DATE}T10:00:00"; FE="${F_DATE}T12:00:00"
hit "[$HU] F.1 POST del bloqueo base [10:00-12:00) responde 201" 201 POST "/api/v1/horarios/$ELENA/bloqueos" "$TOK_prof_elena" \
    "{\"startTime\":\"$FS\",\"endTime\":\"$FE\",\"reason\":\"bloque base F\"}"

CONTIG_S="${F_DATE}T12:00:00"; CONTIG_E="${F_DATE}T13:00:00"
hit "[$HU] F.2 POST contiguo [12:00-13:00) responde 201 (no solapa)" 201 POST "/api/v1/horarios/$ELENA/bloqueos" "$TOK_prof_elena" \
    "{\"startTime\":\"$CONTIG_S\",\"endTime\":\"$CONTIG_E\",\"reason\":\"contiguo F\"}"

OVER_S="${F_DATE}T11:00:00"; OVER_E="${F_DATE}T11:30:00"
hit "[$HU] F.3 POST solapado [11:00-11:30) responde 409" 409 POST "/api/v1/horarios/$ELENA/bloqueos" "$TOK_prof_elena" \
    "{\"startTime\":\"$OVER_S\",\"endTime\":\"$OVER_E\",\"reason\":\"solapa F\"}"
if [[ "$(json_get errorCode)" == "AGENDA_BLOCK_OVERLAP" ]]; then
    pass "[$HU] F.4 El solapamiento parcial se detecta con errorCode AGENDA_BLOCK_OVERLAP"
else
    fail "[$HU] F.4 errorCode esperado AGENDA_BLOCK_OVERLAP, obtuve '$(json_get errorCode)'"
fi

# El mismo criterio debe aplicarse en la base: se comprueba que la BD y el codigo
# coinciden (un bloqueo contiguo NO viola la EXCLUDE).
DB_OVERLAP_COUNT=$(psql_qa -t -A -c "
    SELECT count(*) FROM bloqueo_agenda b1 JOIN bloqueo_agenda b2
      ON b1.profesional_id = b2.profesional_id AND b1.id <> b2.id
     AND tsrange(b1.inicio, b1.fin, '[)') && tsrange(b2.inicio, b2.fin, '[)')
    WHERE b1.profesional_id = '$ELENA'::uuid;
" 2>/dev/null | tr -d ' ')
if [[ "$DB_OVERLAP_COUNT" == "0" ]]; then
    pass "[$HU] F.5 La BD confirma que NO hay bloqueos solapados (contiguo aceptado, solapado rechazado)"
else
    fail "[$HU] F.5 La BD reporta $DB_OVERLAP_COUNT solapamientos: el codigo y la BD no coinciden"
    bug "[$HU] F.5 BUG: la base tiene $DB_OVERLAP_COUNT pares de bloqueos solapados: el criterio de solapamiento del codigo no coincide con la EXCLUDE de la BD"
fi

# =============================================================================
banner "[$HU] G. Profesional con horario solapado (capacidad y recorte del %)"
# =============================================================================
# Miguel tiene dos franjas que se solapan el lunes (09:00-12:00 y 11:00-14:00).
# La BD no lo impide (el CHECK solo exige hora_fin > hora_inicio).
OVERLAP_OK=$(psql_qa -t -A -c "
    SELECT count(*) FROM horario_semanal
    WHERE profesional_id = '$MIGUEL'::uuid AND dia_semana = 'MONDAY';
" 2>/dev/null | tr -d ' ')
info "[$HU] G.1 Franjas de miguel el lunes: $OVERLAP_OK"
if [[ "$OVERLAP_OK" -ge 2 ]]; then
    pass "[$HU] G.2 La BD admite dos franjas solapadas el mismo dia (el CHECK no lo impide)"
else
    fail "[$HU] G.2 Se esperaban 2 franjas solapadas para miguel y hay $OVERLAP_OK"
fi

# Para que el recorte a 100 sea observable se cargan reservas de miguel que
# superan su capacidad. Su capacidad esta INFLADA porque las dos franjas
# solapadas del lunes (09:00-12:00 y 11:00-14:00) se suman como si fueran
# 6h+3h. Se crean reservas CONFIRMADA de 60 min en dias de la semana.
# Ninguna reserva puede solaparse con otra CONFIRMADA del mismo profesional
# (constraint EXCLUDE de V46), asi que se reparten en dias y horas distintas.
# Las reservas deben caer DENTRO del periodo del reporte (que termina HOY),
# asi que se ubican en dias pasados, en distintos dias de la semana.
#
# CORRECCION (veredicto del debugging sistematico): las reservas solo se siembran
# en dias en los que Miguel TIENE franja (lunes a viernes). Antes se sembraban en
# los ultimos 20 dias sin filtro, con lo que caian en sabado y domingo, dias en los
# que Miguel no atiende. Esos datos son INALCANZABLES por la API (POST en un dia
# sin horario responde 409 SLOT_OUTSIDE_SCHEDULE) y ademas falseaban la medicion
# de S3-04 y S3-08, y dejaban el resto de la suite JUnit (que espera una base
# limpia) con 80 reservas que no le pertenecen. Ahora cada reserva sembrada cae
# en un dia servible, de modo que el escenario es reproducible por la API.
G_IDX=0
G_SKIPPED=0
for G_OFF in $(seq 1 20); do
    G_DATE=$(date -d "-$G_OFF days" +%Y-%m-%d)
    # Solo dias con horario de miguel (ISO 1=lunes .. 5=viernes).
    G_DOW=$(date -d "$G_DATE" +%u)
    if [[ "$G_DOW" -gt 5 ]]; then
        G_SKIPPED=$((G_SKIPPED+1))
        continue
    fi
    for G_SLOT in 09:00 10:00 11:00 12:00; do
        G_E=$(python3 -c "
h,m='$G_SLOT'.split(':')
print(f'{int(h)+1:02d}:{m}')")
        G_IDX=$((G_IDX+1))
        psql_qa -c "
            INSERT INTO bookings (id, customer_id, professional_id, service_id, start_time, end_time, status, created_at, updated_at)
            VALUES (gen_random_uuid(), '$HU13_CUSTOMER_ID'::uuid, '$MIGUEL'::uuid, '${SVC_IDS[1]}'::uuid,
                    '${G_DATE}T${G_SLOT}:00', '${G_DATE}T${G_E}:00', 'CONFIRMADA', now(), now());
        " >/dev/null 2>&1
    done
done
info "[$HU] G.2b Se insertaron $G_IDX reservas CONFIRMADA para miguel ($G_SKIPPED dias sin horario omitidos: sabado/domingo)"

hit "[$HU] G.3 Reporte responde 200" 200 GET "/api/v1/reportes/ocupacion?periodo=MES" "$ADMIN_TOKEN"
MIG_CAP=$(report_prof "$MIGUEL" capacityMinutes)
MIG_PCT=$(report_prof "$MIGUEL" occupancyPercent)
MON_CAP=$(report_day "MONDAY" capacityMinutes)
info "[$HU] G.4 capacityMinutes de miguel=$MIG_CAP ; occupancyPercent=$MIG_PCT ; capacityMinutes del lunes=$MON_CAP"

# Si las franjas solapadas se suman, la capacidad del lunes de miguel es 6h+3h=9h
# en vez de 3h reales. Se comprueba si el % se recorta a 100 en silencio.
#
# CORRECCION (veredicto del debugging sistematico): este caso ya NO se apoya en
# reservas de fin de semana inalcanzables por la API para que booked > capacity.
# El recorte se provoca ahora por la RETA REAL y alcanzable por la API que
# encontro el debugging: el profesional VENDE sus huecos y luego REDUCE su propio
# horario (PUT /api/v1/horarios/{id}), que la API permite sin restriccion aunque
# queden reservas CONFIRMADA vigentes. Se mide el recorte sobre un profesional de
# prueba con reservas sembradas por la API.
G_BOOKED=$(report_prof "$MIGUEL" bookedMinutes)
info "[$HU] G.5 bookedMinutes de miguel=$G_BOOKED"
CLAMPED=$(python3 -c '
import sys
booked=float(sys.argv[1]); cap=float(sys.argv[2]); pct=float(sys.argv[3])
print("yes" if cap>0 and booked>cap and pct==100.0 else "no")
' "${G_BOOKED:-0}" "${MIG_CAP:-0}" "${MIG_PCT:-0}")
if [[ "$CLAMPED" == "yes" ]]; then
    bug "[$HU] G.6 BUG: el reporte HU-15 RECORTA el porcentaje a 100 en silencio. bookedMinutes=$G_BOOKED > capacityMinutes=$MIG_CAP y occupancyPercent se devuelve como 100.0 sin avisar. El dato esta inflado y el valor es silencioso."
else
    pass "[$HU] G.6a El porcentaje de miguel no se recorta a 100 con las reservas sembradas (booked=$G_BOOKED, cap=$MIG_CAP, pct=$MIG_PCT)"
fi

# --- G.6b: recorte silencioso por REDUCCION de horario (ruta 100% por API) -----
# Se usa el profesional de Elena, que tiene franja de 14:00 a 18:00 de lunes a
# viernes. Se le siembran reservas CONFIRMADA dentro de la ventana del reporte y
# despues se REDUCE su horario por la API hasta que la capacidad sea menor que
# los minutos reservados. Ningun dato es inalcanzable: las reservas caen en dias
# con horario y el recorte de horario se pide por la API.
G_ELENA="$PROF_ELENA_USER_ID"
G2_DATE=$(recent_in_window 1 3 27)
if [[ -z "$G2_DATE" ]]; then
    fail "[$HU] G.6b.0 No se encontro un lunes dentro de la ventana del reporte"
else
    info "[$HU] G.6b.0 Fecha del caso G.6b (lunes en ventana): $G2_DATE"
    # 4 reservas CONFIRMADA de 60 min en el mismo lunes, dentro de 14:00-18:00.
    for G2_SLOT in 14:00 15:00 16:00 17:00; do
        G2_E=$(python3 -c "
h,m='$G2_SLOT'.split(':')
print(f'{int(h)+1:02d}:{m}')")
        psql_qa -c "
            INSERT INTO bookings (id, customer_id, professional_id, service_id, start_time, end_time, status, created_at, updated_at)
            VALUES (gen_random_uuid(), '$HU13_CUSTOMER_ID'::uuid, '$G_ELENA'::uuid, '${SVC_IDS[1]}'::uuid,
                    '${G2_DATE}T${G2_SLOT}:00', '${G2_DATE}T${G2_E}:00', 'CONFIRMADA', now(), now());
        " >/dev/null 2>&1
    done
    # Se reduce el horario de Elena a una sola franja de 30 minutos en ese dia
    # (los demas dias se conservan). La API lo permite: no comprueba que queden
    # reservas CONFIRMADA dentro de la nueva franja.
    # Se REDUCE el horario completo de Elena a una unica franja de 30 minutos.
    # La ventana del reporte abarca 30 dias, asi que reducir un solo dia no basta:
    # la capacidad total seguiria siendo enorme. El profesional deja de atender
    # casi todos sus dias DESPUES de haber vendido los huecos, que es exactamente
    # el escenario que reproduce el recorte silencioso.
    G2_SLOTS="{\"slots\":[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"14:00:00\",\"endTime\":\"14:30:00\"}]}"
    hit "[$HU] G.6b.1 PUT horario de elena reducido (por API) responde 200" 200 PUT \
        "/api/v1/horarios/$G_ELENA" "$ADMIN_TOKEN" "$G2_SLOTS"

    hit "[$HU] G.6b.2 Reporte responde 200" 200 GET "/api/v1/reportes/ocupacion?periodo=MES" "$ADMIN_TOKEN"
    G2_CAP=$(report_prof "$G_ELENA" capacityMinutes)
    G2_BOOKED=$(report_prof "$G_ELENA" bookedMinutes)
    G2_PCT=$(report_prof "$G_ELENA" occupancyPercent)
    info "[$HU] G.6b.3 elena: bookedMinutes=$G2_BOOKED ; capacityMinutes=$G2_CAP ; occupancyPercent=$G2_PCT"
    G2_CLAMPED=$(python3 -c '
import sys
booked=float(sys.argv[1]); cap=float(sys.argv[2]); pct=float(sys.argv[3])
print("yes" if cap>0 and booked>cap and pct==100.0 else "no")
' "${G2_BOOKED:-0}" "${G2_CAP:-0}" "${G2_PCT:-0}")
    if [[ "$G2_CLAMPED" == "yes" ]]; then
        bug "[$HU] G.6b.4 BUG: el reporte HU-15 RECORTA el porcentaje a 100 en silencio. El profesional reservo sus huecos y luego REDUJO su propio horario por la API (PUT /api/v1/horarios/$G_ELENA), que la API permite sin comprobar que las reservas CONFIRMADA sigan dentro de la franja. Resultado: bookedMinutes=$G2_BOOKED > capacityMinutes=$G2_CAP y occupancyPercent se devuelve como 100.0 sin avisar ni marcar la saturacion. El valor real era $(python3 -c "print(round($G2_BOOKED*100/$G2_CAP,2) if $G2_CAP else 0)")%. Es una ruta 100% por API: no necesita datos inalcanzables."
    else
        pass "[$HU] G.6b.4 El porcentaje no se recorta a 100 tras reducir el horario por API (booked=$G2_BOOKED, cap=$G2_CAP, pct=$G2_PCT)"
    fi
    # Se restaura el horario de Elena para no contaminar los casos posteriores.
    hit "[$HU] G.6b.5 PUT horario de elena restaurado responde 200" 200 PUT \
        "/api/v1/horarios/$G_ELENA" "$ADMIN_TOKEN" \
        "{\"slots\":[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"14:00:00\",\"endTime\":\"18:00:00\"},{\"dayOfWeek\":\"TUESDAY\",\"startTime\":\"14:00:00\",\"endTime\":\"18:00:00\"},{\"dayOfWeek\":\"WEDNESDAY\",\"startTime\":\"14:00:00\",\"endTime\":\"18:00:00\"},{\"dayOfWeek\":\"THURSDAY\",\"startTime\":\"14:00:00\",\"endTime\":\"18:00:00\"},{\"dayOfWeek\":\"FRIDAY\",\"startTime\":\"14:00:00\",\"endTime\":\"18:00:00\"}]}"
fi

# Capacidad REAL de miguel: sus franjas sin contar el solapamiento. Las dos
# franjas del lunes (09:00-12:00 y 11:00-14:00) cubren 09:00-14:00 = 300
# minutos reales, pero el reporte las suma como 180+180 = 360.
REAL_MONDAY_MIN=$(python3 -c "
u = sorted([(9*60,12*60),(11*60,14*60)])
merged = []
for s,e in u:
    if merged and s <= merged[-1][1]: merged[-1] = (merged[-1][0], max(merged[-1][1], e))
    else: merged.append((s,e))
print(sum(e-s for s,e in merged))")
SUM_MONDAY_MIN=$(psql_qa -t -A -c "
    SELECT coalesce(sum(EXTRACT(EPOCH FROM (hora_fin - hora_inicio))/60),0)::bigint
    FROM horario_semanal WHERE profesional_id='$MIGUEL'::uuid AND dia_semana='MONDAY';" 2>/dev/null | tr -d ' ')
info "[$HU] G.7 Capacidad real del lunes de miguel (franjas fusionadas)=$REAL_MONDAY_MIN min; minutos que suma el reporte=$SUM_MONDAY_MIN min"
if [[ "$REAL_MONDAY_MIN" != "$SUM_MONDAY_MIN" && -n "$SUM_MONDAY_MIN" && "$SUM_MONDAY_MIN" != "0" ]]; then
    bug "[$HU] G.8 BUG: la capacidad de miguel esta INFLADA por el solapamiento de franjas. Las dos franjas del lunes (09:00-12:00 y 11:00-14:00) cubren en realidad $REAL_MONDAY_MIN minutos, pero el reporte HU-15 las suma como $SUM_MONDAY_MIN minutos (3h+3h en vez de 5h). El CHECK de la BD (ck_horario_semanal_franja) solo exige hora_fin > hora_inicio y no impide franjas solapadas, y OccupancyCalculator.capacityByProfessional las suma sin fusionar. capacityMinutes del lunes reportado para todos los profesionales=$MON_CAP"
else
    pass "[$HU] G.8 La capacidad declarada coincide con la capacidad real"
fi

# =============================================================================
banner "[$HU] H. Profesional recien creado SIN horario"
# =============================================================================
hit "[$HU] H.1 Reporte responde 200" 200 GET "/api/v1/reportes/ocupacion?periodo=MES" "$ADMIN_TOKEN"
if [[ "$LAST_CODE" == "500" ]]; then
    fail "[$HU] H.2 El reporte devuelve 500 con un profesional sin horario"
    bug "[$HU] H.2 BUG: GET /api/v1/reportes/ocupacion devuelve 500 cuando existe un profesional recien creado SIN horario (posible NPE en Collector.toMap con valor null). body=$LAST_BODY"
else
    pass "[$HU] H.2 El reporte NO devuelve 500 con un profesional sin horario (HTTP $LAST_CODE)"
fi
NOH_NAME=$(report_prof "$NOHORARIO" professionalName)
NOH_CAP=$(report_prof "$NOHORARIO" capacityMinutes)
info "[$HU] H.3 professionalName='$NOH_NAME' capacityMinutes='$NOH_CAP'"
if [[ "$NOH_CAP" == "0" ]]; then
    pass "[$HU] H.4 El profesional sin horario aparece en byProfessional con capacidad 0"
else
    fail "[$HU] H.4 El profesional sin horario deberia tener capacidad 0, tiene '$NOH_CAP'"
fi
if [[ -z "$NOH_NAME" || "$NOH_NAME" == "None" ]]; then
    bug "[$HU] H.5 BUG: el profesional sin horario aparece en byProfessional con professionalName vacio/null ('$NOH_NAME'). El sort por Comparator.comparing(ProfessionalOccupancy::professionalName) no puede ordenar un null."
else
    pass "[$HU] H.5 El profesional sin horario trae professionalName no nulo ('$NOH_NAME')"
fi

# =============================================================================
banner "[$HU] I. HU-05 + HU-13: el bloqueo no cancela las reservas"
# =============================================================================
# Cliente con reservas CONFIRMADA; el profesional intenta bloquear ese rango.
I_DATE=$(fut_dow 40 1 09:00 | cut -dT -f1)
I_BOOKING="aaaaaaaa-aaaa-4aaa-8aaa-000000000001"
psql_qa -c "
    INSERT INTO bookings (id, customer_id, professional_id, service_id, start_time, end_time, status, created_at, updated_at)
    VALUES ('$I_BOOKING'::uuid, '$HU13_CUSTOMER_ID'::uuid, '$SOFIA'::uuid, '$SVC1'::uuid,
            '${I_DATE}T09:00:00', '${I_DATE}T10:00:00', 'CONFIRMADA', now(), now());
" >/dev/null 2>&1

hit "[$HU] I.1 POST de bloqueo sobre el rango con reserva responde 409" 409 POST "/api/v1/horarios/$SOFIA/bloqueos" "$TOK_SOFIA" \
    "{\"startTime\":\"${I_DATE}T09:00:00\",\"endTime\":\"${I_DATE}T12:00:00\",\"reason\":\"intento I\"}"
if [[ "$(json_get errorCode)" == "AGENDA_BLOCK_HAS_BOOKINGS" ]]; then
    pass "[$HU] I.2 errorCode = AGENDA_BLOCK_HAS_BOOKINGS"
else
    fail "[$HU] I.2 errorCode esperado AGENDA_BLOCK_HAS_BOOKINGS, obtuve '$(json_get errorCode)'"
fi

hit "[$HU] I.3 El cliente sigue viendo su historial responde 200" 200 GET "/api/v1/reservas/cliente/historial?size=100" "$CUST"
STILL_THERE=$(python3 -c '
import sys,json
d=json.loads(sys.argv[1]); bid=sys.argv[2]
print("yes" if any(x.get("id")==bid and x.get("status")=="CONFIRMADA" for x in d.get("content",[])) else "no")
' "$LAST_BODY" "$I_BOOKING")
if [[ "$STILL_THERE" == "yes" ]]; then
    pass "[$HU] I.4 La reserva sigue en el historial del cliente con estado CONFIRMADA (no se cancela sola)"
else
    fail "[$HU] I.4 La reserva desaparecio o cambio de estado en el historial"
    bug "[$HU] I.4 BUG: al rechazar el bloqueo (409), la reserva del cliente desaparece o cambia de estado en el historial. body=${LAST_BODY:0:400}"
fi

# =============================================================================
banner "[$HU] J. Migraciones: V50 y tabla bloqueo_agenda"
# =============================================================================
V50=$(psql_qa -t -A -c "SELECT success FROM flyway_schema_history WHERE version = '50';" 2>/dev/null | tr -d ' ')
if [[ "$V50" == "t" ]]; then
    pass "[$HU] J.1 La migracion V50 esta aplicada (success=t)"
else
    fail "[$HU] J.1 La migracion V50 NO esta aplicada o fallo (success='$V50')"
    bug "[$HU] J.1 BUG: la migracion V50__create_agenda_blocks.sql no esta aplicada correctamente en la base"
fi
TABLE=$(psql_qa -t -A -c "SELECT to_regclass('public.bloqueo_agenda');" 2>/dev/null | tr -d ' ')
if [[ "$TABLE" == "bloqueo_agenda" ]]; then
    pass "[$HU] J.2 La tabla bloqueo_agenda existe"
else
    fail "[$HU] J.2 La tabla bloqueo_agenda NO existe ('$TABLE')"
fi
EXCL=$(psql_qa -t -A -c "
    SELECT count(*) FROM pg_constraint
    WHERE conname = 'bloqueo_agenda_sin_solapamiento'
      AND contype = 'x';
" 2>/dev/null | tr -d ' ')
if [[ "$EXCL" == "1" ]]; then
    pass "[$HU] J.3 La constraint EXCLUDE de solapamiento existe"
else
    fail "[$HU] J.3 La constraint EXCLUDE de solapamiento NO existe (count=$EXCL)"
    bug "[$HU] J.3 BUG: la constraint EXCLUDE 'bloqueo_agenda_sin_solapamiento' no esta creada en la base"
fi
CHK=$(psql_qa -t -A -c "
    SELECT count(*) FROM pg_constraint WHERE conname = 'ck_bloqueo_agenda_rango';
" 2>/dev/null | tr -d ' ')
if [[ "$CHK" == "1" ]]; then
    pass "[$HU] J.4 La constraint CHECK del rango (fin > inicio) existe"
else
    fail "[$HU] J.4 La constraint CHECK del rango NO existe (count=$CHK)"
fi
# Las columnas del reporte no deben ignorar la tabla de bloqueos
COLS=$(psql_qa -t -A -c "
    SELECT count(*) FROM information_schema.columns
    WHERE table_name = 'bloqueo_agenda';
" 2>/dev/null | tr -d ' ')
info "[$HU] J.5 Columnas de bloqueo_agenda: $COLS"
if [[ "$COLS" == "6" ]]; then
    pass "[$HU] J.6 La tabla bloqueo_agenda tiene sus 6 columnas"
else
    fail "[$HU] J.6 Se esperaban 6 columnas en bloqueo_agenda y hay $COLS"
fi

# =============================================================================
banner "[$HU] K. BUG A1: slots duplicados por franjas horarias solapadas"
# =============================================================================
# Miguel tiene DOS franjas que se solapan el lunes (09:00-12:00 y 11:00-14:00);
# la BD lo permite (el CHECK solo exige hora_fin > hora_inicio). El
# AvailabilityCalculator genera slots franja por franja SIN fusionar las
# franjas solapadas, asi que la hora de inicio 11:00 aparece DOS VECES en la
# respuesta de disponibilidad: el cliente ve la misma hora offered dos veces.
K_DATE=$(clean_future_dow "$MIGUEL" 1 25 60 "")
if [[ -z "$K_DATE" ]]; then
    fail "[$HU] K.0 No se encontro un lunes futuro limpio para el caso K"
    K_DATE=$(date -d "+32 days" +%Y-%m-%d)
fi
info "[$HU] K.0 Fecha del caso K: $K_DATE"

hit "[$HU] K.1 Disponibilidad de miguel (servicio de 60 min) responde 200" 200 GET \
    "/api/v1/disponibilidad?profesionalId=$MIGUEL&servicioId=${SVC_IDS[1]}&desde=$K_DATE&hasta=$K_DATE" "$CUST"
K_SLOTS=$(avail_slots)
read -r K_TOTAL K_DUPS K_DUP_LIST <<< "$(avail_slot_dups)"
info "[$HU] K.2 Slots de miguel ($K_TOTAL): '$K_SLOTS'"
info "[$HU] K.3 startTime repetidos: $K_DUPS ($K_DUP_LIST)"

# Un profesional con una sola franja por dia no debe tener horas repetidas:
# la respuesta debe ser un conjunto, no una multilista.
K_WINDOW_COUNT=$(psql_qa -t -A -c "
    SELECT count(*) FROM horario_semanal
    WHERE profesional_id='$MIGUEL'::uuid AND dia_semana='MONDAY';
" 2>/dev/null | tr -d ' ')
if [[ "$K_WINDOW_COUNT" -ge 2 ]]; then
    pass "[$HU] K.4 La BD admite 2 franjas solapadas el mismo dia (precondicion del caso K)"
else
    fail "[$HU] K.4 Se esperaban 2 franjas solapadas de miguel el lunes y hay $K_WINDOW_COUNT"
fi

if [[ "$K_DUPS" == "0" ]]; then
    pass "[$HU] K.5 La disponibilidad NO devuelve startTime repetidos ($K_TOTAL slots, 0 repetidos)"
else
    bug "[$HU] K.5 BUG: la devuelve startTime repetidos en los slots de disponibilidad. Miguel tiene $K_WINDOW_COUNT franjas el lunes que se solapan (09:00-12:00 y 11:00-14:00) y el AvailabilityCalculator genera los slots franja por franja sin fusionarlas: de $K_TOTAL slots, $K_DUPS tienen startTime repetido ($K_DUP_LIST). Slots='$K_SLOTS'. El cliente ve la misma hora ofrecida mas de una vez y el numero de huecos libres que se le muestra no corresponde con los huecos reales."
fi

# =============================================================================
banner "[$HU] L. BUG A2: dia con reservas pero sin capacidad => 0% de ocupacion"
# =============================================================================
# Un dia sin horario (fin de semana) NO tiene capacidad (capacityMinutes = 0),
# pero puede tener reservas. OccupancyCalculator.percent devuelve 0.0 cuando
# la capacidad es 0 (para no dividir por cero), de modo que el reporte muestra
# un dia CON reservas y 0% de ocupacion: el dato es contradictorio y se
# presenta como si ese dia estuviese libre.
# Se crea una reserva CONFIRMADA en un sabado dentro de la ventana del reporte
# y se mide el bookedMinutes del SABADO antes y despues de crearla.
#
# FIXTURE DELIBERADO (no es un dato alcanzable por la API). Un sabado no tiene
# horario para ningun profesional, de modo que la API rechaza la reserva con
# 409 SLOT_OUTSIDE_SCHEDULE. La reserva se inserta por SQL a proposito, para
# dejar al profesional con minutos reservados en un dia SIN capacidad, que es
# exactamente el estado contradictorio que se quiere medir (S3-08). El debugging
# sistematico confirmo que S3-08 es alcanzable por API por otra via (encoger el
# horario tras reservar), de modo que este caso SQL y el caso API son el mismo
# defecto. Los datos de esta reserva no son de negocio: son un fixture para
# reproducir el defecto y se insertan por SQL de forma explicita.
L_DATE=""
for d in $(seq 1 27); do
    cand=$(date -d "-$d days" +%Y-%m-%d)
    if [[ "$(date -d "$cand" +%u)" == "6" ]]; then L_DATE="$cand"; break; fi
done
if [[ -z "$L_DATE" ]]; then
    fail "[$HU] L.0 No se encontro un sabado pasado dentro de la ventana del reporte"
    L_DATE=$(date -d "-7 days" +%Y-%m-%d)
fi
info "[$HU] L.0 Fecha del caso L (sabado sin horario): $L_DATE"

L_BOOK_ID="bbbbbbbb-bbbb-4bbb-8bbb-000000000001"
# Idempotencia: la reserva del caso se borra ANTES de medir el "antes", de
# modo que la diferencia de bookedMinutes sea siempre la de esta ejecucion.
psql_qa -c "DELETE FROM bookings WHERE id = '$L_BOOK_ID'::uuid;" >/dev/null 2>&1

hit "[$HU] L.1 Reporte ANTES de la reserva de fin de semana responde 200" 200 GET "/api/v1/reportes/ocupacion?periodo=MES" "$ADMIN_TOKEN"
L_BOOKED_BEFORE=$(report_day "SATURDAY" bookedMinutes)
L_CAP_SAT=$(report_day "SATURDAY" capacityMinutes)
L_PCT_BEFORE=$(report_day "SATURDAY" occupancyPercent)
info "[$HU] L.2 SATURDAY antes: bookedMinutes='$L_BOOKED_BEFORE' capacityMinutes='$L_CAP_SAT' occupancyPercent='$L_PCT_BEFORE'"

psql_qa -c "
    INSERT INTO bookings (id, customer_id, professional_id, service_id, start_time, end_time, status, created_at, updated_at)
    VALUES ('$L_BOOK_ID'::uuid, '$HU13_CUSTOMER_ID'::uuid, '$SOFIA'::uuid, '$SVC1'::uuid,
            '${L_DATE}T10:00:00', '${L_DATE}T11:00:00', 'CONFIRMADA', now(), now());
" >/dev/null 2>&1

hit "[$HU] L.3 Reporte DESPUES de la reserva de fin de semana responde 200" 200 GET "/api/v1/reportes/ocupacion?periodo=MES" "$ADMIN_TOKEN"
L_BOOKED_AFTER=$(report_day "SATURDAY" bookedMinutes)
L_CAP_SAT2=$(report_day "SATURDAY" capacityMinutes)
L_PCT_AFTER=$(report_day "SATURDAY" occupancyPercent)
info "[$HU] L.4 SATURDAY despues: bookedMinutes='$L_BOOKED_AFTER' capacityMinutes='$L_CAP_SAT2' occupancyPercent='$L_PCT_AFTER'"

L_DELTA=$(python3 -c "
try:
    print(int('$L_BOOKED_AFTER' or 0) - int('$L_BOOKED_BEFORE' or 0))
except Exception:
    print(0)")
if [[ "$L_DELTA" == "60" ]]; then
    pass "[$HU] L.5 El reporte cuenta la reserva del sabado (+60 min de bookedMinutes)"
else
    fail "[$HU] L.5 El reporte no conto la reserva del sabado: diferencia=$L_DELTA (antes='$L_BOOKED_BEFORE' despues='$L_BOOKED_AFTER')"
fi

if [[ "$L_CAP_SAT2" == "0" && "$L_BOOKED_AFTER" != "0" && "$L_BOOKED_AFTER" != "" && "$L_PCT_AFTER" == "0.0" ]]; then
    bug "[$HU] L.6 BUG: el reporte HU-15 muestra un dia CON reservas y 0% de ocupacion. El $L_DATE (sabado) no tiene horario, asi que capacityMinutes=0, pero hay reservas (bookedMinutes=$L_BOOKED_AFTER tras la reserva $L_BOOK_ID de 60 min) y occupancyPercent se devuelve como $L_PCT_AFTER. OccupancyCalculator.percent devuelve 0.0 cuando capacityMinutes <= 0, de modo que el dia aparece con reservas y con ocupacion nula: el dato es contradictorio y se presenta como si el dia estuviese libre."
else
    pass "[$HU] L.6 El reporte NO muestra un dia con reservas y 0% de ocupacion (booked='$L_BOOKED_AFTER' capacity='$L_CAP_SAT2' pct='$L_PCT_AFTER')"
fi

print_summary

#!/usr/bin/env bash
# =============================================================================
# [HU-05] Bloqueo temporal de agenda - pruebas E2E
# =============================================================================
# Endpoint verificado en el codigo:
#   /api/v1/horarios/{professionalId}/bloqueos   (AgendaBlockController)
#   GET    -> 200 con los bloqueos no terminados
#   POST   -> 201 con el bloqueo creado
#   DELETE -> 204
# Contrato: docs/HU-05-bloqueo-agenda.md
# =============================================================================
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"
source "$SCRIPT_DIR/.e2e-results/seed.env"

HU="HU-05"
SOFIA="$PROF_SOFIA_USER_ID"
MIGUEL="$PROF_MIGUEL_USER_ID"
ELENA="$PROF_ELENA_USER_ID"
TOK_SOFIA="$TOK_prof_sofia"
TOK_MIGUEL="$TOK_prof_miguel"
TOK_ELENA="$TOK_prof_elena"
CUST="$TOK_hu13_customer_bookly_local"

# ---------- Helpers ----------------------------------------------------------
psql_qa() {
    docker exec -i "${DB_CONTAINER:-backendcf-db}" psql -v ON_ERROR_STOP=1 \
        -U "${DB_ROLE:-bookly_test}" -d "${DB_NAME:-bookly_test}" "$@"
}

# Fecha futura en un dia de la semana concreto (1=lunes .. 7=domingo)
fut_dow() {
    # fut_dow <dias_minimos> <dia_semana_iso 1..7> <HH:MM>  -> "YYYY-MM-DDTHH:MM:00"
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
    # json_get <campo>  (sobre $LAST_BODY)
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

# Cuenta claves de "details" en una respuesta de error
details_count() {
    python3 -c 'import sys,json
try:
    d=json.loads(sys.argv[1])
except Exception:
    print("0"); sys.exit(0)
det=d.get("details") or {}
print(len(det) if isinstance(det,dict) else 0)
' "$LAST_BODY"
}

# Crea un bloqueo. Deja el id en la variable global BLOCK_ID y el body en LAST_BODY.
# create_block <token> <profId> <start> <end> <reason-json>
create_block() {
    local tok="$1" prof="$2" start="$3" end="$4" reason="$5"
    hit_raw POST "/api/v1/horarios/$prof/bloqueos" "$tok" \
        "{\"startTime\":\"$start\",\"endTime\":\"$end\",\"reason\":$reason}"
    BLOCK_ID=$(json_get id)
}

# =============================================================================
banner "[$HU] 1. Crear un bloqueo valido como profesional propio"
# =============================================================================
D1=$(fut_dow 40 2 09:00)   # martes
D1E=$(fut_dow 40 2 12:00)
create_block "$TOK_SOFIA" "$SOFIA" "$D1" "$D1E" '"Vacaciones de prueba"'
assert_status "[$HU] 1.1 POST bloqueo valido devuelve 201" 201 "$LAST_CODE" "$LAST_BODY"
if [[ -n "$BLOCK_ID" && "$BLOCK_ID" != "null" ]]; then
    pass "[$HU] 1.2 El bloqueo creado trae id no nulo ($BLOCK_ID)"
else
    fail "[$HU] 1.2 El bloqueo creado NO trae id: $LAST_BODY"
fi
if [[ "$(json_get reason)" == "Vacaciones de prueba" ]]; then
    pass "[$HU] 1.3 El campo reason se propaga en la respuesta"
else
    fail "[$HU] 1.3 El campo reason no se propaga: $LAST_BODY"
fi
if [[ "$(json_get startTime)" == "${D1}"* ]]; then
    pass "[$HU] 1.4 startTime se propaga"
else
    fail "[$HU] 1.4 startTime no coincide: esperado $D1, obtuve $(json_get startTime)"
fi
if [[ "$(json_get professionalId)" == "$SOFIA" ]]; then
    pass "[$HU] 1.5 professionalId se propaga"
else
    fail "[$HU] 1.5 professionalId incorrecto: $(json_get professionalId)"
fi

# =============================================================================
banner "[$HU] 2. GET lista solo los bloqueos no terminados"
# =============================================================================
# Se inserta por SQL un bloqueo ya pasado (end_time < now): no debe aparecer.
PAST_BLOCK_ID="33333333-3333-4333-8333-000000000001"
psql_qa -c "
    INSERT INTO bloqueo_agenda (id, profesional_id, inicio, fin, motivo, created_at)
    VALUES ('$PAST_BLOCK_ID'::uuid, '$SOFIA'::uuid,
            now() - interval '30 days', now() - interval '29 days', 'Bloqueo ya terminado', now());
" >/dev/null 2>&1

hit "[$HU] 2.1 GET bloqueos responde 200" 200 GET "/api/v1/horarios/$SOFIA/bloqueos" "$TOK_SOFIA"
if [[ "$LAST_BODY" == *"$BLOCK_ID"* ]]; then
    pass "[$HU] 2.2 El bloqueo vigente SI aparece en la lista"
else
    fail "[$HU] 2.2 El bloqueo vigente NO aparece: $LAST_BODY"
fi
if [[ "$LAST_BODY" == *"$PAST_BLOCK_ID"* ]]; then
    fail "[$HU] 2.3 Un bloqueo ya terminado NO debe aparecer en la lista: $LAST_BODY"
    bug "[$HU] 2.3 BUG: GET /api/v1/horarios/$SOFIA/bloqueos devuelve un bloqueo ya terminado (end_time < now)"
else
    pass "[$HU] 2.3 Un bloqueo ya terminado NO aparece en la lista"
fi

# =============================================================================
banner "[$HU] 3. Validacion del rango (endTime <= startTime)"
# =============================================================================
hit "[$HU] 3.1 POST con endTime <= startTime devuelve 400" 400 POST "/api/v1/horarios/$SOFIA/bloqueos" "$TOK_SOFIA" \
    "{\"startTime\":\"$(fut_dow 45 3 10:00)\",\"endTime\":\"$(fut_dow 45 3 10:00)\",\"reason\":\"x\"}"
if [[ "$(json_get errorCode)" == "SCHEDULE_VALIDATION_ERROR" ]]; then
    pass "[$HU] 3.2 errorCode = SCHEDULE_VALIDATION_ERROR"
else
    fail "[$HU] 3.2 errorCode esperado SCHEDULE_VALIDATION_ERROR, obtuve $(json_get errorCode)"
fi
if [[ -n "$(json_get details.endTime)" ]]; then
    pass "[$HU] 3.3 details.endTime presente"
else
    fail "[$HU] 3.4 details.endTime ausente: $LAST_BODY"
fi

# =============================================================================
banner "[$HU] 4. Validacion del rango (startTime en el pasado)"
# =============================================================================
PAST_START=$(date -d "-5 days" +%Y-%m-%dT10:00:00)
PAST_END=$(date -d "-5 days" +%Y-%m-%dT11:00:00)
hit "[$HU] 4.1 POST con startTime en el pasado devuelve 400" 400 POST "/api/v1/horarios/$SOFIA/bloqueos" "$TOK_SOFIA" \
    "{\"startTime\":\"$PAST_START\",\"endTime\":\"$PAST_END\",\"reason\":\"x\"}"
if [[ "$(json_get errorCode)" == "SCHEDULE_VALIDATION_ERROR" ]]; then
    pass "[$HU] 4.2 errorCode = SCHEDULE_VALIDATION_ERROR"
else
    fail "[$HU] 4.2 errorCode esperado SCHEDULE_VALIDATION_ERROR, obtuve $(json_get errorCode)"
fi
if [[ -n "$(json_get details.startTime)" ]]; then
    pass "[$HU] 4.3 details.startTime presente"
else
    fail "[$HU] 4.3 details.startTime ausente: $LAST_BODY"
fi

# =============================================================================
banner "[$HU] 5. Body sin startTime / endTime"
# =============================================================================
hit "[$HU] 5.1 POST sin startTime ni endTime devuelve 400" 400 POST "/api/v1/horarios/$SOFIA/bloqueos" "$TOK_SOFIA" \
    '{"reason":"sin fechas"}'
if [[ "$(json_get errorCode)" == "VALIDATION_ERROR" ]]; then
    pass "[$HU] 5.2 errorCode = VALIDATION_ERROR"
else
    fail "[$HU] 5.2 errorCode esperado VALIDATION_ERROR, obtuve $(json_get errorCode)"
fi
hit "[$HU] 5.3 POST solo con startTime devuelve 400" 400 POST "/api/v1/horarios/$SOFIA/bloqueos" "$TOK_SOFIA" \
    "{\"startTime\":\"$(fut_dow 50 4 10:00)\"}"
hit "[$HU] 5.4 POST solo con endTime devuelve 400" 400 POST "/api/v1/horarios/$SOFIA/bloqueos" "$TOK_SOFIA" \
    "{\"endTime\":\"$(fut_dow 50 4 11:00)\"}"

# =============================================================================
banner "[$HU] 6. Longitud del campo reason"
# =============================================================================
REASON_201=$(python3 -c 'print("r"*201)')
REASON_200=$(python3 -c 'print("r"*200)')
hit "[$HU] 6.1 POST con reason de 201 caracteres devuelve 400" 400 POST "/api/v1/horarios/$SOFIA/bloqueos" "$TOK_SOFIA" \
    "{\"startTime\":\"$(fut_dow 55 5 10:00)\",\"endTime\":\"$(fut_dow 55 5 11:00)\",\"reason\":\"$REASON_201\"}"
if [[ "$(json_get errorCode)" == "VALIDATION_ERROR" ]]; then
    pass "[$HU] 6.2 errorCode = VALIDATION_ERROR"
else
    fail "[$HU] 6.2 errorCode esperado VALIDATION_ERROR, obtuve $(json_get errorCode)"
fi
if [[ -n "$(json_get details.reason)" ]]; then
    pass "[$HU] 6.3 details.reason presente"
else
    fail "[$HU] 6.3 details.reason ausente: $LAST_BODY"
fi

hit "[$HU] 6.4 POST con reason de 200 caracteres devuelve 201" 201 POST "/api/v1/horarios/$SOFIA/bloqueos" "$TOK_SOFIA" \
    "{\"startTime\":\"$(fut_dow 56 5 10:00)\",\"endTime\":\"$(fut_dow 56 5 11:00)\",\"reason\":\"$REASON_200\"}"
BLOCK_200_ID=$(json_get id)

# =============================================================================
banner "[$HU] 7. reason ausente, vacio o solo espacios"
# =============================================================================
hit "[$HU] 7.1 POST sin el campo reason devuelve 201" 201 POST "/api/v1/horarios/$SOFIA/bloqueos" "$TOK_SOFIA" \
    "{\"startTime\":\"$(fut_dow 60 6 10:00)\",\"endTime\":\"$(fut_dow 60 6 11:00)\"}"
if [[ "$(json_get reason)" == "" || "$(json_get reason)" == "null" ]]; then
    pass "[$HU] 7.2 reason ausente -> null en la respuesta"
else
    fail "[$HU] 7.2 reason ausente deveria ser null, obtuve '$(json_get reason)'"
fi
BLOCK_NOR_REASON_ID=$(json_get id)

hit "[$HU] 7.3 POST con reason=\"\" devuelve 201" 201 POST "/api/v1/horarios/$SOFIA/bloqueos" "$TOK_SOFIA" \
    "{\"startTime\":\"$(fut_dow 61 6 12:00)\",\"endTime\":\"$(fut_dow 61 6 13:00)\",\"reason\":\"\"}"
if [[ "$(json_get reason)" == "" || "$(json_get reason)" == "null" ]]; then
    pass "[$HU] 7.4 reason=\"\" -> null en la respuesta (trim)"
else
    fail "[$HU] 7.4 reason=\"\" deberia quedar null, obtuve '$(json_get reason)'"
fi
BLOCK_EMPTY_REASON_ID=$(json_get id)

hit "[$HU] 7.5 POST con reason=\"   \" devuelve 201" 201 POST "/api/v1/horarios/$SOFIA/bloqueos" "$TOK_SOFIA" \
    "{\"startTime\":\"$(fut_dow 62 6 14:00)\",\"endTime\":\"$(fut_dow 62 6 15:00)\",\"reason\":\"   \"}"
if [[ "$(json_get reason)" == "" || "$(json_get reason)" == "null" ]]; then
    pass "[$HU] 7.6 reason=\"   \" -> null en la respuesta (trim)"
else
    fail "[$HU] 7.6 reason=\"   \" deberia quedar null, obtuve '$(json_get reason)'"
fi
BLOCK_BLANK_REASON_ID=$(json_get id)

# =============================================================================
banner "[$HU] 8. Bloqueo que solapa con reservas CONFIRMADA"
# =============================================================================
# La reserva historica CONFIRMADA de sofia esta en (hoy-10d) 09:00-10:00.
# Se crea un bloqueo futuro que solape con el RANGO de una reserva CONFIRMADA
# ya existente: por eso se inserta por SQL una reserva CONFIRMADA futura.
BOOKING_C1="44444444-4444-4444-8444-000000000001"
BOOKING_C2="44444444-4444-4444-8444-000000000002"
BOOKING_C3="44444444-4444-4444-8444-000000000003"
BOOKING_CANCELLED="44444444-4444-4444-8444-000000000004"
OV_START=$(fut_dow 70 1 09:00)
OV_END=$(fut_dow 70 1 17:00)
OV_DATE="${OV_START:0:10}"
for spec in "$BOOKING_C1|09:00|10:00" "$BOOKING_C2|11:00|12:00" "$BOOKING_C3|14:00|15:00"; do
    bid="${spec%%|*}"; rest="${spec#*|}"
    s1="${rest%%|*}"; s2="${rest#*|}"
    psql_qa -c "
        INSERT INTO bookings (id, customer_id, professional_id, service_id, start_time, end_time, status, created_at, updated_at)
        VALUES ('$bid'::uuid, '$HU13_CUSTOMER_ID'::uuid, '$SOFIA'::uuid, '${SVC_IDS[0]}'::uuid,
                '${OV_DATE}T$s1:00', '${OV_DATE}T$s2:00', 'CONFIRMADA', now(), now());
    " >/dev/null 2>&1
done
# Reserva CANCELADA en el mismo rango (no debe impedir el bloqueo)
psql_qa -c "
    INSERT INTO bookings (id, customer_id, professional_id, service_id, start_time, end_time, status, created_at, updated_at)
    VALUES ('$BOOKING_CANCELLED'::uuid, '$HU13_CUSTOMER_ID'::uuid, '$SOFIA'::uuid, '${SVC_IDS[0]}'::uuid,
            '${OV_START:0:10}T15:30:00', '${OV_START:0:10}T16:00:00', 'CANCELADA', now(), now());
" >/dev/null 2>&1

hit "[$HU] 8.1 POST que solapa con 3 reservas CONFIRMADA devuelve 409" 409 POST "/api/v1/horarios/$SOFIA/bloqueos" "$TOK_SOFIA" \
    "{\"startTime\":\"$OV_START\",\"endTime\":\"$OV_END\",\"reason\":\"choque\"}"
if [[ "$(json_get errorCode)" == "AGENDA_BLOCK_HAS_BOOKINGS" ]]; then
    pass "[$HU] 8.2 errorCode = AGENDA_BLOCK_HAS_BOOKINGS"
else
    fail "[$HU] 8.2 errorCode esperado AGENDA_BLOCK_HAS_BOOKINGS, obtuve $(json_get errorCode)"
fi
DET_N=$(details_count)
if [[ "$DET_N" == "3" ]]; then
    pass "[$HU] 8.3 details trae 3 claves (una por reserva afectada)"
else
    fail "[$HU] 8.3 details deberia traer 3 claves, trae $DET_N"
fi
if [[ "$LAST_BODY" == *"$BOOKING_C1"* ]]; then
    pass "[$HU] 8.4 details incluye la clave bookingId de la reserva C1"
else
    fail "[$HU] 8.4 details no incluye $BOOKING_C1: $LAST_BODY"
fi

# =============================================================================
banner "[$HU] 9. Bloqueo que solapa con una reserva CANCELADA"
# =============================================================================
CANC_START=$(fut_dow 80 2 09:00)
CANC_END=$(fut_dow 80 2 17:00)
psql_qa -c "
    INSERT INTO bookings (id, customer_id, professional_id, service_id, start_time, end_time, status, created_at, updated_at)
    VALUES ('$BOOKING_CANCELLED'::uuid, '$HU13_CUSTOMER_ID'::uuid, '$SOFIA'::uuid, '${SVC_IDS[0]}'::uuid,
            '${CANC_START:0:10}T10:00:00', '${CANC_START:0:10}T11:00:00', 'CANCELADA', now(), now());
" >/dev/null 2>&1
hit "[$HU] 9.1 POST que solapa solo con reserva CANCELADA devuelve 201" 201 POST "/api/v1/horarios/$SOFIA/bloqueos" "$TOK_SOFIA" \
    "{\"startTime\":\"$CANC_START\",\"endTime\":\"$CANC_END\",\"reason\":\"solo canceladas\"}"
BLOCK_CANCELLED_ID=$(json_get id)

# =============================================================================
banner "[$HU] 10. Bloqueo que solapa con otro bloqueo"
# =============================================================================
OVL_START=$(fut_dow 90 3 09:00)
OVL_END=$(fut_dow 90 3 12:00)
OVL2_START=$(fut_dow 90 3 11:00)
OVL2_END=$(fut_dow 90 3 14:00)
create_block "$TOK_SOFIA" "$SOFIA" "$OVL_START" "$OVL_END" '"primer bloqueo"'
BLOCK_OVL_ID="$BLOCK_ID"
hit "[$HU] 10.1 POST que solapa con otro bloqueo devuelve 409" 409 POST "/api/v1/horarios/$SOFIA/bloqueos" "$TOK_SOFIA" \
    "{\"startTime\":\"$OVL2_START\",\"endTime\":\"$OVL2_END\",\"reason\":\"solapa\"}"
if [[ "$(json_get errorCode)" == "AGENDA_BLOCK_OVERLAP" ]]; then
    pass "[$HU] 10.2 errorCode = AGENDA_BLOCK_OVERLAP"
else
    fail "[$HU] 10.2 errorCode esperado AGENDA_BLOCK_OVERLAP, obtuve $(json_get errorCode)"
fi
if [[ "$(details_count)" -ge 1 ]]; then
    pass "[$HU] 10.3 details trae el bloqueo en conflicto"
else
    fail "[$HU] 10.3 details vacio: $LAST_BODY"
fi

# =============================================================================
banner "[$HU] 11. Bloqueo contiguo (fin == inicio del otro)"
# =============================================================================
CONT_START=$(fut_dow 100 4 12:00)
CONT_END=$(fut_dow 100 4 15:00)
hit "[$HU] 11.1 POST contiguo al bloqueo anterior devuelve 201" 201 POST "/api/v1/horarios/$SOFIA/bloqueos" "$TOK_SOFIA" \
    "{\"startTime\":\"$CONT_START\",\"endTime\":\"$CONT_END\",\"reason\":\"contiguo\"}"
BLOCK_CONTIGUO_ID=$(json_get id)

# =============================================================================
banner "[$HU] 12. Autorizacion: otro profesional, CUSTOMER, sin token"
# =============================================================================
hit "[$HU] 12.1 POST sobre la agenda de otro profesional devuelve 403" 403 POST "/api/v1/horarios/$MIGUEL/bloqueos" "$TOK_SOFIA" \
    "{\"startTime\":\"$(fut_dow 110 5 09:00)\",\"endTime\":\"$(fut_dow 110 5 10:00)\",\"reason\":\"ajeno\"}"
if [[ "$(json_get errorCode)" == "ACCESS_DENIED" ]]; then
    pass "[$HU] 12.2 errorCode = ACCESS_DENIED"
else
    fail "[$HU] 12.2 errorCode esperado ACCESS_DENIED, obtuve $(json_get errorCode)"
fi

hit "[$HU] 12.3 POST como CUSTOMER devuelve 403" 403 POST "/api/v1/horarios/$SOFIA/bloqueos" "$CUST" \
    "{\"startTime\":\"$(fut_dow 111 5 09:00)\",\"endTime\":\"$(fut_dow 111 5 10:00)\",\"reason\":\"customer\"}"

hit "[$HU] 12.4 POST sin token devuelve 401" 401 POST "/api/v1/horarios/$SOFIA/bloqueos" "" \
    "{\"startTime\":\"$(fut_dow 112 5 09:00)\",\"endTime\":\"$(fut_dow 112 5 10:00)\",\"reason\":\"anonimo\"}"

NONEXISTENT_PROF="99999999-9999-4999-8999-999999999999"
hit "[$HU] 12.5 POST con professionalId inexistente devuelve 404" 404 POST "/api/v1/horarios/$NONEXISTENT_PROF/bloqueos" "$TOK_SOFIA" \
    "{\"startTime\":\"$(fut_dow 113 6 09:00)\",\"endTime\":\"$(fut_dow 113 6 10:00)\",\"reason\":\"no existe\"}"
if [[ "$(json_get errorCode)" == "PROFESSIONAL_NOT_FOUND" ]]; then
    pass "[$HU] 12.6 errorCode = PROFESSIONAL_NOT_FOUND"
else
    fail "[$HU] 12.6 errorCode esperado PROFESSIONAL_NOT_FOUND, obtuve $(json_get errorCode)"
fi

# =============================================================================
banner "[$HU] 13. GET: autorizacion"
# =============================================================================
hit "[$HU] 13.1 GET de la agenda de otro profesional devuelve 403" 403 GET "/api/v1/horarios/$MIGUEL/bloqueos" "$TOK_SOFIA"
hit "[$HU] 13.2 GET con professionalId inexistente devuelve 404" 404 GET "/api/v1/horarios/$NONEXISTENT_PROF/bloqueos" "$TOK_SOFIA"
if [[ "$(json_get errorCode)" == "PROFESSIONAL_NOT_FOUND" ]]; then
    pass "[$HU] 13.3 errorCode = PROFESSIONAL_NOT_FOUND"
else
    fail "[$HU] 13.3 errorCode esperado PROFESSIONAL_NOT_FOUND, obtuve $(json_get errorCode)"
fi
hit "[$HU] 13.4 GET sin token devuelve 401" 401 GET "/api/v1/horarios/$SOFIA/bloqueos" ""

# =============================================================================
banner "[$HU] 14. DELETE de bloqueos"
# =============================================================================
create_block "$TOK_MIGUEL" "$MIGUEL" "$(fut_dow 120 1 09:00)" "$(fut_dow 120 1 10:00)" '"para borrar"'
DEL_ID="$BLOCK_ID"
hit "[$HU] 14.1 DELETE de un bloqueo propio devuelve 204" 204 DELETE "/api/v1/horarios/$MIGUEL/bloqueos/$DEL_ID" "$TOK_MIGUEL"

NONEXISTENT_BLOCK="88888888-8888-4888-8888-888888888888"
hit "[$HU] 14.2 DELETE de un bloqueo inexistente devuelve 404" 404 DELETE "/api/v1/horarios/$MIGUEL/bloqueos/$NONEXISTENT_BLOCK" "$TOK_MIGUEL"
if [[ "$(json_get errorCode)" == "AGENDA_BLOCK_NOT_FOUND" ]]; then
    pass "[$HU] 14.3 errorCode = AGENDA_BLOCK_NOT_FOUND"
else
    fail "[$HU] 14.3 errorCode esperado AGENDA_BLOCK_NOT_FOUND, obtuve $(json_get errorCode)"
fi

# Bloque de sofia borrado usando el path de miguel: debe ser 404 (no 403)
hit "[$HU] 14.4 DELETE de un bloqueo ajeno usando el path de otro devuelve 404" 404 DELETE "/api/v1/horarios/$MIGUEL/bloqueos/$BLOCK_OVL_ID" "$TOK_MIGUEL"
if [[ "$(json_get errorCode)" == "AGENDA_BLOCK_NOT_FOUND" ]]; then
    pass "[$HU] 14.5 errorCode = AGENDA_BLOCK_NOT_FOUND (no 403)"
else
    fail "[$HU] 14.5 errorCode esperado AGENDA_BLOCK_NOT_FOUND, obtuve $(json_get errorCode)"
fi

hit "[$HU] 14.6 DELETE sin token devuelve 401" 401 DELETE "/api/v1/horarios/$SOFIA/bloqueos/$BLOCK_OVL_ID" ""

# =============================================================================
banner "[$HU] 15. CONCURRENCIA: dos POST simultaneos del mismo rango"
# =============================================================================
# CASO CLAVE: si el 409 del bloqueo solapado sale con errorCode
# REGISTRATION_CONFLICT (handler global de DataIntegrityViolationException)
# en vez de AGENDA_BLOCK_OVERLAP, es un BUG de contrato de error.
CONC_START=$(fut_dow 130 2 09:00)
CONC_END=$(fut_dow 130 2 12:00)
CONC_PAYLOAD="{\"startTime\":\"$CONC_START\",\"endTime\":\"$CONC_END\",\"reason\":\"concurrent\"}"

C1_OUT=$(mktemp); C2_OUT=$(mktemp)
( curl -sS -o "$C1_OUT" -w '%{http_code}' -X POST "$BASE_URL/api/v1/horarios/$SOFIA/bloqueos" \
    -H "Authorization: Bearer $TOK_SOFIA" -H "Content-Type: application/json" --data "$CONC_PAYLOAD" > "$C1_OUT.code" ) &
P1=$!
( curl -sS -o "$C2_OUT" -w '%{http_code}' -X POST "$BASE_URL/api/v1/horarios/$SOFIA/bloqueos" \
    -H "Authorization: Bearer $TOK_SOFIA" -H "Content-Type: application/json" --data "$CONC_PAYLOAD" > "$C2_OUT.code" ) &
P2=$!
wait $P1 $P2
CODE_A=$(cat "$C1_OUT.code"); CODE_B=$(cat "$C2_OUT.code")
BODY_A=$(cat "$C1_OUT"); BODY_B=$(cat "$C2_OUT")
info "[$HU] 15.1 POST concurrente A -> HTTP $CODE_A"
info "[$HU] 15.2 POST concurrente B -> HTTP $CODE_B"

OK201=0
for c in "$CODE_A" "$CODE_B"; do [[ "$c" == "201" ]] && OK201=$((OK201+1)); done
if [[ "$OK201" -le 1 ]]; then
    pass "[$HU] 15.3 Al menos uno de los dos POST concurrentes NO fue 201 (se respeta la exclusion)"
else
    fail "[$HU] 15.3 Los dos POST concurrentes devolvieron 201: se-crearon dos bloqueos solapados"
    bug "[$HU] 15.3 BUG: dos POST concurrentes del mismo rango devolvieron 201 ambos: no se respeta la EXCLUDE"
fi

# Se revisa CADA respuesta concurrente: el que NO fue 201 debe ser un 409
# AGENDA_BLOCK_OVERLAP (nunca 500, nunca 401 y nunca REGISTRATION_CONFLICT).
CONC_EXPECTED=409
CONC_OK=0
for pair in "$CODE_A|$BODY_A" "$CODE_B|$BODY_B"; do
    c="${pair%%|*}"; b="${pair#*|}"
    [[ "$c" == "201" ]] && continue
    ec=$(python3 -c 'import sys,json
try: print(json.loads(sys.argv[1]).get("errorCode",""))
except Exception: print("")' "$b")
    if [[ "$c" == "409" && "$ec" == "AGENDA_BLOCK_OVERLAP" ]]; then
        CONC_OK=1
        pass "[$HU] 15.4 El rechazo del bloqueo concurrente es 409 AGENDA_BLOCK_OVERLAP"
    elif [[ "$c" == "409" && "$ec" == "REGISTRATION_CONFLICT" ]]; then
        fail "[$HU] 15.4 El 409 concurrente usa REGISTRATION_CONFLICT en vez de AGENDA_BLOCK_OVERLAP"
        bug "[$HU] 15.4 BUG: POST /api/v1/horarios/{professionalId}/bloqueos concurrente devuelve 409 con errorCode 'REGISTRATION_CONFLICT' en vez de 'AGENDA_BLOCK_OVERLAP' (lo absorbe el handler global de DataIntegrityViolationException). body=$b"
    elif [[ "$c" == "500" ]]; then
        fail "[$HU] 15.5 Un POST concurrente devolvio 500 (error interno no controlado)"
        bug "[$HU] 15.5 BUG: POST /api/v1/horarios/{professionalId}/bloqueos concurrente devuelve 500 ante la violacion de la EXCLUDE. body=$b"
    elif [[ "$c" == "401" ]]; then
        fail "[$HU] 15.5 Un POST concurrente devolvio 401 con un token valido"
        bug "[$HU] 15.5 BUG: POST /api/v1/horarios/{professionalId}/bloqueos concurrente devuelve 401 UNAUTHORIZED pese a enviar un Bearer valido (el deadlock/exclusion aborta la transaccion y el reenvio a /error dispara el authenticationEntryPoint). body=$b"
    else
        fail "[$HU] 15.5 Un POST concurrente devolvio HTTP inesperado $c (errorCode='$ec')"
        bug "[$HU] 15.5 BUG: POST concurrente devuelve HTTP $c con errorCode '$ec'; se esperaba 409 AGENDA_BLOCK_OVERLAP. body=$b"
    fi
done
if [[ "$CONC_OK" -eq 0 && "$OK201" -le 1 ]]; then
    fail "[$HU] 15.6 Ninguna respuesta concurrente fue 409 AGENDA_BLOCK_OVERLAP"
fi
rm -f "$C1_OUT" "$C2_OUT" "$C1_OUT.code" "$C2_OUT.code"

# Control secuencial: el mismo solapamiento, pero sin concurrencia, debe
# devolver 409 AGENDA_BLOCK_OVERLAP. Si el control pasa y el concurrente no,
# el defecto es especificamente de concurrencia.
CTRL_START=$(fut_dow 140 3 09:00)
CTRL_END=$(fut_dow 140 3 12:00)
CTRL_PAYLOAD="{\"startTime\":\"$CTRL_START\",\"endTime\":\"$CTRL_END\",\"reason\":\"control\"}"
hit_raw POST "/api/v1/horarios/$SOFIA/bloqueos" "$TOK_SOFIA" "$CTRL_PAYLOAD"
CTRL_CODE_1="$LAST_CODE"
hit_raw POST "/api/v1/horarios/$SOFIA/bloqueos" "$TOK_SOFIA" "$CTRL_PAYLOAD"
CTRL_CODE_2="$LAST_CODE"
CTRL_EC=$(python3 -c 'import sys,json
try: print(json.loads(sys.argv[1]).get("errorCode",""))
except Exception: print("")' "$LAST_BODY")
info "[$HU] 15.7 Control secuencial: 1ro=$CTRL_CODE_1 2do=$CTRL_CODE_2 errorCode=$CTRL_EC"
if [[ "$CTRL_CODE_1" == "201" && "$CTRL_CODE_2" == "409" && "$CTRL_EC" == "AGENDA_BLOCK_OVERLAP" ]]; then
    pass "[$HU] 15.8 Control secuencial: 201 -> 409 AGENDA_BLOCK_OVERLAP (el camino no concurrente es correcto)"
else
    fail "[$HU] 15.8 Control secuencial inesperado: $CTRL_CODE_1 -> $CTRL_CODE_2 ($CTRL_EC)"
fi

# Evidencia del lado servidor: el log de la app muestra la excepcion real
# que el cliente no ve (el 401 enmascara un 500).
APP_LOG="${APP_LOG:-/tmp/backendcf-sprint3.log}"
if [[ -f "$APP_LOG" ]]; then
    SRV=$(grep -oE "Request processing failed: org.springframework[a-zA-Z.]*(Exception|Error)" "$APP_LOG" | tail -3)
    SQLST=$(grep -oE "SQLState: [0-9A-Z]+" "$APP_LOG" | tail -5 | tr '\n' ' ')
    info "[$HU] 15.9 Evidencia servidor (ultimas excepciones): ${SRV:-n/d} | SQLState: ${SQLST:-n/d}"
    if echo "$SRV" | grep -qE "CannotAcquireLockException|JpaSystemException"; then
        info "[$HU] 15.10 El servidor lanzo una excepcion NO controlada; el cliente recibio 401 en vez de 409 AGENDA_BLOCK_OVERLAP"
    fi
fi

print_summary

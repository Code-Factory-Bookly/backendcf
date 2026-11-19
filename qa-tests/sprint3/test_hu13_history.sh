#!/usr/bin/env bash
# =============================================================================
# [HU-13] Historial de reservas del cliente - pruebas E2E
# =============================================================================
# Endpoints verificados en el codigo (BookingController):
#   GET /api/v1/reservas/cliente/historial  -> 200 BookingHistoryPageResponse
#   GET /api/v1/reservas/cliente/{customerId} -> 200 List<BookingResponse> (ruta vieja)
# Contrato: docs/HU-13-historial-reservas-cliente.md
# =============================================================================
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"
source "$SCRIPT_DIR/.e2e-results/seed.env"

HU="HU-13"
CUST="$TOK_hu13_customer_bookly_local"
CUST2="$TOK_hu13_secondary_bookly_local"
ORPHAN="$TOK_hu13_orphan_bookly_local"
HIST="/api/v1/reservas/cliente/historial"

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

# Devuelve el numero de elementos de content (objeto paginado).
content_len() {
    python3 -c 'import sys,json
try:
    d=json.loads(sys.argv[1])
except Exception:
    print("0"); sys.exit(0)
c=d.get("content") if isinstance(d,dict) else None
print(len(c) if isinstance(c,list) else 0)
' "$LAST_BODY"
}

# Devuelve el numero de elementos cuando la respuesta es una lista suelta
# (ruta vieja /api/v1/reservas/cliente/{customerId}).
list_len() {
    python3 -c 'import sys,json
try:
    d=json.loads(sys.argv[1])
except Exception:
    print("0"); sys.exit(0)
print(len(d) if isinstance(d,list) else 0)
' "$LAST_BODY"
}

# Lista los estados presentes en content
statuses_list() {
    python3 -c 'import sys,json
try:
    d=json.loads(sys.argv[1])
except Exception:
    print(""); sys.exit(0)
print(",".join(str(x.get("status")) for x in (d.get("content") or [])))
' "$LAST_BODY"
}

# Lista los startTime presentes en content
starts_list() {
    python3 -c 'import sys,json
try:
    d=json.loads(sys.argv[1])
except Exception:
    print(""); sys.exit(0)
print(",".join(str(x.get("startTime")) for x in (d.get("content") or [])))
' "$LAST_BODY"
}

# =============================================================================
banner "[$HU] 1. Historial del cliente con reservas"
# =============================================================================
hit "[$HU] 1.1 GET historial responde 200" 200 GET "$HIST" "$CUST"
N=$(content_len)
if [[ "$N" -gt 0 ]]; then
    pass "[$HU] 1.2 content no vacio ($N reservas)"
else
    fail "[$HU] 1.2 content deberia traer reservas: $LAST_BODY"
fi
FIRST=$(json_get content.0)
if [[ "$FIRST" == *"startTime"* ]]; then pass "[$HU] 1.3 Cada reserva trae startTime"; else fail "[$HU] 1.3 Falta startTime: $LAST_BODY"; fi
if [[ "$FIRST" == *"serviceName"* ]]; then pass "[$HU] 1.4 Cada reserva trae serviceName"; else fail "[$HU] 1.4 Falta serviceName: $LAST_BODY"; fi
if [[ "$FIRST" == *"professionalName"* ]]; then pass "[$HU] 1.5 Cada reserva trae professionalName"; else fail "[$HU] 1.5 Falta professionalName: $LAST_BODY"; fi
if [[ "$FIRST" == *"status"* ]]; then pass "[$HU] 1.6 Cada reserva trae status"; else fail "[$HU] 1.6 Falta status: $LAST_BODY"; fi

# =============================================================================
banner "[$HU] 2. Filtro por rango de fechas (from + to)"
# =============================================================================
FROM=$(date -d "-15 days" +%Y-%m-%dT00:00:00)
TO=$(date -d "+40 days" +%Y-%m-%dT00:00:00)
hit "[$HU] 2.1 GET historial con from+to responde 200" 200 GET "$HIST?from=$FROM&to=$TO" "$CUST"
N_RANGE=$(content_len)
if [[ "$N_RANGE" -gt 0 && "$N_RANGE" -le "$N" ]]; then
    pass "[$HU] 2.2 El filtro por rango devuelve un subconjunto ($N_RANGE de $N)"
else
    fail "[$HU] 2.3 Conteo inesperado con filtro de rango: $N_RANGE (total $N)"
fi
OUT_OF_RANGE=$(python3 -c '
import sys,json
d=json.loads(sys.argv[1]); f=sys.argv[2]; t=sys.argv[3]
bad=[x for x in d.get("content",[]) if not (f <= x["startTime"] <= t)]
print(len(bad))
' "$LAST_BODY" "$FROM" "$TO")
if [[ "$OUT_OF_RANGE" == "0" ]]; then
    pass "[$HU] 2.3 Todas las reservas del rango caen dentro de from/to"
else
    fail "[$HU] 2.3 Hay $OUT_OF_RANGE reservas fuera del rango from/to: $LAST_BODY"
fi

# =============================================================================
banner "[$HU] 3. Filtro por estado CONFIRMADA"
# =============================================================================
hit "[$HU] 3.1 GET historial con status=CONFIRMADA responde 200" 200 GET "$HIST?status=CONFIRMADA" "$CUST"
ST=$(statuses_list)
NON_CONF=$(python3 -c '
import sys
st=[x for x in sys.argv[1].split(",") if x]
print(len([x for x in st if x!="CONFIRMADA"]))
' "$ST")
if [[ -n "$ST" && "$NON_CONF" == "0" ]]; then
    pass "[$HU] 3.2 Todas las reservas filtradas estan en estado CONFIRMADA"
else
    fail "[$HU] 3.3 Hay estados distintos de CONFIRMADA en el resultado filtrado: '$ST'"
fi
if [[ "$(content_len)" -gt 0 ]]; then
    pass "[$HU] 3.4 El filtro por estado devuelve contenido no vacio"
else
    fail "[$HU] 3.4 El filtro por estado devuelve content vacio"
fi

# =============================================================================
banner "[$HU] 4. Filtro por estado invalido"
# =============================================================================
hit "[$HU] 4.1 GET historial con status=INVALIDO responde 400 (no 500)" 400 GET "$HIST?status=INVALIDO" "$CUST"
if [[ "$LAST_CODE" == "500" ]]; then
    bug "[$HU] 4.2 BUG: GET $HIST?status=INVALIDO devuelve 500 en vez de 400"
fi

# =============================================================================
banner "[$HU] 5. Rango invertido (from > to)"
# =============================================================================
FROM2=$(date -d "+40 days" +%Y-%m-%dT00:00:00)
TO2=$(date -d "-15 days" +%Y-%m-%dT00:00:00)
hit "[$HU] 5.1 GET historial con from > to responde 400" 400 GET "$HIST?from=$FROM2&to=$TO2" "$CUST"

# =============================================================================
banner "[$HU] 6. Paginacion invalida"
# =============================================================================
hit "[$HU] 6.1 GET historial con page=-1 responde 400" 400 GET "$HIST?page=-1" "$CUST"
hit "[$HU] 6.2 GET historial con size=0 responde 400" 400 GET "$HIST?size=0" "$CUST"
hit "[$HU] 6.3 GET historial con size=101 responde 400" 400 GET "$HIST?size=101" "$CUST"
hit "[$HU] 6.4 GET historial con size=100 responde 200" 200 GET "$HIST?size=100" "$CUST"

# =============================================================================
banner "[$HU] 7. Orden invalido y orden valido"
# =============================================================================
hit "[$HU] 7.1 GET historial con order=INVALIDO responde 400" 400 GET "$HIST?order=INVALIDO" "$CUST"

hit "[$HU] 7.2 GET historial con order=desc responde 200" 200 GET "$HIST?order=desc" "$CUST"
DESC_STARTS=$(starts_list)
ASC_STARTS=""
hit_raw GET "$HIST?order=asc" "$CUST"
ASC_STARTS=$(starts_list)
if [[ -n "$DESC_STARTS" && "$DESC_STARTS" == "$ASC_STARTS" ]]; then
    fail "[$HU] 7.3 order=desc y order=asc devuelven exactamente las mismas reservas (el orden no se aplica): $DESC_STARTS"
    bug "[$HU] 7.3 BUG: GET $HIST?order=desc devuelve el mismo orden que order=asc (el parametro order no se aplica). desc=$DESC_STARTS asc=$ASC_STARTS"
else
    pass "[$HU] 7.3 order=desc y order=asc producen ordenes distintos"
fi
DESC_SORTED=$(python3 -c '
import sys,json
try: d=json.loads(sys.argv[1])
except Exception: print("no"); raise SystemExit
xs=[x["startTime"] for x in d.get("content",[])]
print("yes" if xs==sorted(xs,reverse=True) else "no")
' "$LAST_BODY")
# el ultimo body es el de order=asc: se re-consulta desc para comprobarlo
hit_raw GET "$HIST?order=desc" "$CUST"
DESC_SORTED=$(python3 -c '
import sys,json
try: d=json.loads(sys.argv[1])
except Exception: print("no"); raise SystemExit
xs=[x["startTime"] for x in d.get("content",[])]
print("yes" if xs==sorted(xs,reverse=True) else "no")
' "$LAST_BODY")
if [[ "$DESC_SORTED" == "yes" ]]; then
    pass "[$HU] 7.4 order=desc ordena de forma descendente por startTime"
else
    fail "[$HU] 7.4 order=desc NO ordena de forma descendente por startTime: $LAST_BODY"
    bug "[$HU] 7.4 BUG: GET $HIST?order=desc no ordena descendentemente por startTime. body=$LAST_BODY"
fi
hit_raw GET "$HIST" "$CUST"
ASC_SORTED=$(python3 -c '
import sys,json
try: d=json.loads(sys.argv[1])
except Exception: print("no"); raise SystemExit
xs=[x["startTime"] for x in d.get("content",[])]
print("yes" if xs==sorted(xs) else "no")
' "$LAST_BODY")
if [[ "$ASC_SORTED" == "yes" ]]; then
    pass "[$HU] 7.5 El orden por defecto (asc) es ascendente por startTime"
else
    fail "[$HU] 7.5 El orden por defecto NO es ascendente por startTime: $LAST_BODY"
    bug "[$HU] 7.5 BUG: GET $HIST (sin order) no ordena ascendentemente por startTime. body=$LAST_BODY"
fi

# =============================================================================
banner "[$HU] 8. Cliente sin reservas"
# =============================================================================
hit "[$HU] 8.1 GET historial de cliente sin reservas responde 200" 200 GET "$HIST" "$ORPHAN"
N_ORPHAN=$(content_len)
if [[ "$N_ORPHAN" == "0" ]]; then
    pass "[$HU] 8.2 content es una lista vacia"
else
    fail "[$HU] 8.2 Se esperaba content=[] y llegaron $N_ORPHAN elementos: $LAST_BODY"
fi
MSG=$(json_get message)
if [[ -n "$MSG" && "$MSG" != "None" ]]; then
    pass "[$HU] 8.3 El mensaje informativo no esta vacio: '$MSG'"
else
    fail "[$HU] 8.3 El mensaje informativo esta vacio: $LAST_BODY"
fi

# =============================================================================
banner "[$HU] 9. Control de acceso por rol"
# =============================================================================
hit "[$HU] 9.1 GET historial como ADMIN responde 403" 403 GET "$HIST" "$ADMIN_TOKEN"
hit "[$HU] 9.2 GET historial como PROFESSIONAL responde 403" 403 GET "$HIST" "$TOK_prof_sofia"
hit "[$HU] 9.3 GET historial sin token responde 401" 401 GET "$HIST" ""

# =============================================================================
banner "[$HU] 10. Aislamiento: customerId ajeno en el query"
# =============================================================================
# CASO CLAVE: la ruta nueva NO acepta customerId; debe ignorar el parametro
# y devolver SIEMPRE el historial del cliente autenticado.
hit "[$HU] 10.1 GET historial con customerId ajeno responde 200" 200 GET "$HIST?customerId=$HU13_SECOND_CUSTOMER_ID" "$CUST"
LEAK=$(python3 -c '
import sys,json
try: d=json.loads(sys.argv[1])
except Exception: print("leak"); raise SystemExit
foreign=sys.argv[2]
ids={str(x.get("customerId")) for x in d.get("content",[])}
print("leak" if foreign in ids else "ok")
' "$LAST_BODY" "$HU13_SECOND_CUSTOMER_ID")
if [[ "$LEAK" == "ok" ]]; then
    pass "[$HU] 10.2 El historial NO contiene reservas del cliente ajeno (el parametro se ignora)"
else
    fail "[$HU] 10.2 Se filtraron reservas de otro cliente"
    bug "[$HU] 10.2 BUG: GET $HIST?customerId=<ajeno> devuelve reservas de otro cliente (el parametro no se ignora). body=$LAST_BODY"
fi
if [[ "$(json_get content.0.customerId)" == "$HU13_CUSTOMER_ID" ]]; then
    pass "[$HU] 10.3 Las reservas devueltas pertenecen al cliente autenticado"
else
    fail "[$HU] 10.3 customerId de las reservas != cliente autenticado: $LAST_BODY"
fi

# =============================================================================
banner "[$HU] 11. Ruta vieja /api/v1/reservas/cliente/{customerId} como ADMIN"
# =============================================================================
# CASO CLAVE: la ruta vieja allows que el ADMIN vea el historial de otro
# cliente y no pagina. Es una inconsistencia de seguridad frente a la ruta
# nueva (HU-13 no filtra por rol ADMIN).
hit "[$HU] 11.1 GET ruta vieja de otro cliente como ADMIN responde 200" 200 GET "/api/v1/reservas/cliente/$HU13_SECOND_CUSTOMER_ID" "$ADMIN_TOKEN"
OLD_N=$(list_len)
if [[ "$OLD_N" -gt 0 ]]; then
    pass "[$HU] 11.2 La ruta vieja devuelve $OLD_N reservas de otro cliente al ADMIN"
    bug "[$HU] 11.2 BUG (inconsistencia de seguridad): GET /api/v1/reservas/cliente/{customerId} como ADMIN devuelve el historial COMPLETO de otro cliente, sin paginar y sin filtrar. La ruta nueva /api/v1/reservas/cliente/historial restringe a rol CUSTOMER. body=${LAST_BODY:0:400}"
else
    pass "[$HU] 11.2 La ruta vieja no devolvio reservas de otro cliente al ADMIN"
fi

# =============================================================================
banner "[$HU] 12. Las 4 reservas propias del cliente"
# =============================================================================
# hu13.customer tiene: 1 CONFIRMADA futura (API), 1 CONFIRMADA pasada (SQL),
# 1 COMPLETED pasada (SQL) y 1 CANCELADA pasada (SQL).
hit "[$HU] 12.1 GET historial completo responde 200" 200 GET "$HIST?size=100" "$CUST"
N_ALL=$(content_len)
# El cliente debe ver al menos sus 4 reservas sembradas por el seed
# (CONFIRMADA pasada, COMPLETED pasada, CANCELADA pasada y CONFIRMADA futura).
# El conteo exacto depende de si otros tests ya corrieron sobre la misma base,
# asi que se valida por presencia de los identificadores sembrados.
N_ALL_OK=1
for bid in "$BOOKING_PAST_CONFIRMED_ID" "$BOOKING_PAST_COMPLETED_ID" "$BOOKING_PAST_CANCELLED_ID"; do
    if [[ "$LAST_BODY" == *"$bid"* ]]; then
        pass "[$HU] 12.2 El historial incluye la reserva sembrada $bid"
    else
        fail "[$HU] 12.2 El historial NO incluye la reserva sembrada $bid"
        N_ALL_OK=0
    fi
done
# Y NO debe ver la reserva del cliente secundario
FOREIGN_SEEN=$(python3 -c '
import sys,json
d=json.loads(sys.argv[1]); foreign=sys.argv[2]
print("yes" if any(x.get("id")==foreign for x in d.get("content",[])) else "no")
' "$LAST_BODY" "$BOOKING_OTHER_CONFIRMED_ID")
if [[ "$FOREIGN_SEEN" == "no" ]]; then
    pass "[$HU] 12.2b El historial NO incluye la reserva del cliente secundario ($BOOKING_OTHER_CONFIRMED_ID)"
else
    fail "[$HU] 12.2b El historial incluye una reserva del cliente secundario"
    bug "[$HU] 12.2b BUG: GET $HIST devuelve la reserva $BOOKING_OTHER_CONFIRMED_ID que pertenece a otro cliente"
fi
if [[ "$N_ALL" -ge 4 ]]; then
    pass "[$HU] 12.2c El cliente ve al menos 4 reservas propias (total $N_ALL)"
else
    fail "[$HU] 12.2c Se esperaban al menos 4 reservas propias y llegaron $N_ALL"
fi
ALL_ST=$(statuses_list)
for st in CONFIRMADA COMPLETED CANCELADA; do
    if [[ "$ALL_ST" == *"$st"* ]]; then
        pass "[$HU] 12.3 El historial incluye reservas en estado $st"
    else
        fail "[$HU] 12.3 El historial NO incluye reservas en estado $st (estados: $ALL_ST)"
    fi
done
# Todos los elementos del historial deben pertenecer al cliente autenticado
ALL_OWN=$(python3 -c '
import sys,json
d=json.loads(sys.argv[1]); own=sys.argv[2]
print("yes" if all(x.get("customerId")==own for x in d.get("content",[])) else "no")
' "$LAST_BODY" "$HU13_CUSTOMER_ID")
if [[ "$ALL_OWN" == "yes" ]]; then
    pass "[$HU] 12.4 Todas las reservas del historial pertenecen al cliente autenticado"
else
    fail "[$HU] 12.4 Hay reservas de otro cliente en el historial"
    bug "[$HU] 12.4 BUG: GET $HIST devuelve reservas cuyo customerId != al cliente autenticado"
fi

print_summary

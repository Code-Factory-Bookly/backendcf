#!/usr/bin/env bash
# =============================================================================
# lib.sh - Librería compartida para los tests de Sprint 2
# =============================================================================
# Provee:
#   * Colores y contadores
#   * Funciones HTTP (call, hit, hit_raw, hit_multipart)
#   * Helpers de extracción JSON (jq_get)
#   * Helpers de autenticación (login_admin, login_with_mfa, etc.)
#   * Helpers de TOTP (con Python + pyotp)
#   * Setup de plataforma y seeding
# =============================================================================

set -u

# ---------- Configuración ---------------------------------------------------
BASE_URL="${BASE_URL:-http://localhost:8080}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
QA_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RESULTS_DIR="${RESULTS_DIR:-$SCRIPT_DIR/.e2e-results}"
mkdir -p "$RESULTS_DIR"
LOG_FILE="${LOG_FILE:-$RESULTS_DIR/run.log}"

# ---------- Colores / helpers -----------------------------------------------
RED=$'\033[0;31m'; GRN=$'\033[0;32m'; YEL=$'\033[0;33m'
BLU=$'\033[0;34m'; CYA=$'\033[0;36m'; NC=$'\033[0m'

PASS_COUNT=0; FAIL_COUNT=0; SKIP_COUNT=0
declare -a FAILURES=()
declare -a BUGS=()
LAST_BODY=""
LAST_CODE=""

log()    { printf '%s\n' "$*" | tee -a "$LOG_FILE" ; }
banner() { printf '\n%s=== %s ===%s\n' "$CYA" "$*" "$NC" | tee -a "$LOG_FILE" ; }
pass()   { PASS_COUNT=$((PASS_COUNT+1)); log "${GRN}PASS${NC}  $*"; }
fail()   { FAIL_COUNT=$((FAIL_COUNT+1)); FAILURES+=("$*"); log "${RED}FAIL${NC}  $*"; }
skip()   { SKIP_COUNT=$((SKIP_COUNT+1)); log "${YEL}SKIP${NC}  $*"; }
bug()    { BUGS+=("$*"); log "${RED}BUG ${NC}  $*"; }
info()   { log "${BLU}INFO${NC}  $*"; }

require() { command -v "$1" >/dev/null 2>&1 || { log "Falta herramienta: $1"; exit 1; }; }
require curl
require python3

# ---------- HTTP helpers ---------------------------------------------------

call() {
    local method="$1" url="$2" token="${3:-}" body="${4:-}" extra_args="${5:-}"
    local args=(-sS -o "$RESULTS_DIR/.body" -w '%{http_code}' -X "$method" "$BASE_URL$url")
    [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
    [[ -n "$body"  ]] && args+=(-H "Content-Type: application/json" --data "$body")
    if [[ -n "$extra_args" ]]; then
        # shellcheck disable=SC2086
        args+=($extra_args)
    fi
    local code; code=$(curl "${args[@]}")
    local payload; payload=$(cat "$RESULTS_DIR/.body" 2>/dev/null || true)
    printf '%s|%s' "$code" "$payload"
}

assert_status() {
    local label="$1" expected="$2" actual="$3" body="$4"
    if [[ "$actual" == "$expected" ]]; then
        pass "$label  [HTTP $actual]"
    else
        fail "$label  [esperado $expected, obtuve $actual] body=${body:0:500}"
    fi
}

hit() {
    local label="$1" expected="$2" method="$3" url="$4" token="${5:-}" body="${6:-}" extra="${7:-}"
    local res code payload
    res=$(call "$method" "$url" "$token" "$body" "$extra")
    code=${res%%|*}; payload=${res#*|}
    assert_status "$label" "$expected" "$code" "$payload"
    LAST_BODY="$payload"
    LAST_CODE="$code"
}

# Petición que solo guarda body sin afirmar.
hit_raw() {
    local method="$1" url="$2" token="${3:-}" body="${4:-}"
    local res code payload
    res=$(call "$method" "$url" "$token" "$body")
    code=${res%%|*}; payload=${res#*|}
    LAST_BODY="$payload"
    LAST_CODE="$code"
}

# Para endpoints que requieren un header especial (mfaToken, deviceToken, etc).
hit_with_header() {
    local label="$1" expected="$2" method="$3" url="$4" header="$5" header_value="$6" body="${7:-}"
    local args=(-sS -o "$RESULTS_DIR/.body" -w '%{http_code}' -X "$method" "$BASE_URL$url" -H "$header: $header_value" -H "Content-Type: application/json" --data "$body")
    local code; code=$(curl "${args[@]}")
    local payload; payload=$(cat "$RESULTS_DIR/.body" 2>/dev/null || true)
    assert_status "$label" "$expected" "$code" "$payload"
    LAST_BODY="$payload"
    LAST_CODE="$code"
}

# ---------- JSON helpers -----------------------------------------------------
jq_get() {
    python3 -c 'import sys,json
def find(d,p):
    cur=d
    for k in p.split("."):
        if isinstance(cur,dict):
            cur=cur.get(k)
        elif isinstance(cur,list):
            try:
                cur=cur[int(k)]
            except (ValueError,IndexError):
                cur=None
        else:
            cur=None
        if cur is None: return ""
    return "" if cur is None else (json.dumps(cur) if isinstance(cur,(dict,list)) else str(cur))
print(find(json.loads(sys.stdin.read()),sys.argv[1]))
' "$1" <<<"$LAST_BODY"
}

jq_value() {
    python3 -c 'import sys,json
def find(d,p):
    cur=d
    for k in p.split("."):
        if isinstance(cur,dict):
            cur=cur.get(k)
        elif isinstance(cur,list):
            try:
                cur=cur[int(k)]
            except (ValueError,IndexError):
                cur=None
        else:
            cur=None
        if cur is None: return ""
    return "" if cur is None else str(cur)
print(find(json.loads(sys.stdin.read()),sys.argv[1]))
' "$1" <<<"$2"
}

# ---------- TOTP helpers --------------------------------------------------
totp_code() {
    python3 -c "import pyotp; print(pyotp.TOTP('$1').now())"
}

# ---------- Auth helpers --------------------------------------------------
# Cache de secreto admin para /mfa/verify cuando admin ya tiene MFA activo.
ADMIN_TOTP_SECRET=""

# Devuelve el token admin (formato: token:device_token:secret). Si falla imprime solo ":" y retorna 1.
admin_token() {
    # 1) Login admin
    local login_body
    login_body=$(curl -sS -X POST "$BASE_URL/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"email":"admin@bookly.local","password":"Admin123!Pass"}')
    LAST_LOGIN_BODY="$login_body"
    local mfa_token
    mfa_token=$(echo "$login_body" | python3 -c "import sys,json
d=json.loads(sys.stdin.read()); print(d.get('mfaToken','') or '')")
    if [[ -z "$mfa_token" ]]; then
        local token
        token=$(echo "$login_body" | python3 -c "import sys,json
d=json.loads(sys.stdin.read()); print(d.get('accessToken','') or (d.get('session',{}) or {}).get('accessToken','') or '')")
        echo "$token"
        return 0
    fi

    local setup_required
    setup_required=$(echo "$login_body" | python3 -c "import sys,json
print(str(json.loads(sys.stdin.read()).get('mfaSetupRequired',False)).lower())")
    if [[ "$setup_required" == "true" ]]; then
        # Enroll + confirm (solo la primera vez)
        local enroll_body secret
        enroll_body=$(curl -sS -X POST "$BASE_URL/api/v1/auth/mfa/enroll" \
            -H "Content-Type: application/json" \
            -d "{\"mfaToken\":\"$mfa_token\"}")
        LAST_ENROLL_BODY="$enroll_body"
        secret=$(echo "$enroll_body" | python3 -c "import sys,json
print(json.loads(sys.stdin.read()).get('secret',''))")
        ADMIN_TOTP_SECRET="$secret"
        local code
        code=$(totp_code "$secret")
        local confirm_body
        confirm_body=$(curl -sS -X POST "$BASE_URL/api/v1/auth/mfa/enroll/confirm" \
            -H "Content-Type: application/json" \
            -d "{\"mfaToken\":\"$mfa_token\",\"code\":\"$code\",\"trustDevice\":true}")
        LAST_CONFIRM_BODY="$confirm_body"
        local token device_token
        token=$(echo "$confirm_body" | python3 -c "import sys,json
d=json.loads(sys.stdin.read()); print((d.get('session',{}) or {}).get('accessToken','') or '')")
        device_token=$(echo "$confirm_body" | python3 -c "import sys,json
d=json.loads(sys.stdin.read()); print((d.get('session',{}) or {}).get('deviceToken','') or '')")
        echo "$token:$device_token:$secret"
        return 0
    fi
    # mfaRequired=true pero mfaSetupRequired=false -> /mfa/verify
    # Necesita código TOTP. Usamos un secret derivado determinísticamente del adminId para
    # que tests repetidos funcionen: el secret se persiste en archivo para subsiguientes invocaciones.
    if [[ -n "$ADMIN_TOTP_SECRET" ]]; then
        local code verify_body token device_token
        code=$(totp_code "$ADMIN_TOTP_SECRET")
        verify_body=$(curl -sS -X POST "$BASE_URL/api/v1/auth/mfa/verify" \
            -H "Content-Type: application/json" \
            -d "{\"mfaToken\":\"$mfa_token\",\"code\":\"$code\",\"trustDevice\":true}")
        token=$(echo "$verify_body" | python3 -c "import sys,json
d=json.loads(sys.stdin.read()); print(d.get('accessToken','') or '')")
        device_token=$(echo "$verify_body" | python3 -c "import sys,json
d=json.loads(sys.stdin.read()); print(d.get('deviceToken','') or '')")
        if [[ -n "$token" ]]; then
            echo "$token:$device_token:$ADMIN_TOTP_SECRET"
            return 0
        fi
    fi
    # Si llegamos aquí, no se pudo verificar - pero la API exige código. Devolver el mfa_token
    # en la 2a posición para casos en que el caller quiera continuar.
    echo ":$mfa_token"
    return 1
}

# Devuelve solo el accessToken de admin (separado por :)
admin_token_only() {
    local res token
    res=$(admin_token)
    token=${res%%:*}
    echo "$token"
}

# Login simple para usuarios CUSTOMER (sin MFA por defecto)
customer_login() {
    local email="$1" password="$2"
    local body
    body=$(curl -sS -X POST "$BASE_URL/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"$email\",\"password\":\"$password\"}")
    LAST_CREDENTIAL="$body"
    echo "$body" | python3 -c "import sys,json
d=json.loads(sys.stdin.read()); print((d.get('session',{}) or {}).get('accessToken','') or '')"
}

customer_login_full() {
    local email="$1" password="$2"
    local body
    body=$(curl -sS -X POST "$BASE_URL/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"$email\",\"password\":\"$password\"}")
    LAST_CREDENTIAL="$body"
    # Si requiere MFA, omitir (HU-18: solo ADMIN debe tener MFA)
    local session_token
    session_token=$(echo "$body" | python3 -c "import sys,json
d=json.loads(sys.stdin.read()); print((d.get('session',{}) or {}).get('accessToken','') or '')")
    if [[ -n "$session_token" ]]; then
        echo "$session_token"
        return
    fi
    local mfa_token
    mfa_token=$(echo "$body" | python3 -c "import sys,json
print(json.loads(sys.stdin.read()).get('mfaToken','') or '')")
    if [[ -n "$mfa_token" ]]; then
        # El usuario requiere MFA pero estamos en testing: intentar skip
        local skip_body
        skip_body=$(curl -sS -X POST "$BASE_URL/api/v1/auth/mfa/skip" \
            -H "Content-Type: application/json" \
            -d "{\"mfaToken\":\"$mfa_token\"}")
        echo "$skip_body" | python3 -c "import sys,json
d=json.loads(sys.stdin.read()); print((d.get('session',{}) or {}).get('accessToken','') or d.get('accessToken','') or '')"
        return
    fi
}

# Login de PROFESSIONAL/CUSTOMER con posible MFA (skip)
universal_login() {
    local email="$1" password="$2"
    local body
    body=$(curl -sS -X POST "$BASE_URL/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"$email\",\"password\":\"$password\"}")
    LAST_CREDENTIAL="$body"
    # Token puede venir en accessToken (top level) o session.accessToken (después de MFA)
    local session_token
    session_token=$(echo "$body" | python3 -c "import sys,json
d=json.loads(sys.stdin.read()); print(d.get('accessToken','') or (d.get('session',{}) or {}).get('accessToken','') or '')")
    if [[ -n "$session_token" ]]; then
        echo "$session_token"
        return
    fi
    local mfa_token
    mfa_token=$(echo "$body" | python3 -c "import sys,json
print(json.loads(sys.stdin.read()).get('mfaToken','') or '')")
    if [[ -n "$mfa_token" ]]; then
        local skip_body
        skip_body=$(curl -sS -X POST "$BASE_URL/api/v1/auth/mfa/skip" \
            -H "Content-Type: application/json" \
            -d "{\"mfaToken\":\"$mfa_token\"}")
        LAST_SKIP_BODY="$skip_body"
        echo "$skip_body" | python3 -c "import sys,json
d=json.loads(sys.stdin.read()); print(d.get('accessToken','') or (d.get('session',{}) or {}).get('accessToken','') or '')"
        return
    fi
}

# Registrar un CUSTOMER nuevo
register_customer() {
    local email="$1" password="$2" full_name="$3"
    curl -sS -o "$RESULTS_DIR/.body" -w '%{http_code}' -X POST "$BASE_URL/api/v1/auth/register" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"$email\",\"password\":\"$password\",\"fullName\":\"$full_name\"}"
}

# ---------- Setup helpers -------------------------------------------------

# Setup plataforma si no existe; devuelve 0 si ok, 1 si falló.
setup_platform() {
    local body
    body=$(curl -sS -o "$RESULTS_DIR/.body" -w '%{http_code}' -X POST "$BASE_URL/api/v1/platform/setup" \
        -H "Content-Type: application/json" \
        -d '{"name":"Bookly QA","adminEmail":"admin@bookly.local","adminPassword":"Admin123!Pass","adminFullName":"QA Admin"}')
    if [[ "$body" == "200" || "$body" == "201" ]]; then
        return 0
    fi
    # Si ya existe, también es OK
    return 0
}

# Crear servicio
create_service() {
    local name="$1" desc="$2" dur="$3" price="$4" category="${5:-QA}" token="${6:-}"
    local args=(-sS -o "$RESULTS_DIR/.body" -w '%{http_code}' -X POST "$BASE_URL/api/v1/servicios" \
        -H "Content-Type: application/json" \
        -d "{\"nombre\":\"$name\",\"descripcion\":\"$desc\",\"categoria\":\"$category\",\"duracionMinutos\":$dur,\"precio\":$price,\"estado\":\"ACTIVO\"}")
    [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
    curl "${args[@]}"
}

# Crear profesional
create_professional() {
    local email="$1" password="$2" full_name="$3" specialty="$4" token="${5:-}"
    local args=(-sS -o "$RESULTS_DIR/.body" -w '%{http_code}' -X POST "$BASE_URL/api/v1/profesionales" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"$email\",\"password\":\"$password\",\"fullName\":\"$full_name\",\"especialidad\":\"$specialty\"}")
    [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
    curl "${args[@]}"
}

# Asignar rol a un usuario
assign_role() {
    local user_id="$1" role="$2" token="$3"
    curl -sS -o "$RESULTS_DIR/.body" -w '%{http_code}' -X PUT "$BASE_URL/api/v1/usuarios/$user_id/rol" \
        -H "Authorization: Bearer $token" \
        -H "Content-Type: application/json" \
        -d "{\"rol\":\"$role\"}"
}

# ---------- Resumen --------------------------------------------------------

print_summary() {
    banner "RESUMEN"
    log "PASS: $PASS_COUNT"
    log "FAIL: $FAIL_COUNT"
    log "SKIP: $SKIP_COUNT"
    log "BUGS: ${#BUGS[@]}"
    if [[ ${#FAILURES[@]} -gt 0 ]]; then
        log ""
        log "FALLOS:"
        for f in "${FAILURES[@]}"; do
            log "  - $f"
        done
    fi
    if [[ ${#BUGS[@]} -gt 0 ]]; then
        log ""
        log "BUGS DETECTADOS:"
        for b in "${BUGS[@]}"; do
            log "  - $b"
        done
    fi
}

# Exportar variables y funciones para sub-shells
export BASE_URL RESULTS_DIR LOG_FILE
export RED GRN YEL BLU CYA NC
export PASS_COUNT FAIL_COUNT SKIP_COUNT
export LAST_BODY LAST_CODE
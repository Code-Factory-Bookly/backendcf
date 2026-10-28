#!/usr/bin/env bash
#
# sonar-metrics.sh
#
# Muestra las metricas principales de SonarCloud y verifica los criterios
# de calidad obligatorios del proyecto. Salida a color en consola.
#
# Uso:
#   ./sonar-metrics.sh              # rama actual
#   ./sonar-metrics.sh main         # rama especifica
#
# El script puede ejecutarse desde cualquier directorio: resuelve la raiz
# del repositorio git de forma automatica.
#
# Variables de entorno:
#   SONAR_PROJECT_KEY   clave del proyecto en SonarCloud
#   SONAR_TOKEN         token de SonarCloud (opcional: habilita el detalle)
#   NO_COLOR            desactiva el color (estandar https://no-color.org)
#
# Dependencias: curl, jq, awk y git.

set -uo pipefail

PROJECT_KEY="${SONAR_PROJECT_KEY:-Code-Factory-Bookly_backendcf}"
API="https://sonarcloud.io/api"

# Umbrales de los criterios de calidad (Lineamientos.md / README_QA.md)
THRESHOLD_COVERAGE=65
THRESHOLD_DEBT_MIN=960      # 2 dias laborables = 960 min
THRESHOLD_COMPLEXITY=50
THRESHOLD_NEW_COVERAGE=80   # Quality Gate de SonarCloud (codigo nuevo)

# Ancho del panel
WIDTH=78

# =============================================================================
# Color (ANSI 256; degrada a texto plano si no hay terminal o NO_COLOR)
# =============================================================================
if [[ -t 1 && -z "${NO_COLOR:-}" && "${TERM:-dumb}" != "dumb" ]]; then
    C_RESET=$'\033[0m'
    C_BOLD=$'\033[1m'
    C_DIM=$'\033[2m'
    C_RED=$'\033[38;5;203m'
    C_GREEN=$'\033[38;5;114m'
    C_YELLOW=$'\033[38;5;221m'
    C_BLUE=$'\033[38;5;75m'
    C_CYAN=$'\033[38;5;80m'
    C_MAGENTA=$'\033[38;5;176m'
    C_GRAY=$'\033[38;5;245m'
    C_GRAY_DARK=$'\033[38;5;240m'
    C_BG_GREEN=$'\033[48;5;29m'
    C_BG_RED=$'\033[48;5;124m'
    C_BG_YELLOW=$'\033[48;5;130m'
    C_BG_GRAY=$'\033[48;5;238m'
else
    C_RESET='' C_BOLD='' C_DIM='' C_RED='' C_GREEN='' C_YELLOW='' C_BLUE=''
    C_CYAN='' C_MAGENTA='' C_GRAY='' C_GRAY_DARK='' C_BG_GREEN='' C_BG_RED=''
    C_BG_YELLOW='' C_BG_GRAY=''
fi

# =============================================================================
# Helpers de presentacion
# =============================================================================
hr()   { printf '%s%s%s\n' "$C_GRAY_DARK" "$(printf '─%.0s' $(seq 1 "$WIDTH"))" "$C_RESET"; }
hr_d() { printf '%s%s%s\n' "$C_GRAY" "$(printf '═%.0s' $(seq 1 "$WIDTH"))" "$C_RESET"; }

seccion() { printf '\n%s%s%s\n' "$C_BOLD" "$1" "$C_RESET"; }

# badge <texto> <bg> <fg>
badge() { printf '%s%s %s %s' "$2" "$C_BOLD" "$1" "$C_RESET"; }

# status_badge <CUMPLE|NO CUMPLE|N/A>
status_badge() {
    case "$1" in
        "CUMPLE")    badge "✓ CUMPLE"    "$C_BG_GREEN" "$C_BOLD" ;;
        "NO CUMPLE") badge "✗ NO CUMPLE" "$C_BG_RED"   "$C_BOLD" ;;
        *)           badge "— N/A"       "$C_BG_GRAY"  "$C_BOLD" ;;
    esac
}

# color_por_estado <valor> <umbral> <op: ge|le|lt>
color_por_estado() {
    local valor="${1:-0}" umbral="$2" op="$3"
    [[ ! "$valor" =~ ^[0-9]+(\.[0-9]+)?$ ]] && { printf '%s' "$C_GRAY"; return; }
    local cumple=1
    case "$op" in
        ge) awk "BEGIN{exit !($valor >= $umbral)}" || cumple=0 ;;
        le) awk "BEGIN{exit !($valor <= $umbral)}" || cumple=0 ;;
        lt) awk "BEGIN{exit !($valor <  $umbral)}" || cumple=0 ;;
    esac
    if [[ "$cumple" == "1" ]]; then printf '%s' "$C_GREEN"; else printf '%s' "$C_RED"; fi
}

# rating_sonar <1.0..5.0> -> A..E
rating_letra() {
    case "$1" in
        1.0|1) echo "A" ;; 2.0|2) echo "B" ;; 3.0|3) echo "C" ;;
        4.0|4) echo "D" ;; 5.0|5) echo "E" ;; *) echo "?" ;;
    esac
}

# barra <valor> <maximo> <ancho>
# Acepta valores con % sufijo (ej. "92.6%") y los normaliza.
barra() {
    local valor="${1:-0}" maximo="${2:-100}" ancho="${3:-20}"
    valor="${valor//%/}"
    [[ ! "$valor" =~ ^[0-9]+(\.[0-9]+)?$ ]] && valor=0
    local llenos vacios
    llenos=$(awk -v v="$valor" -v m="$maximo" -v w="$ancho" \
        'BEGIN { if (m <= 0) { print 0 } else { r = int((v/m)*w + 0.5); if (r > w) r = w; if (r < 0) r = 0; print r } }')
    vacios=$((ancho - llenos))
    if [[ "$llenos" -gt 0 ]]; then
        printf '%s%s%s' "$C_GREEN" "$(printf '█%.0s' $(seq 1 "$llenos"))" "$C_RESET"
    fi
    if [[ "$vacios" -gt 0 ]]; then
        printf '%s%s%s' "$C_GRAY_DARK" "$(printf '░%.0s' $(seq 1 "$vacios"))" "$C_RESET"
    fi
}

# fila_metrica <etiqueta> <valor> <color> <max-opcional>
fila_metrica() {
    local etiqueta="$1" valor="$2" color="$3" max="${4:-}"
    printf '    %s%-30s%s %s%9s%s' "$C_GRAY" "$etiqueta" "$C_RESET" "$color" "$valor" "$C_RESET"
    if [[ -n "$max" ]]; then
        printf '  %s' "$(barra "$valor" "$max" 14)"
    fi
    printf '\n'
}

# pct <metrica> -> "91.6%" o "—"
# Redondea a 1 decimal para evitar valores largos como "93.09831181727904%".
pct() {
    local v="${1:-}"
    if [[ "$v" =~ ^[0-9]+(\.[0-9]+)?$ ]]; then
        printf '%s%%' "$(awk "BEGIN{printf \"%.1f\", $v}")"
    else
        printf '%s—%s' "$C_GRAY" "$C_RESET"
    fi
}

# =============================================================================
# Resolucion de repo, rama y token
# =============================================================================
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# Raiz del repositorio backend:
#   1) el repo git que contiene al script, si lo hay
#   2) el subdirectorio "backend/*"brother del proyecto, si existe
#   3) la carpeta padre del script
REPO_ROOT=""
for candidate in "$SCRIPT_DIR" "$SCRIPT_DIR/.." "$SCRIPT_DIR/../.." \
                 "$SCRIPT_DIR/../backend/cf" "$SCRIPT_DIR/../backendcf"; do
    git_root="$(git -C "$candidate" rev-parse --show-toplevel 2>/dev/null || true)"
    if [[ -n "$git_root" ]]; then
        REPO_ROOT="$git_root"
        break
    fi
done
if [[ -z "$REPO_ROOT" ]]; then
    for candidate in "$SCRIPT_DIR/../backend/cf" "$SCRIPT_DIR/../backendcf" "$SCRIPT_DIR/.."; do
        if [[ -d "$candidate/.git" || -d "$candidate/src/main/java" ]]; then
            REPO_ROOT="$(cd "$candidate" && pwd)"
            break
        fi
    done
fi
REPO_ROOT="${REPO_ROOT:-$SCRIPT_DIR}"
REPO_ROOT="$(cd "$REPO_ROOT" && pwd)"

BRANCH="${1:-}"
if [[ -z "$BRANCH" ]]; then
    BRANCH="$(git -C "$REPO_ROOT" branch --show-current 2>/dev/null || echo main)"
fi
[[ -z "$BRANCH" || "$BRANCH" == "HEAD" ]] && BRANCH="main"

CURRENT_BRANCH="$(git -C "$REPO_ROOT" branch --show-current 2>/dev/null || echo 'desconocida')"

if [[ -z "${SONAR_TOKEN:-}" && -f "$HOME/.sonar_token" ]]; then
    SONAR_TOKEN="$(<"$HOME/.sonar_token")"
fi

AUTH=()
[[ -n "${SONAR_TOKEN:-}" ]] && AUTH=(-u "${SONAR_TOKEN}:")
if [[ -n "${SONAR_TOKEN:-}" ]]; then
    TOKEN_LABEL="${C_GREEN}autenticado${C_RESET}"
else
    TOKEN_LABEL="${C_GRAY}publico (sin token)${C_RESET}"
fi

api() {
    curl -sS --max-time 20 "${AUTH[@]}" "$API$1"
}

# =============================================================================
# Consulta a SonarCloud
# =============================================================================
QG="$(api "/qualitygates/project_status?projectKey=${PROJECT_KEY}&branch=${BRANCH}")"
VULNS_JSON="$(api "/issues/search?componentKeys=${PROJECT_KEY}&branch=${BRANCH}&types=VULNERABILITY&resolved=false&ps=100")"
BUGS_JSON="$(api "/issues/search?componentKeys=${PROJECT_KEY}&branch=${BRANCH}&types=BUG&resolved=false&ps=100")"
# Unified: bugs + vulnerabilities sin resolver (para contar criticas abiertas reales)
ISSUES_JSON="$(api "/issues/search?componentKeys=${PROJECT_KEY}&branch=${BRANCH}&types=BUG,VULNERABILITY&resolved=false&ps=100")"
MEASURES="$(api "/measures/component?component=${PROJECT_KEY}&branch=${BRANCH}&metricKeys=coverage,line_coverage,branch_coverage,sqale_index,new_coverage,new_technical_debt,complexity,cognitive_complexity,violations,bugs,vulnerabilities,security_hotspots,duplicated_lines_density,ncloc,files,reliability_rating,security_rating,new_critical_violations,new_vulnerabilities")"

metric() {
    jq -r --arg m "$1" '
        .component.measures[]?
        | select(.metric == $m)
        | (.value // (.periods[]?.value) // "N/A")
    ' <<<"$MEASURES" | head -n 1
}

QG_STATUS="$(jq -r '.projectStatus.status // "UNKNOWN"' <<<"$QG" 2>/dev/null)"
COVERAGE="$(metric coverage)"
LINE_COVERAGE="$(metric line_coverage)"
BRANCH_COVERAGE="$(metric branch_coverage)"
NEW_COVERAGE="$(metric new_coverage)"
DEBT="$(metric sqale_index)"
NEW_DEBT="$(metric new_technical_debt)"
COMPLEXITY="$(metric complexity)"
COGNITIVE="$(metric cognitive_complexity)"
DUPLICATION="$(metric duplicated_lines_density)"
VIOLATIONS="$(metric violations)"
BUGS="$(metric bugs)"
VULNERABILITIES="$(metric vulnerabilities)"
NEW_VULNS="$(metric new_vulnerabilities)"
CRITICAL="$(metric new_critical_violations)"
# Criticas y bloqueantes realmente abiertas (codigo actual, no solo codigo nuevo)
OPEN_CRITICAL="$(jq -r '[.issues[]? | select((.severity // "") == "CRITICAL" or (.severity // "") == "BLOCKER")] | length' <<<"$ISSUES_JSON" 2>/dev/null || echo 0)"
[[ "$OPEN_CRITICAL" =~ ^[0-9]+$ ]] || OPEN_CRITICAL=0
HOTSPOTS="$(metric security_hotspots)"
NC_LOC="$(metric ncloc)"
FILES="$(metric files)"
RELIABILITY="$(metric reliability_rating)"
SECURITY="$(metric security_rating)"

# Complejidad maxima por clase, registrada en el reporte local del proyecto.
QUALITY_STATUS_FILE="$REPO_ROOT/docs/QUALITY-STATUS.md"
MAX_COMPLEXITY="N/A"
if [[ -f "$QUALITY_STATUS_FILE" ]]; then
    MAX_COMPLEXITY="$(grep -oE '\|[[:space:]]*`?[A-Za-z0-9_]+`?[[:space:]]*\|[[:space:]]*[0-9]+[[:space:]]*\|' \
        "$QUALITY_STATUS_FILE" | grep -oE '[0-9]+' | sort -nr | head -n 1)"
fi

# Criterio Gherkin: criterios de aceptacion de las historias de usuario.
# Se aceptan:
#   - archivos .feature con "Feature:"
#   - documentos .md con escenarios ("Scenario:") que ademas mencionen
#     Gherkin o "criterios de aceptacion" (p.ej. Hus-refinadas-sprint2.md)
find_gherkin_file() {
    find "$REPO_ROOT" -path "$REPO_ROOT/target" -prune -o \
        -path "$REPO_ROOT/.git" -prune -o \
        -type f \( -name '*.feature' -o -name '*.md' \) -print 2>/dev/null \
        | while IFS= read -r file; do
            if [[ "$file" == *.feature ]]; then
                if grep -qE '^\s*Feature:' "$file" 2>/dev/null; then
                    echo "${file#"$REPO_ROOT"/}"
                    break
                fi
            elif grep -qE '^\s*Scenario:' "$file" 2>/dev/null \
                 && grep -qiE 'gherkin|criterios de aceptaci' "$file" 2>/dev/null; then
                echo "${file#"$REPO_ROOT"/}"
                break
            fi
        done | head -n 1
}

has_gherkin() {
    [[ -n "$(find_gherkin_file)" ]]
}

# Criterio Pipeline: workflow con SonarCloud + token.
has_quality_pipeline() {
    local wf="$REPO_ROOT/.github/workflows/ci.yml"
    [[ -f "$wf" ]] \
        && grep -qi 'sonar-maven-plugin:sonar' "$wf" \
        && grep -qi 'SONAR_TOKEN' "$wf"
}

# =============================================================================
# Calculo de estados
# =============================================================================
API_OK=1
if [[ -z "$MEASURES" || "$MEASURES" == "null" ]] || jq -e 'has("errors")' >/dev/null 2>&1 <<<"$MEASURES"; then
    API_OK=0
fi
if [[ -z "$QG" || "$QG" == "null" ]] || jq -e 'has("errors")' >/dev/null 2>&1 <<<"$QG"; then
    API_OK=0
fi

# Cobertura global
if [[ "$API_OK" -eq 1 && "$COVERAGE" != "N/A" ]]; then
    COV_CUMPLE="$(awk "BEGIN{exit !($COVERAGE >= $THRESHOLD_COVERAGE)}" && echo "CUMPLE" || echo "NO CUMPLE")"
else
    COV_CUMPLE="N/A"
fi

# Cobertura de codigo nuevo (Quality Gate de SonarCloud)
if [[ "$API_OK" -eq 1 && "$NEW_COVERAGE" =~ ^[0-9]+(\.[0-9]+)?$ ]]; then
    NEWC_CUMPLE="$(awk "BEGIN{exit !($NEW_COVERAGE >= $THRESHOLD_NEW_COVERAGE)}" && echo "CUMPLE" || echo "NO CUMPLE")"
else
    NEWC_CUMPLE="N/A"
fi

# Deuda tecnica
if [[ "$DEBT" =~ ^[0-9]+$ ]]; then
    DEBT_CUMPLE="$([[ "$DEBT" -le "$THRESHOLD_DEBT_MIN" ]] && echo "CUMPLE" || echo "NO CUMPLE")"
    DEBT_DIAS="$(awk "BEGIN{printf \"%.2f\", $DEBT/480}")"
else
    DEBT_CUMPLE="N/A"
    DEBT_DIAS="N/A"
fi

# Complejidad
if [[ "$MAX_COMPLEXITY" =~ ^[0-9]+$ ]]; then
    CPLX_CUMPLE="$([[ "$MAX_COMPLEXITY" -lt "$THRESHOLD_COMPLEXITY" ]] && echo "CUMPLE" || echo "NO CUMPLE")"
else
    CPLX_CUMPLE="N/A"
fi

# Hallazgos criticos abiertos (bugs + vulnerabilidades).
# Se usan los issues abiertos reales: la metrica new_critical_violations solo
# cubre codigo nuevo y puede quedar desactualizada.
if [[ "$OPEN_CRITICAL" -eq 0 ]]; then
    VULN_CUMPLE="CUMPLE"
else
    VULN_CUMPLE="NO CUMPLE"
fi

# Quality Gate
if [[ "$QG_STATUS" == "OK" ]]; then
    QG_CUMPLE="CUMPLE"
elif [[ "$QG_STATUS" == "UNKNOWN" ]]; then
    QG_CUMPLE="N/A"
else
    QG_CUMPLE="NO CUMPLE"
fi

# Gherkin
GHERKIN_FILE="$(find_gherkin_file)"
if [[ -n "$GHERKIN_FILE" ]]; then
    GHERKIN_CUMPLE="CUMPLE"
    GHERKIN_SCENARIOS="$(grep -cE '^\s*Scenario:' "$REPO_ROOT/$GHERKIN_FILE" 2>/dev/null || echo 0)"
else
    GHERKIN_CUMPLE="NO CUMPLE"
    GHERKIN_SCENARIOS=0
fi

# Pipeline
PIPELINE_CUMPLE="$(has_quality_pipeline && echo "CUMPLE" || echo "N/A")"

# =============================================================================
# Encabezado
# =============================================================================
printf '\n'
hr_d
printf ' %s%s%s %s·%s %s\n' "$C_BOLD" "SONARCLOUD" "$C_RESET" "$C_GRAY_DARK" "$C_RESET" "$C_CYAN${PROJECT_KEY}${C_RESET}"
hr_d
printf ' %s%-18s%s %s\n' "$C_GRAY" "Rama actual"    "$C_RESET" "$CURRENT_BRANCH"
printf ' %s%-18s%s %s%s%s\n' "$C_GRAY" "Rama analizada" "$C_RESET" "$C_BOLD$C_CYAN" "$BRANCH" "$C_RESET"
printf ' %s%-18s%s %s\n' "$C_GRAY" "Acceso API"      "$C_RESET" "$TOKEN_LABEL"
printf ' %s%-18s%s ' "$C_GRAY" "Quality Gate"    "$C_RESET"
if [[ "$QG_STATUS" == "OK" ]]; then
    badge "✓ OK" "$C_BG_GREEN" "$C_BOLD"
elif [[ "$QG_STATUS" == "UNKNOWN" ]]; then
    badge "— DESCONOCIDO" "$C_BG_GRAY" "$C_BOLD"
else
    badge "✗ $QG_STATUS" "$C_BG_RED" "$C_BOLD"
fi
printf '\n'

if [[ "$API_OK" -eq 0 ]]; then
    printf ' %s%s⚠  No se pudo consultar la API de SonarCloud.%s\n' "$C_BOLD" "$C_YELLOW" "$C_RESET"
    printf ' %s   Revisa la conexion, el project key y el token (SONAR_TOKEN).%s\n' "$C_GRAY" "$C_RESET"
    printf ' %s   Project key: %s%s\n\n' "$C_GRAY" "$PROJECT_KEY" "$C_RESET"
fi

# =============================================================================
# Metricas principales
# =============================================================================
seccion "  MÉTRICAS PRINCIPALES"
hr

printf '  %s%sCobertura%s\n' "$C_BOLD" "$C_BLUE" "$C_RESET"
fila_metrica "Global"       "$(pct "$COVERAGE")"        "$(color_por_estado "${COVERAGE:-0}" "$THRESHOLD_COVERAGE" ge)" "$THRESHOLD_COVERAGE"
fila_metrica "Líneas"       "$(pct "$LINE_COVERAGE")"   "$C_GRAY"
fila_metrica "Ramas"        "$(pct "$BRANCH_COVERAGE")" "$C_GRAY"
fila_metrica "Código nuevo" "$(pct "$NEW_COVERAGE")"    "$(color_por_estado "${NEW_COVERAGE:-0}" "$THRESHOLD_NEW_COVERAGE" ge)" "$THRESHOLD_NEW_COVERAGE"
printf '  %s%sDeuda técnica%s\n' "$C_BOLD" "$C_BLUE" "$C_RESET"
if [[ "$DEBT" =~ ^[0-9]+$ ]]; then
    fila_metrica "Total"        "${DEBT} min (${DEBT_DIAS} d)" "$(color_por_estado "$DEBT" "$THRESHOLD_DEBT_MIN" le)" "$THRESHOLD_DEBT_MIN"
else
    fila_metrica "Total"        "${DEBT:-N/A} min" "$C_GRAY"
fi
fila_metrica "Código nuevo" "${NEW_DEBT:-N/A} min" "$C_GRAY"

printf '  %s%sCalidad del código%s\n' "$C_BOLD" "$C_BLUE" "$C_RESET"
fila_metrica "Complejidad máx./clase" "${MAX_COMPLEXITY}" "$(color_por_estado "${MAX_COMPLEXITY:-0}" "$THRESHOLD_COMPLEXITY" lt)" "$THRESHOLD_COMPLEXITY"
fila_metrica "Complejidad total"     "${COMPLEXITY:-N/A}" "$C_GRAY"
fila_metrica "Complejidad cognitiva" "${COGNITIVE:-N/A}" "$C_GRAY"
fila_metrica "Líneas duplicadas"     "$(pct "$DUPLICATION")" "$C_GRAY"
fila_metrica "Violations"            "${VIOLATIONS:-N/A}" "$C_GRAY"

printf '  %s%sSeguridad%s\n' "$C_BOLD" "$C_BLUE" "$C_RESET"
fila_metrica "Bugs"              "${BUGS:-N/A}" "$C_GRAY"
fila_metrica "Vulnerabilidades"  "${VULNERABILITIES:-N/A}" "$C_GRAY"
fila_metrica "Críticas nuevas"   "${CRITICAL:-N/A}" "$([[ "$CRITICAL" == "0" ]] && printf '%s' "$C_GREEN" || printf '%s' "$C_RED")"
fila_metrica "Security hotspots" "${HOTSPOTS:-N/A}" "$C_GRAY"
fila_metrica "Rating conf./seg." "$(rating_letra "$RELIABILITY") / $(rating_letra "$SECURITY")" "$C_GRAY"
printf '  %s%sTamaño%s\n' "$C_BOLD" "$C_BLUE" "$C_RESET"
fila_metrica "Líneas de código" "${NC_LOC:-N/A}" "$C_GRAY"
fila_metrica "Archivos"         "${FILES:-N/A}" "$C_GRAY"

# =============================================================================
# Criterios de calidad
# =============================================================================
seccion "  CRITERIOS DE CALIDAD"
hr

criterio() {
    local dim="$1" crit="$2" obl="$3" estado="$4" nota="${5:-}"
    printf '  %s%-24s%s  %s%-31s%s  %s%-6s%s  ' \
        "$C_BOLD" "$dim" "$C_RESET" \
        "$C_GRAY" "$crit" "$C_RESET" \
        "$C_MAGENTA" "$obl" "$C_RESET"
    status_badge "$estado"
    if [[ -n "$nota" ]]; then
        printf '   %s%s%s' "$C_GRAY_DARK" "$nota" "$C_RESET"
    fi
    printf '\n'
}

criterio "Análisis estático"      "SonarCloud + Quality Gate"   "Debe" "$QG_CUMPLE"        "$QG_STATUS"
criterio "Cobertura unitaria"     ">= ${THRESHOLD_COVERAGE}%"                    "Debe" "$COV_CUMPLE"        "$(pct "$COVERAGE")"
criterio "Cobertura código nuevo" ">= ${THRESHOLD_NEW_COVERAGE}%"                 "Debe" "$NEWC_CUMPLE"       "$(pct "$NEW_COVERAGE")"
criterio "Deuda técnica"         "<= 2 días (${THRESHOLD_DEBT_MIN} min)"  "Debe" "$DEBT_CUMPLE"       "${DEBT_DIAS} d"
criterio "Complejidad ciclomática" "< ${THRESHOLD_COMPLEXITY} por clase"           "Debe" "$CPLX_CUMPLE"       "max ${MAX_COMPLEXITY}"
criterio "Hallazgos críticos"    "Cero bugs/vulns críticos"    "Debe" "$VULN_CUMPLE"      "${OPEN_CRITICAL} abiertos"
criterio "Historias de usuario"   "Criterios en Gherkin"          "Debe" "$GHERKIN_CUMPLE" \
    "$([[ -n "$GHERKIN_FILE" ]] && printf '%s (%s escenarios)' "$GHERKIN_FILE" "$GHERKIN_SCENARIOS")"
criterio "Pipeline (Calidad)"     "SonarCloud + cobertura"        "Debe" "$PIPELINE_CUMPLE"

# =============================================================================
# Resumen
# =============================================================================
TOTAL=8
PASA=0
for e in "$QG_CUMPLE" "$COV_CUMPLE" "$NEWC_CUMPLE" "$DEBT_CUMPLE" "$CPLX_CUMPLE" "$VULN_CUMPLE" "$GHERKIN_CUMPLE" "$PIPELINE_CUMPLE"; do
    [[ "$e" == "CUMPLE" ]] && PASA=$((PASA + 1))
done
FALLA=$((TOTAL - PASA))

printf '\n'
hr
if [[ "$FALLA" -eq 0 ]]; then
    printf ' %s%s✓  %d/%d criterios cumplidos — TODO EN VERDE%s\n' \
        "$C_BOLD" "$C_BG_GREEN" "$PASA" "$TOTAL" "$C_RESET"
else
    printf ' %s%s✗  %d/%d criterios cumplidos — %d en rojo%s\n' \
        "$C_BOLD" "$C_BG_RED" "$PASA" "$TOTAL" "$FALLA" "$C_RESET"
fi
hr

# =============================================================================
# Hallazgos detallados: BUGS y VULNERABILIDADES
# =============================================================================
# SonarCloud clasifica los hallazgos en tipos distintos:
#   - BUG           -> errores en el codigo (incluye los CRITICAL de java:S9352)
#   - VULNERABILITY -> fallas de seguridad explotables
# Ambos cuentan para el criterio "cero criticas", por eso se listan juntos.
seccion "  HALLAZGOS DETALLADOS"
hr

imprimir_hallazgos() {
    local etiqueta="$1" json="$2" total
    total="$(jq -r '.total // 0' <<<"$json" 2>/dev/null || echo 0)"

    printf ' %s%s%s\n' "$C_BOLD" "$etiqueta" "$C_RESET"
    if [[ "$total" == "0" || -z "$total" ]]; then
        printf '   %s✓ Ninguno.%s\n' "$C_GREEN" "$C_RESET"
        return
    fi
    printf '   %s%s abierto(s)%s\n' "$C_YELLOW" "$total" "$C_RESET"

    while IFS=$'\t' read -r sev rule path msg meta; do
        [[ -z "$sev" ]] && continue
        case "$sev" in
            CRITICAL|BLOCKER) color="$C_RED" ;;
            MAJOR)            color="$C_YELLOW" ;;
            MINOR)            color="$C_CYAN" ;;
            *)                color="$C_GRAY" ;;
        esac
        printf '   %s%-9s%s %s%s%s\n' "$color" "$sev" "$C_RESET" "$C_BOLD" "$rule" "$C_RESET"
        printf '     %s%s%s\n' "$C_GRAY" "$path" "$C_RESET"
        printf '     %s%s%s\n' "$C_GRAY" "$msg" "$C_RESET"
        printf '     %s%s%s\n' "$C_GRAY_DARK" "$meta" "$C_RESET"
        printf '\n'
    done < <(jq -r '.issues[]? |
        [ (.severity // "N/A" | ascii_upcase),
          (.rule // "N/A"),
          (.component // "N/A" | sub("^[^:]*:"; "")),
          (.message // "N/A"),
          ("Creado: " + (.creationDate // "N/A") + "  |  Estado: " + (.status // "N/A") + "  |  Tipo: " + (.type // "N/A"))
        ] | @tsv' <<<"$json" 2>/dev/null)
}

imprimir_hallazgos "BUGS (errores de codigo)" "$BUGS_JSON"
imprimir_hallazgos "VULNERABILIDADES (seguridad)" "$VULNS_JSON"

printf '\n'
hr_d
printf '\n'

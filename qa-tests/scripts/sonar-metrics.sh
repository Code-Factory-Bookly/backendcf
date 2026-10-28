#!/usr/bin/env bash
#
# sonar-metrics.sh
#
# Muestra las metricas principales de SonarCloud y verifica los criterios
# de calidad obligatorios del proyecto.
#
# Uso:
#   ./qa-tests/scripts/sonar-metrics.sh              # rama actual
#   ./qa-tests/scripts/sonar-metrics.sh main         # rama especifica
#
# El script puede ejecutarse desde cualquier directorio: resuelve la raiz
# del repositorio git de forma automatica.
#
# Dependencias: curl, jq y git.

set -uo pipefail

PROJECT_KEY="${SONAR_PROJECT_KEY:-Code-Factory-Bookly_backendcf}"
API="https://sonarcloud.io/api"

# Resolver la raiz del repositorio git (subiendo desde la ubicacion del script).
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel 2>/dev/null || echo "$SCRIPT_DIR/../..")"
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

api() {
    curl -sS "${AUTH[@]}" "$API$1"
}

QG="$(api "/qualitygates/project_status?projectKey=${PROJECT_KEY}&branch=${BRANCH}")"
MEASURES="$(api "/measures/component?component=${PROJECT_KEY}&branch=${BRANCH}&metricKeys=coverage,line_coverage,branch_coverage,sqale_index,new_coverage,new_technical_debt,complexity,cognitive_complexity,violations,bugs,vulnerabilities,security_hotspots,duplicated_lines_density,ncloc,files,reliability_rating,security_rating,new_critical_violations,new_vulnerabilities")"

metric() {
    jq -r --arg m "$1" '
        .component.measures[]?
        | select(.metric == $m)
        | (.value // (.periods[]?.value) // "N/A")
    ' <<<"$MEASURES" | head -n 1
}

QG_STATUS="$(jq -r '.projectStatus.status // "UNKNOWN"' <<<"$QG")"
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

# Criterio Gherkin: buscar dentro del repositorio.
has_gherkin() {
    find "$REPO_ROOT" -path "$REPO_ROOT/target" -prune -o \
        -path "$REPO_ROOT/.git" -prune -o \
        -type f \( -name 'HU_Reservas_Empresa_Unica.md' -o -name '*.feature' \) -print 2>/dev/null \
        | grep -q .
}

# Criterio Pipeline: workflow con SonarCloud + token.
has_quality_pipeline() {
    local wf="$REPO_ROOT/.github/workflows/ci.yml"
    [[ -f "$wf" ]] \
        && grep -qi 'sonar-maven-plugin:sonar' "$wf" \
        && grep -qi 'SONAR_TOKEN' "$wf"
}

echo
echo "=============================================================================="
echo " PROYECTO:       ${PROJECT_KEY}"
echo " RAMA ACTUAL:    ${CURRENT_BRANCH}"
echo " RAMA ANALIZADA: ${BRANCH}"
echo "=============================================================================="
echo
echo " METRICAS PRINCIPALES"
echo "------------------------------------------------------------------------------"
printf ' %-34s %s\n' "Quality Gate" "${QG_STATUS}"
printf ' %-34s %s\n' "Cobertura global" "${COVERAGE:-N/A}%"
printf ' %-34s %s\n' "Cobertura de lineas" "${LINE_COVERAGE:-N/A}%"
printf ' %-34s %s\n' "Cobertura de ramas" "${BRANCH_COVERAGE:-N/A}%"
printf ' %-34s %s\n' "Cobertura de codigo nuevo" "$(awk "BEGIN {printf \"%.1f\", ${NEW_COVERAGE:-0}}")%"
printf ' %-34s %s\n' "Deuda tecnica" "${DEBT:-N/A} min"
printf ' %-34s %s\n' "Deuda tecnica codigo nuevo" "${NEW_DEBT:-N/A} min"
printf ' %-34s %s\n' "Complejidad max/clase" "${MAX_COMPLEXITY}"
printf ' %-34s %s\n' "Complejidad cognitiva" "${COGNITIVE:-N/A}"
printf ' %-34s %s\n' "Problemas (violations)" "${VIOLATIONS:-N/A}"
printf ' %-34s %s\n' "Bugs" "${BUGS:-N/A}"
printf ' %-34s %s\n' "Vulnerabilidades" "${VULNERABILITIES:-N/A}"
printf ' %-34s %s\n' "Vulnerabilidades nuevas" "${NEW_VULNS:-N/A}"
printf ' %-34s %s\n' "Criticas nuevas" "${CRITICAL:-N/A}"
printf ' %-34s %s\n' "Security hotspots" "${HOTSPOTS:-N/A}"
printf ' %-34s %s\n' "Lineas duplicadas" "${DUPLICATION:-N/A}%"
printf ' %-34s %s\n' "Rating confiabilidad / seguridad" "${RELIABILITY:-N/A} / ${SECURITY:-N/A}"
printf ' %-34s %s\n' "Lineas de codigo / archivos" "${NC_LOC:-N/A} / ${FILES:-N/A}"

echo
echo " CRITERIOS DE CALIDAD"
echo "------------------------------------------------------------------------------"
printf '| %-24s | %-34s | %-10s | %s |\n' \
    "Dimension" "Criterio minimo" "Obligatoriedad" "Estado"
printf '|%s|%s|%s|%s|\n' \
    "------------------------" \
    "----------------------------------" \
    "------------" \
    "------------"
printf '| %-24s | %-34s | %-10s | %s |\n' \
    "Analisis estatico" "SonarCloud + Quality Gate" "Debe" \
    "$([[ "$QG_STATUS" == "OK" ]] && echo "CUMPLE" || echo "NO CUMPLE")"
printf '| %-24s | %-34s | %-10s | %s |\n' \
    "Cobertura unitaria" ">= 65%" "Debe" \
    "$(awk "BEGIN {exit !(${COVERAGE:-0} >= 65)}" && echo "CUMPLE" || echo "NO CUMPLE")"
printf '| %-24s | %-34s | %-10s | %s |\n' \
    "Deuda tecnica" "<= 2 dias (960 min)" "Debe" \
    "$([[ "$DEBT" =~ ^[0-9]+$ ]] && [[ "$DEBT" -le 960 ]] && echo "CUMPLE" || echo "NO CUMPLE")"
printf '| %-24s | %-34s | %-10s | %s |\n' \
    "Complejidad ciclomatica" "< 50 por clase" "Debe" \
    "$([[ "$MAX_COMPLEXITY" =~ ^[0-9]+$ ]] && [[ "$MAX_COMPLEXITY" -lt 50 ]] && echo "CUMPLE" || echo "NO CUMPLE")"
printf '| %-24s | %-34s | %-10s | %s |\n' \
    "Vulnerabilidades" "Cero criticas" "Debe" \
    "$([[ "$CRITICAL" =~ ^[0-9]+$ ]] && [[ "$CRITICAL" -eq 0 ]] && echo "CUMPLE" || echo "NO CUMPLE")"
printf '| %-24s | %-34s | %-10s | %s |\n' \
    "Historias de usuario" "Criterios en Gherkin" "Debe" \
    "$(has_gherkin && echo "CUMPLE" || echo "NO CUMPLE")"
printf '| %-24s | %-34s | %-10s | %s |\n' \
    "Pipeline (Calidad)" "SonarCloud + cobertura" "Debe" \
    "$(has_quality_pipeline && echo "CUMPLE" || echo "NO CUMPLE")"

echo
echo "=============================================================================="

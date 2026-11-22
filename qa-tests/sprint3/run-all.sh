#!/usr/bin/env bash
# =============================================================================
# run-all.sh - Ejecuta todos los tests E2E de Sprint 3
# =============================================================================
# Cada test se ejecuta contra una base limpia (reset.sh + seed.sh) para que los
# casos no se contaminen entre si.
# =============================================================================
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

SUMMARY_DIR="$RESULTS_DIR/summary"
mkdir -p "$SUMMARY_DIR"
SUMMARY="$SUMMARY_DIR/all.txt"
: > "$SUMMARY"

TESTS=(
    "test_hu05_blocks.sh"
    "test_hu13_history.sh"
    "test_hu15_occupancy.sh"
    "test_hu05_13_15_integration.sh"
)

GRAND_PASS=0
GRAND_FAIL=0
GRAND_BUG=0

for t in "${TESTS[@]}"; do
    echo ""
    echo "================================================="
    echo " EJECUTANDO: $t"
    echo "================================================="

    # Reset de la base de datos
    bash "$SCRIPT_DIR/reset.sh" > "$SUMMARY_DIR/${t%.sh}.reset.log" 2>&1
    RESET_CODE=$?
    if [[ "$RESET_CODE" -ne 0 ]]; then
        echo "  ERROR: reset.sh fallo (codigo $RESET_CODE). Ver ${t%.sh}.reset.log"
        tail -20 "$SUMMARY_DIR/${t%.sh}.reset.log"
        echo "  -> RESET FALLIDO, se omite el test" | tee -a "$SUMMARY"
        continue
    fi

    # Seed
    bash "$SCRIPT_DIR/seed.sh" > "$SUMMARY_DIR/${t%.sh}.seed.log" 2>&1

    # Ejecutar el test
    OUT="$SUMMARY_DIR/${t%.sh}.log"
    bash "$SCRIPT_DIR/$t" > "$OUT" 2>&1
    EXIT_CODE=$?

    # Se cuentan solo las lineas de resultado (las que empiezan con el marcador
    # de color), no las lineas del resumen ("PASS: n", "FAIL: n", ...).
    # Las lineas de resultado llevan el prefijo [HU-05], [HU-13], [HU-15] o
    # [INTEGRACION] justo despues del marcador. El resumen ("PASS: n") no lo
    # lleva, asi que no se cuenta.
    T_PASS=$(grep -cE "^[^:]*PASS[^:]*\[(HU-05|HU-13|HU-15|INTEGRACION)\]" "$OUT" 2>/dev/null || true)
    T_FAIL=$(grep -cE "^[^:]*FAIL[^:]*\[(HU-05|HU-13|HU-15|INTEGRACION)\]" "$OUT" 2>/dev/null || true)
    T_BUG=$(grep -cE "^[^:]*BUG[^:]*\[(HU-05|HU-13|HU-15|INTEGRACION)\]" "$OUT" 2>/dev/null || true)
    : "${T_PASS:=0}"; : "${T_FAIL:=0}"; : "${T_BUG:=0}"
    GRAND_PASS=$((GRAND_PASS + T_PASS))
    GRAND_FAIL=$((GRAND_FAIL + T_FAIL))
    GRAND_BUG=$((GRAND_BUG + T_BUG))
    echo "  -> PASS=$T_PASS FAIL=$T_FAIL BUG=$T_BUG (exit=$EXIT_CODE)" | tee -a "$SUMMARY"

    tail -25 "$OUT"
    echo ""
done

echo ""
echo "================================================="
echo " RESUMEN FINAL - SPRINT 3"
echo "================================================="
{
    echo "TOTAL PASS: $GRAND_PASS"
    echo "TOTAL FAIL: $GRAND_FAIL"
    echo "TOTAL BUGS: $GRAND_BUG"
    echo "TOTAL CASOS: $((GRAND_PASS + GRAND_FAIL))"
    echo ""
    echo "--- BUGS DETECTADOS POR TEST ---"
} | tee -a "$SUMMARY"

for t in "${TESTS[@]}"; do
    OUT="$SUMMARY_DIR/${t%.sh}.log"
    if [[ -f "$OUT" ]]; then
        BUGS_IN=$(grep -E "BUG[^:]*\[(HU-05|HU-13|HU-15|INTEGRACION)\]" "$OUT" | sed -E 's/^.*BUG[^A-Za-z0-9]*//' || true)
        if [[ -n "$BUGS_IN" ]]; then
            echo "" | tee -a "$SUMMARY"
            echo "### $t" | tee -a "$SUMMARY"
            echo "$BUGS_IN" | tee -a "$SUMMARY"
        fi
    fi
done

echo ""
echo "Log consolidado: $SUMMARY"

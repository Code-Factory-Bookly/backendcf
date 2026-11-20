#!/usr/bin/env bash
# =============================================================================
# md2pdf.sh - Convierte un informe Markdown a PDF con Chrome headless.
#
# Uso: ./md2pdf.sh <archivo.md> <archivo.pdf> "<titulo>"
#
# Formato: Letter, Times New Roman, margenes 1.25in, cada H2 en pagina nueva.
# =============================================================================
set -eu

MD="$1"
PDF="$2"
TITLE="$3"

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

HTML="$WORK/report.html"

# ---------------------------------------------------------------------------
# CSS: replica el formato del PDF de referencia (Sprint 2).
# ---------------------------------------------------------------------------
cat > "$WORK/report.css" <<'CSS'
@page {
  size: Letter;
  margin: 1.25in;
}

html {
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

body {
  font-family: "Times New Roman", Times, serif;
  font-size: 11pt;
  line-height: 1.23;
  color: #000;
  margin: 0;
  padding: 0;
}

h1 {
  font-size: 18pt;
  margin: 0 0 14pt 0;
  page-break-after: avoid;
}

h2 {
  font-size: 15pt;
  margin: 0 0 10pt 0;
  break-before: page;
  page-break-after: avoid;
}

h3 {
  font-size: 12.5pt;
  margin: 12pt 0 5pt 0;
  page-break-after: avoid;
}

h4 {
  font-size: 11.5pt;
  font-style: italic;
  margin: 9pt 0 4pt 0;
  page-break-after: avoid;
}

p {
  margin: 0 0 7pt 0;
  text-align: justify;
}

strong {
  font-weight: bold;
}

em {
  font-style: italic;
}

code {
  font-family: "Noto Sans Mono", "DejaVu Sans Mono", monospace;
  font-size: 9pt;
}

pre {
  font-family: "Noto Sans Mono", "DejaVu Sans Mono", monospace;
  font-size: 9pt;
  background: #f4f4f4;
  border: 1px solid #ddd;
  border-radius: 3pt;
  padding: 6pt 8pt;
  margin: 0 0 9pt 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  page-break-inside: avoid;
}

pre code {
  font-size: 9pt;
  background: none;
  border: none;
  padding: 0;
}

ul, ol {
  margin: 0 0 8pt 0;
  padding-left: 22pt;
}

li {
  margin-bottom: 2.5pt;
}

blockquote {
  margin: 0 0 10pt 0;
  padding: 4pt 12pt;
  border-left: 3pt solid #999;
  color: #333;
  font-style: italic;
}

blockquote p {
  margin: 0;
  text-align: left;
}

table {
  border-collapse: collapse;
  width: auto;
  font-size: 10pt;
  margin: 0 0 10pt 0;
  page-break-inside: avoid;
}

th, td {
  border: 1px solid #666;
  padding: 6px 12px;
  text-align: left;
  vertical-align: top;
}

th {
  background: #f0f0f0;
  font-weight: bold;
  text-align: center;
}

hr {
  border: 0;
  border-top: 1px solid #ccc;
  margin: 14px 0;
}
CSS

# ---------------------------------------------------------------------------
# Markdown -> HTML. Se corta el primer H1 para evitar el titulo duplicado
# (Chrome ya imprime el <title> del documento en la parte superior).
# ---------------------------------------------------------------------------
{
  printf '<!DOCTYPE html>\n<html lang="es">\n<head>\n<meta charset="utf-8">\n'
  printf '<title>%s</title>\n' "$TITLE"
  printf '<style>\n'
  cat "$WORK/report.css"
  printf '</style>\n</head>\n<body>\n'
  pandoc -f markdown -t html5 --metadata title="$TITLE" "$MD"
  printf '\n</body>\n</html>\n'
} > "$HTML"

# ---------------------------------------------------------------------------
# Markdown -> PDF con Chrome headless (mismo productor Skia/PDF que el
# PDF de referencia del Sprint 2).
# ---------------------------------------------------------------------------
CHROME=""
for c in chromium chromium-browser google-chrome google-chrome-stable; do
  if command -v "$c" >/dev/null 2>&1; then CHROME="$c"; break; fi
done

if [[ -z "$CHROME" ]]; then
  echo "ERROR: no se encontro Chrome/Chromium para generar el PDF." >&2
  exit 1
fi

"$CHROME" \
  --headless \
  --no-sandbox \
  --disable-gpu \
  --no-pdf-header-footer \
  --disable-pdf-tagging \
  --print-to-pdf="$PDF" \
  "file://$HTML" 2>/dev/null

if [[ ! -f "$PDF" ]]; then
  echo "ERROR: Chrome no genero el PDF en $PDF" >&2
  exit 1
fi

echo "PDF generado: $PDF"

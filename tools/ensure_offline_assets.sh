#!/usr/bin/env bash
# Used by BOTH GitHub Actions and Gradle. Works if a repository kept an older
# .github workflow that invokes Gradle directly without generating assets.
set -euo pipefail
cd "$(dirname "$0")/.."

if python3 tools/verify_offline_assets.py >/dev/null 2>&1; then
  echo 'ManaVault: existing offline printing and fingerprint assets verified; skipping download.'
  exit 0
fi

echo 'ManaVault: missing/incomplete generated offline assets. Preparing them before APK compilation.'
python3 tools/verify_offline_assets.py || true

# Installing in a private environment avoids Ubuntu system-Python restrictions.
VENV="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/manavault-assets-venv"
if [ ! -x "$VENV/bin/python" ]; then
  python3 -m venv "$VENV"
fi
"$VENV/bin/python" -m pip install --disable-pip-version-check --quiet requests pillow ijson cairosvg

# A missing catalog must be built first; the visual generators read it.
if [ ! -s app/src/main/assets/printing-catalog.dat ]; then
  echo 'ManaVault: downloading/building Scryfall SQLite catalog...'
  timeout 900 "$VENV/bin/python" tools/build_native_catalog.py
fi
# If an incomplete file exists, regenerate it rather than falsely treating it
# as ready; the builder swaps the output atomically after successful validation.
if ! "$VENV/bin/python" - <<'PY'
import gzip,sqlite3,tempfile
from pathlib import Path
p=Path('app/src/main/assets/printing-catalog.dat')
try:
 with tempfile.TemporaryDirectory() as folder:
  target=Path(folder)/'catalog.sqlite'
  with gzip.open(p,'rb') as src,target.open('wb') as dst:
   while data:=src.read(1048576):dst.write(data)
  db=sqlite3.connect(target)
  try:
   assert db.execute('SELECT COUNT(*) FROM printings').fetchone()[0]>=100000
   assert db.execute('SELECT COUNT(*) FROM reference_fts').fetchone()[0]>=100000
  finally:
   db.close()
except (OSError,AssertionError,sqlite3.DatabaseError,EOFError):raise SystemExit(1)
PY
then
  echo 'ManaVault: rebuilding incomplete SQLite catalog...'
  timeout 900 "$VENV/bin/python" tools/build_native_catalog.py
fi

if ! "$VENV/bin/python" - <<'PY'
import json
from pathlib import Path
try:
 p=json.loads(Path('app/src/main/assets/visual-index.json').read_text())
 assert p['format']=='manavault-visual-v2' and len(p['cards'])>=12
except (OSError,ValueError,KeyError,AssertionError):raise SystemExit(1)
PY
then
  echo 'ManaVault: generating offline artwork fingerprints...'
  timeout 600 "$VENV/bin/python" tools/build_artwork_index.py --max 450
fi

if ! "$VENV/bin/python" - <<'PY'
import json
from pathlib import Path
try:
 p=json.loads(Path('app/src/main/assets/symbol-index.json').read_text())
 assert p['format']=='manavault-symbol-v3' and len(p['sets'])>=12
 assert len(p['sets']) >= p['coverage']['icon_eligible']*.85
except (OSError,ValueError,KeyError,AssertionError):raise SystemExit(1)
PY
then
  echo 'ManaVault: generating complete Scryfall set-icon silhouettes (SVG)...'
  timeout 600 "$VENV/bin/python" tools/build_symbol_index.py
fi

"$VENV/bin/python" tools/verify_offline_assets.py

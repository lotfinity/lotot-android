#!/usr/bin/env python3
"""Check reviewed generic translations against their exact bundled source."""
from pathlib import Path
import json, sqlite3, re
ROOT=Path(__file__).resolve().parents[1]
catalog=json.loads((ROOT/'androbd/src/main/assets/dtc_translations.json').read_text())
assert catalog['schema_version']==1 and catalog['scope']=='GENERIC'
sources=catalog['source_en']
assert set(catalog['translations'])=={'fr','ar-DZ'}
db=sqlite3.connect(ROOT/'androbd/src/main/assets/dtc_codes.db')
for code,original in sources.items():
    assert re.fullmatch(r'[PBCU][0-9A-F]{4}',code),code
    row=db.execute("SELECT description FROM dtc_definitions WHERE code=? AND manufacturer='GENERIC' AND locale='en'",(code,)).fetchone()
    assert row and row[0]==original,code
for locale,rows in catalog['translations'].items():
    assert rows.keys()==sources.keys(),locale
    assert all(isinstance(v,str) and v.strip() for v in rows.values()),locale
    print(f'{locale}: {len(rows)} reviewed generic definitions; English database preserved')
print('Remaining definitions use explicit English fallback; coverage is not claimed for the entire database.')

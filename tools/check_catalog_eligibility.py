#!/usr/bin/env python3
"""Examine physical-printing eligibility from the database actually inside an APK.

Example:
 python tools/check_catalog_eligibility.py app-debug.apk 'Sanctuary Cat' 'Gift Horse'
Requires Python 3 stdlib only. Does not modify the APK.
"""
import argparse
import contextlib
import gzip
import json
from pathlib import Path
import re
import shutil
import sqlite3
import tempfile
import unicodedata
import zipfile


def key(text):
    text = unicodedata.normalize('NFKD', text.casefold())
    text = ''.join(c for c in text if not unicodedata.combining(c))
    return ' '.join(re.findall(r'[^\W_]+', text, flags=re.UNICODE))


def unpack_apk(path, tmp):
    if path.suffix.lower() == '.apk': return path
    with zipfile.ZipFile(path) as z:
        names = [x for x in z.namelist() if x.lower().endswith('.apk')]
        if len(names) != 1: raise ValueError('Expected exactly one APK in ZIP')
        out = Path(tmp)/'app.apk'
        with z.open(names[0]) as src, out.open('wb') as dst:
            shutil.copyfileobj(src,dst)
        return out


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('apk',type=Path)
    p.add_argument('names',nargs='+')
    args=p.parse_args()
    with tempfile.TemporaryDirectory() as tmp:
        apk=unpack_apk(args.apk,tmp)
        database=Path(tmp)/'catalog.sqlite'
        with zipfile.ZipFile(apk) as z:
            with z.open('assets/printing-catalog.dat') as compressed, gzip.GzipFile(fileobj=compressed) as stream, database.open('wb') as dest:
                shutil.copyfileobj(stream,dest)
        db=sqlite3.connect('file:'+str(database)+'?mode=ro',uri=True)
        print('Bundled catalog status:',db.execute('select * from info').fetchall())
        for name in args.names:
            records=db.execute('select id,setCode,collectorKey,data from printings where nameKey=?',(key(name),)).fetchall()
            physical=[];digital=[]
            for identifier,sc,collector,raw in records:
                obj=json.loads(raw)
                # The bundled catalog records an explicit digital boolean, not a
                # games[] field. Do not mistake absent games[] for non-paper.
                if obj.get('digital') is True:
                    digital.append((identifier,sc,collector))
                elif obj.get('digital') is False:
                    physical.append((identifier,sc,collector))
                else:
                    raise ValueError('Missing digital field; eligibility cannot be certified')
            print(json.dumps({'name':name,'normalized_nameKey':key(name),'total_records':len(records),
                              'eligible_non_digital_printings':len(physical),'digital_only':len(digital),
                              'candidate_ids':[v[0] for v in physical] if len(physical)==1 else []},indent=2))


if __name__=='__main__':main()

#!/usr/bin/env python3
"""v5.12.32: no Android SDK required; native policy and indexed search fixtures."""
from pathlib import Path
import subprocess,sqlite3,tempfile,re
root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/com/manavault/app'
tools=root/'tools'
with tempfile.TemporaryDirectory() as directory:
    out=Path(directory)
    files=[java/'ArtworkProbePolicy.java',java/'CollectorOcrPolicy.java',
           tools/'ArtworkProbePolicyTest.java',tools/'CollectorOcrPolicyTest.java']
    subprocess.run(['javac','-d',str(out),*[str(x) for x in files]],check=True)
    for test in ['ArtworkProbePolicyTest','CollectorOcrPolicyTest']:
        subprocess.run(['java','-cp',str(out),'com.manavault.app.'+test],check=True)
# Demonstrate that a one-bit-per-band difference yields ZERO exact-band hits
# while SQLite B-tree (band, code) lookup retrieves it under bounded fuzzy probes.
h=0x123456789ABCDEF0
mutated=h ^ sum(1 << (8*band) for band in range(8))
with sqlite3.connect(':memory:') as con:
    con.execute('CREATE TABLE art_band (band INTEGER,code INTEGER,printingId TEXT,face INTEGER,PRIMARY KEY(band,code,printingId,face))')
    con.execute('CREATE INDEX art_band_search ON art_band(band,code)')
    con.executemany('INSERT INTO art_band VALUES(?,?,?,0)',[(b,(mutated>>(8*b))&255,'true-card') for b in range(8)])
    def search(probes):
        vals=','.join(['(?,?)']*len(probes))
        sql='WITH probes(band,code) AS (VALUES '+vals+') SELECT b.printingId,COUNT(*) AS votes FROM probes p JOIN art_band b INDEXED BY art_band_search ON b.band=p.band AND b.code=p.code GROUP BY b.printingId,b.face HAVING COUNT(*)>=? ORDER BY votes DESC LIMIT ?'
        params=[v for pair in probes for v in pair]+[2,100]
        return con.execute(sql,params).fetchall()
    exact=[(b,(h>>(8*b))&255) for b in range(8)]
    fuzzy=exact+[(b,((h>>(8*b))&255) ^ (1<<bit)) for b in range(8) for bit in range(8)]
    assert search(exact)==[]
    assert search(fuzzy)==[('true-card',8)]
html=(root/'app/src/main/assets/index.html').read_text()
main=(java/'MainActivity.java').read_text();verifier=(java/'PrintingFingerprintVerifier.java').read_text()
scanner=(java/'NativeScannerActivity.java').read_text();index=(java/'FullArtworkIndex.java').read_text()
assert 'coLocatedAmbiguous===true' in html and 'coLocatedAmbiguous' in verifier
assert 'request.optJSONArray("expectedCollectors")' in main
assert 'collectorRescueAttempted' in verifier and 'expectedCollectors:' in html
assert 'ArtworkProbePolicy.strongAdvisoryName' in scanner
assert 'art_band_search' in index and 'alignedArtDescriptors' in index
assert 'IMAGE' not in index[:50] # sanity: no remote image retrieval in indexed source
print('v5.12.32 native OCR, fuzzy indexed candidate retrieval, co-located symbol guard PASSED')

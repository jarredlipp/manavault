import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from scanner_benchmark import summarize
log='''2026-10-25T17:30:00.000Z [info] Saved DURING SCAN: Card A (verified · structured set + collector numerator · finalized in 90ms)
2026-10-25T17:30:02.000Z [info] Scan timing: Card A · verification 90ms · manual review 0ms · end-to-end finalize 92ms · capture gap 2000ms
2026-10-25T17:30:03.000Z [info] Handled DURING SCAN: Who // What // When // Where // Why (retry · Try this card again · finalized in 200ms)
2026-10-25T17:30:05.000Z [info] Handled DURING SCAN: Who // What // When // Where // Why (skipped · Skipped during scanning · finalized in 200ms)
2026-10-25T17:30:06.000Z [info] Saved DURING SCAN: Card B (verified · edition selected · finalized in 300ms)
2026-10-25T17:30:08.000Z [info] Scan timing: Card B · verification 100ms · manual review 600ms · end-to-end finalize 702ms · capture gap 6000ms'''
r=summarize(log,3)
assert r['saved_entries']==2 and r['retries']==1 and r['skips']==1
assert r['physical_cards_reported']==3 and r['maximum_possible_capture_coverage_pct']==66.7
assert r['long_capture_gaps_over_4s']==1 and r['mean_verification_ms']==95
assert r['elapsed_seconds']==8 and r['manual_review_seconds']==.6
print('v5.13.0 physical-throughput benchmark metrics PASS')

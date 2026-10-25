#!/usr/bin/env python3
"""Measure the *real* ManaVault scanning session, not the inflated OCR capture rate.

This reads existing diagnostic-log text without requiring source screenshots,
network access, private card images, or Android SDK. `--physical` is user counted
physical cards, not deduced from logs: OCR retries are not physical cards.
"""
import argparse, json, re
from collections import Counter
from datetime import datetime
from pathlib import Path

STAMP=re.compile(r'^\s*(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d+Z)')
RESULT=re.compile(r'\b(Saved|Handled) DURING SCAN:\s*(.+?)\s*\((.*?)\)')
TIMING=re.compile(r'Scan timing:\s*(.+?)\s*·\s*verification\s+(\d+)ms\s*·\s*manual review\s+(\d+)ms\s*·\s*end-to-end finalize\s+(\d+)ms\s*·\s*capture gap\s+(\d+)ms')
WRONG=re.compile(r'Footer-first unable to establish set/# for (.*?):')

def summarize(log,physical=None):
    text=log if isinstance(log,str) else Path(log).read_text(encoding='utf-8',errors='replace')
    t0=t1=None;statuses=Counter();names=Counter();failures=Counter();manual_ms=0;verification_ms=0;gaps=[];samples=0
    for line in text.splitlines():
        time=STAMP.search(line)
        if time:
            try:
                current=datetime.fromisoformat(time.group(1).replace('Z','+00:00'))
                if t0 is None:t0=current
                t1=current
            except ValueError:pass
        match=RESULT.search(line)
        if match:
            if match.group(1)=='Saved':statuses['saved']+=1
            else:
                result=match.group(3).split(' · ',1)[0].strip().lower()
                statuses[result if result in ('retry','skipped') else 'handled_other']+=1
            names[match.group(2).strip()]+=1
        m=TIMING.search(line)
        if m:
            verification_ms+=int(m.group(2));manual_ms+=int(m.group(3));gaps.append(int(m.group(5)));samples+=1
        m=WRONG.search(line)
        if m:failures[m.group(1).strip()]+=1
    seconds=max(0,(t1-t0).total_seconds()) if t0 and t1 else 0
    counted=sum(statuses.values())
    answer={'elapsed_seconds':round(seconds,2),'saved_entries':statuses['saved'],
     'handled_capture_outcomes':counted,'retries':statuses['retry'],
     'skips':statuses['skipped'],'manual_review_seconds':round(manual_ms/1000,2),
     'mean_verification_ms':round(verification_ms/samples,1) if samples else None,
     'mean_capture_gap_ms':round(sum(gaps)/len(gaps),1) if gaps else None,
     'long_capture_gaps_over_4s':sum(g>4000 for g in gaps),
     'saved_entries_per_minute':round(statuses['saved']*60/seconds,2) if seconds else None,
     'top_failed_footer_identity':failures.most_common(5),
     'possible_repeated_names':names.most_common(6),
     'note':'Saved entries are NOT equivalent to confirmed distinct physical cards or correct printings.'}
    if physical is not None:
        answer['physical_cards_reported']=physical
        answer['maximum_possible_capture_coverage_pct']=round(100*min(statuses['saved'],physical)/physical,1) if physical else None
        answer['physical_card_attempt_rate_per_minute']=round(physical*60/seconds,2) if seconds else None
        answer['accuracy_note']='True precision/recall require physical card IDs and verified printing ground truth.'
    return answer

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log',help='Exported text diagnostic log')
    parser.add_argument('--physical',type=int,help='Actual number of unique physical cards stacked')
    args=parser.parse_args()
    print(json.dumps(summarize(args.log,args.physical),indent=2))
if __name__=='__main__':main()

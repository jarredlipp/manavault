#!/usr/bin/env python3
"""ManaVault scanner diagnostics, per-session and capture-to-save timing."""
import argparse
from datetime import datetime, timezone
from pathlib import Path
from zipfile import ZipFile, is_zipfile
from zoneinfo import ZoneInfo
import re, statistics, json

STAMP=re.compile(r'^(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d+)Z')
FILENAME=re.compile(r'^scan-(\d{8})-(\d{6})-')

def values(s,regex):return [int(x) for x in re.findall(regex,s)]
def percent(x,p):
    if not x:return None
    x=sorted(x);idx=(len(x)-1)*p/100;lo=int(idx);hi=min(len(x)-1,lo+1)
    return round(x[lo]+(x[hi]-x[lo])*(idx-lo),1)
def stat(a):
    return dict(count=len(a),mean_ms=round(statistics.mean(a),1) if a else None,
                p50_ms=percent(a,50),p95_ms=percent(a,95))
def iter_sessions(path):
    if is_zipfile(path):
        with ZipFile(path) as z:
            for name in z.namelist():
                if Path(name).name.startswith('scan-') and name.lower().endswith('.txt'):
                    yield Path(name).name,z.read(name).decode('utf8','replace')
    else:yield path.name,path.read_text(encoding='utf8',errors='replace')
def parse_utc(line):
    match=STAMP.match(line)
    if match:return datetime.fromisoformat(match.group(1)).replace(tzinfo=timezone.utc)
    return None

def analyze(sessions):
    capture=[];verification=[];manual=[];finalize=[];startup=[]
    counts=dict(saved=0,retried_or_skipped=0,verified_auto=0,
                manual_review_positive_count=0,unresolved_edition_count=0,
                slow_cycle_count=0,footer_pair_failures=0,unrecognized_title=0,
                too_dark=0,download_started=0)
    camera_sessions=0
    for name,content in sessions:
        capture+=values(content,r'capture gap (\d+)ms')
        v=values(content,r'\bverification (\d+)ms');verification+=v
        m=values(content,r'\bmanual review (\d+)ms');manual+=m
        finalize+=values(content,r'end-to-end finalize (\d+)ms')
        counts['saved']+=content.count('Saved DURING SCAN:')
        counts['retried_or_skipped']+=content.count('Handled DURING SCAN:')
        counts['verified_auto']+=len(re.findall(
            r'SCAN RECEIPT[^\n]* · verified ·[^\n]*(?:structured set \+ collector|strong printed collector)',content))
        counts['manual_review_positive_count']+=sum(x>0 for x in m)
        counts['unresolved_edition_count']+=content.count('Unresolved edition for ')
        counts['slow_cycle_count']+=content.count('Scan cycle SLOW:')
        counts['footer_pair_failures']+=content.count('Footer-first unable to establish set/#')
        counts['unrecognized_title']+=content.count('Footer-first unable to establish set/# for Unrecognized title:')
        counts['too_dark']+=content.count('Photo quality: Image too dark')
        counts['download_started']+=content.count('Card database: Downloading complete Scryfall printing database')
        if 'CameraX · ManaVault' in content.split('\n',1)[0]:
            camera_sessions+=1
            match=FILENAME.search(name)
            if match:
                local=datetime.strptime(match.group(1)+match.group(2),'%Y%m%d%H%M%S')
                local=local.replace(tzinfo=ZoneInfo('America/Detroit'))
                for line in content.splitlines():
                    if 'Offline printing engine: Ready:' in line:
                        ready=parse_utc(line)
                        if ready:
                            seconds=(ready-local.astimezone(timezone.utc)).total_seconds()
                            if 0<=seconds<=120:startup.append(round(seconds*1000))
                        break
    counts['camera_sessions']=camera_sessions
    counts['samples']={'capture_gap':stat(capture),'verification':stat(verification),
                       'manual_review':stat(manual),'end_to_end_finalize':stat(finalize),
                       'camera_start_to_native_ready':stat(startup)}
    return counts

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('diagnostics',type=Path)
    parser.add_argument('--json',action='store_true')
    args=parser.parse_args()
    result=analyze(iter_sessions(args.diagnostics))
    if args.json:print(json.dumps(result,indent=2))
    else:
        print('Camera sessions:',result['camera_sessions'],'saved:',result['saved'],
              'unrecognized title:',result['unrecognized_title'])
        print('Dark image alerts:',result['too_dark'],'download starts:',result['download_started'])
        for key,details in result['samples'].items():print(key,details)
if __name__=='__main__':main()

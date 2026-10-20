from pathlib import Path
r=Path(__file__).resolve().parents[1]
j=(r/'app/src/main/assets/index.html').read_text()
a=(r/'app/src/main/java/com/manavault/app/NativeScannerActivity.java').read_text()
p=(r/'app/src/main/java/com/manavault/app/TitleReadingPolicy.java').read_text()
assert "if(mv613NonTitleText(raw))return {reason:" in j
assert "if(mv613NonTitleText(raw))return null;" in j
assert "if(mv613NonTitleText(String(data?.name||'')))return null;" in j
assert 'TitleReadingPolicy.looksLikeRulesSentence(value)' in a
assert 'if(position>.72f)continue;' in a
assert 'each other' in p and 'cycling ' in p
print('Title identity guard source regression checks passed')

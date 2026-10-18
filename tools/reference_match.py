"""Offline, deterministic text-reference search helpers (no third-party model).

Reference strings are formed from Scryfall card metadata. They help identify
cards with imperfect title OCR; an FTS result NEVER verifies a physical printing.
"""
import re
import unicodedata

STOP = frozenset('the and you your target when this that card creature player with from into may one all then have has are for its who can not until end turn owner they their each control battlefield enters library graveyard hand cast mana cost'.split())

def tokens(text):
    text = ''.join(c for c in unicodedata.normalize('NFKD',str(text or '').lower()) if not unicodedata.combining(c))
    return [x for x in re.findall(r'[a-z][a-z0-9]{2,}',text) if x not in STOP]

def reference_fields(c):
    faces = c.get('card_faces') or []
    face_rules = ' '.join(str(f.get('oracle_text') or '') for f in faces)
    face_flavor = ' '.join(str(f.get('flavor_text') or '') for f in faces)
    face_types = ' '.join(str(f.get('type_line') or '') for f in faces)
    return (
        str(c.get('name') or ''),
        (str(c.get('type_line') or '')+' '+face_types).strip(),
        (str(c.get('oracle_text') or '')+' '+face_rules).strip(),
        (str(c.get('flavor_text') or '')+' '+face_flavor).strip(),
        (str(c.get('artist') or '')+' '+' '.join(str(f.get('artist') or '') for f in faces)).strip(),
    )

def reference_query(observed, limit=7):
    """Return a safe FTS5 AND query containing distinctive OCR terms.

    The caller should try progressively fewer terms if exact FTS returns no
    candidate. Never include user-provided FTS operators or punctuation.
    """
    values=[]
    for t in tokens(observed):
        if t not in values:values.append(t)
    return ' AND '.join('"'+t+'"' for t in values[:max(1,limit)])

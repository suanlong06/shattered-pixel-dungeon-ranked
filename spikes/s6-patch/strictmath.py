"""T0.8: Math.<transcendental> -> StrictMath.<same> in gameplay sources (not in visual-only packages).
Usage: python strictmath.py <root>... [--dry]"""
import os, re, sys
FUNCS = 'sin|cos|tan|asin|acos|atan|atan2|exp|log|log10|pow|cbrt|hypot|sinh|cosh|tanh|expm1|log1p'
CALL = re.compile(r'(?<![\w.])Math\.(' + FUNCS + r')\s*\(')
SKIP = ('/effects/', '/sprites/', '/ui/', '/scenes/', '/tiles/', '/windows/', '/noosa/', '/glwrap/', '/gltextures/', '/glscripts/', '/input/')
dry = '--dry' in sys.argv
calls = files = 0
for root in [a for a in sys.argv[1:] if a != '--dry']:
    for d, _, fs in os.walk(root):
        dd = d.replace(os.sep, '/') + '/'
        if any(x in dd for x in SKIP):
            continue
        for f in fs:
            if not f.endswith('.java'):
                continue
            p = os.path.join(d, f)
            s = open(p, encoding='utf-8').read()
            s2, n = CALL.subn(lambda m: 'StrictMath.' + m.group(1) + '(', s)
            if n:
                calls += n; files += 1
                print(f'{n:4d} {os.path.relpath(p, root)}')
                if not dry:
                    open(p, 'w', encoding='utf-8', newline='').write(s2)
print(f'{calls} calls in {files} files')

"""Spike S6: route RNG calls inside visual-only nested classes of gameplay files to the fx stream.

Finds (anonymous or named) class bodies whose supertype is a visual type (particles, emitters,
scene-graph nodes, sprites) and rewrites `Random.` to `com.watabou.utils.fx.Random.` inside them.
Usage: python fxify.py <source root> [--dry]
"""
import os
import re
import sys

VISUAL = r'(?:PixelParticle(?:\.Shrinking)?|Emitter(?:\.Factory)?|Factory|Group|Image|Visual|Gizmo|MovieClip|' \
         r'CharSprite|ItemSprite|MobSprite|Halo|Tilemap|ColorBlock|Particle|BitmaskEmitter)'
# visual nested classes, anonymous visual instances, and visual-only methods (staffFx, fx, appearVFX...)
DECL = re.compile(r'(?:class\s+\w+\s+extends\s+' + VISUAL + r'\b[^{]*|new\s+' + VISUAL + r'\s*\([^)]*\)\s*'
                  r'|void\s+\w*(?:[Ff]x|VFX)\w*\s*\([^)]*\)\s*)\{')
SKIP_DIRS = ('/effects/', '/sprites/', '/ui/', '/scenes/', '/tiles/', '/windows/')
CALL = re.compile(r'(?<![\w.])Random\.')


def body_end(s, open_idx):
    depth = 0
    for i in range(open_idx, len(s)):
        c = s[i]
        if c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
            if depth == 0:
                return i
    return len(s)


def process(path, dry):
    s = open(path, encoding='utf-8').read()
    spans = []
    for m in DECL.finditer(s):
        o = m.end() - 1
        spans.append((o, body_end(s, o)))
    if not spans:
        return 0
    out, last, n = [], 0, 0
    for a, b in sorted(spans):
        if a < last:
            continue  # nested inside an already handled span
        out.append(s[last:a])
        seg, k = CALL.subn('com.watabou.utils.fx.Random.', s[a:b])
        out.append(seg)
        n += k
        last = b
    out.append(s[last:])
    if n and not dry:
        open(path, 'w', encoding='utf-8', newline='').write(''.join(out))
    return n


def main():
    root, dry = sys.argv[1], '--dry' in sys.argv
    total_calls = total_files = 0
    for d, _, fs in os.walk(root):
        dd = d.replace('\\', '/') + '/'
        if any(x in dd for x in SKIP_DIRS):
            continue
        for f in fs:
            if f.endswith('.java'):
                n = process(os.path.join(d, f), dry)
                if n:
                    total_calls += n
                    total_files += 1
                    print(f'{n:4d} {os.path.relpath(os.path.join(d, f), root)}')
    print(f'{total_calls} calls in {total_files} files')


if __name__ == '__main__':
    main()

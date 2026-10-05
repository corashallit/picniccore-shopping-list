"""VectorDrawable XML -> SVG, for the round-trip check (tools/roundtrip.py).

Mirrors how the app draws a library sticker (§3.3): with shadow=True the `cut` group is first drawn tinted in the
sticker shadow colour at (+1.6, +2.2), then the drawable as it is. Paths with fillType evenOdd, clip-path groups and
aapt gradients are supported (everything the emitter writes)."""
import xml.etree.ElementTree as ET

A = '{http://schemas.android.com/apk/res/android}'


def _num(v, d=0.0):
    return float(v) if v is not None else d


def _color(c):
    if c is None:
        return None
    c = c.strip()
    if len(c) == 9:          # #AARRGGBB
        return '#' + c[3:]
    return c


def convert(xml_text, shadow=None, offset=(1.6, 2.2), scale=1.0):
    root = ET.fromstring(xml_text)
    vw, vh = _num(root.get(A + 'viewportWidth')), _num(root.get(A + 'viewportHeight'))
    defs, ids = [], [0]

    def nid(p):
        ids[0] += 1
        return f'{p}{ids[0]}'

    def path_svg(el, tint=None):
        d = el.get(A + 'pathData')
        attrs = [f'd="{d}"']
        fill = _color(el.get(A + 'fillColor'))
        grad = None
        for ch in el:
            if ch.tag == 'aapt:attr' or ch.tag.endswith('}attr'):
                g = list(ch)[0]
                gid = nid('g')
                stops = ''.join(f'<stop offset="{s.get(A + "offset")}" stop-color="{_color(s.get(A + "color"))}"/>' for s in g)
                defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{g.get(A + "startX")}" y1="{g.get(A + "startY")}" '
                            f'x2="{g.get(A + "endX")}" y2="{g.get(A + "endY")}">{stops}</linearGradient>')
                grad = f'url(#{gid})'
        if tint:
            fill = tint if (fill or grad) else None
            grad = None
        attrs.append(f'fill="{grad or fill or "none"}"')
        if el.get(A + 'fillAlpha'):
            attrs.append(f'fill-opacity="{el.get(A + "fillAlpha")}"')
        if el.get(A + 'fillType') == 'evenOdd':
            attrs.append('fill-rule="evenodd"')
        stroke = _color(el.get(A + 'strokeColor'))
        if stroke:
            attrs.append(f'stroke="{tint or stroke}" stroke-width="{el.get(A + "strokeWidth", "0")}"')
            attrs.append(f'stroke-linecap="{el.get(A + "strokeLineCap", "butt")}" stroke-linejoin="{el.get(A + "strokeLineJoin", "miter")}"')
            if el.get(A + 'strokeAlpha'):
                attrs.append(f'stroke-opacity="{el.get(A + "strokeAlpha")}"')
        return '<path ' + ' '.join(attrs) + '/>'

    def group_svg(el, tint=None, only=None):
        out = []
        clip = None
        for ch in el:
            tag = ch.tag
            if tag == 'clip-path':
                cid = nid('c')
                defs.append(f'<clipPath id="{cid}" clipPathUnits="userSpaceOnUse"><path d="{ch.get(A + "pathData")}"/></clipPath>')
                clip = cid
            elif tag == 'path':
                out.append(path_svg(ch, tint))
            elif tag == 'group':
                if only and ch.get(A + 'name') not in only:
                    continue
                out.append(group_svg(ch, tint))
        body = ''.join(out)
        return f'<g clip-path="url(#{clip})">{body}</g>' if clip else f'<g>{body}</g>'

    body = ''
    if shadow:
        body += f'<g transform="translate({offset[0]} {offset[1]})">{group_svg(root, tint=shadow, only={"cut"})}</g>'
    body += group_svg(root)
    w, h = vw * scale, vh * scale
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {vw} {vh}">'
            f'<defs>{"".join(defs)}</defs>{body}</svg>')

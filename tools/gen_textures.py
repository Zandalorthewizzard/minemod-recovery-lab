#!/usr/bin/env python3
"""Deterministic texture generator for the Minemod Shellback (scope S9).

Standard library only; no randomness, clock or environment reads.  PNGs are RGBA8,
colour type 6, filter 0, zlib level 9, without ancillary chunks.

    python3 tools/gen_textures.py            write the three PNGs
    python3 tools/gen_textures.py --check    validate assets, JSON links and rest geometry
    python3 tools/gen_textures.py --preview  contact sheet + uv_layout.json
    python3 tools/gen_textures.py --uv-json  print the UV / geometry contract
"""
import argparse, hashlib, json, os, re, struct, tempfile, zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = "src/main/resources/assets/minemod"
ENTITY_PNG = ASSETS + "/textures/entity/shellback.png"
PLATE_PNG = ASSETS + "/textures/item/shellback_plate.png"
EGG_PNG = ASSETS + "/textures/item/shellback_spawn_egg.png"
ITEMS_DIR, MODELS_DIR, TEXTURES = ASSETS + "/items", ASSETS + "/models/item", ASSETS + "/textures"
JAVA = "src/client/java/dev/minemod/client/ShellbackGeometry.java"
EVIDENCE = "evidence/S9"
ITEMS = ("shellback_plate", "shellback_spawn_egg")
ALLOWED_MINECRAFT = ("minecraft:model", "minecraft:item/generated")

# --- UV layout: single source of truth for textures, S7 and the Java check ---
# name, cube w, h, d, texOffs.x, texOffs.y, rest pivot (x,y,z), box origin (x,y,z),
# CubeDeformation grow (x,y,z).  Grow keeps same-facing rest surfaces apart; vanilla
# ModelPart.Cube applies it symmetrically (ModelPart.java:267-272).
UV_LAYOUT = (
    ("body",            8, 6, 12,  0,  0, (0.0, 20.0, 0.0), (-4.0, -6.0, -6.0), (0.0, 0.0, 0.0)),
    ("head",            6, 5,  5, 40,  0, (0.0, 17.0, -6.0), (-3.0, -3.0, -5.0), (0.0, 0.0, 0.0)),
    ("plate_front",    10, 3,  5,  0, 18, (0.0, 16.0, -3.0), (-5.0, -3.0, -5.0), (0.0, 0.0, 0.0)),
    ("plate_mid",      10, 3,  5, 30, 18, (0.0, 15.0, -1.0), (-5.0, -3.0, -3.0), (0.05, 0.0, 0.0)),
    ("plate_rear",     10, 3,  5,  0, 26, (0.0, 14.0, 2.0), (-5.0, -3.0, -2.0), (0.1, 0.0, 0.0)),
    ("tail",            2, 2,  6, 30, 26, (0.0, 16.0, 6.0), (-1.0, -1.0, 0.0), (0.0, 0.0, 0.0)),
    ("underside",       7, 1, 10,  0, 34, (0.0, 20.0, 0.0), (-3.5, 0.0, -5.0), (-0.02, -0.02, 0.0)),
    ("leg_front_left",  3, 4,  3,  0, 45, (2.0, 20.0, -4.0), (-1.5, 0.0, -1.5), (0.0, 0.0, 0.0)),
    ("leg_front_right", 3, 4,  3, 12, 45, (-2.0, 20.0, -4.0), (-1.5, 0.0, -1.5), (0.0, 0.0, 0.0)),
    ("leg_back_left",   3, 4,  3, 24, 45, (2.0, 20.0, 4.0), (-1.5, 0.0, -1.5), (0.0, 0.0, 0.0)),
    ("leg_back_right",  3, 4,  3, 36, 45, (-2.0, 20.0, 4.0), (-1.5, 0.0, -1.5), (0.0, 0.0, 0.0)),
)
BY_NAME = {row[0]: row for row in UV_LAYOUT}
FACES = ("up", "down", "north", "south", "west", "east")
EW = EH = 64
IW = 16
A = 255
STONE = ((0x26, 0x26, 0x2A, A), (0x2E, 0x2E, 0x34, A), (0x38, 0x38, 0x3F, A),
         (0x42, 0x42, 0x49, A), (0x4E, 0x4E, 0x55, A))
STONE_DARK, EDGE = (0x1B, 0x1B, 0x1F, A), (0x8A, 0x8A, 0x92, A)
AMBER, GLINT = (0xE5, 0x9A, 0x2F, A), (0xFF, 0xD2, 0x7A, A)
UNDER = ((0xC5, 0xB2, 0x99, A), (0xD4, 0xC3, 0xAB, A), (0xE0, 0xD2, 0xBC, A), (0xAF, 0x9C, 0x84, A))

# 16x16 icon masks: '.' transparent, 'L' light rim, '#' stone, 'D' dark chip, 'A' amber.
PLATE_MASK = ("................/.....LLLLLL...../...LL######LL.../..L##########L../"
              ".L############L./.L############L./.L###DD####DD#L./.L############L./"
              "..L##########L../..L###D####D#L../...L########L.../....L######L..../"
              ".....L####L...../......L##L....../.......LL......./................").split("/")
EGG_MASK = ("................/......LLL......./.....L###L....../....L##A##L...../"
            "....L#####L...../...L##A####L..../...L####A##L..../..L#########L.../"
            "..L##A######L.../..L#####A###L.../..L##########L../..L####A#####L../"
            "...L########L.../....L######L..../.....LL##LL...../................").split("/")
# 3x5 bitmap font, 15 bits per glyph (5 rows x 3 columns, '#'=1), hex encoded.
FONT = dict(zip("ABCDEFGHIKLMNOPRSTUXY0123456789 _?",
                "2BED 6BAE 3923 6B6E 79A7 79A4 396B 5BED 7497 5BAD 4927 5FED 6B6D 2B6A "
                "6BA4 6BAD 388E 7492 5B6B 5AAD 5A92 7B6F 2C97 62A7 72CF 5BC9 798E 39EF "
                "7292 7BEF 7BCE 0000 0007 6282".split()))
PART_COLORS = ((0x7F, 0xD1, 0xFF, A), (0xFF, 0xD1, 0x7F, A), (0xFF, 0x9E, 0x64, A), (0xFF, 0x7F, 0x9E, A),
               (0xD1, 0x7F, 0xFF, A), (0x9E, 0xFF, 0x7F, A), (0x7F, 0xFF, 0xC4, A), (0xFF, 0xFF, 0x7F, A),
               (0x9E, 0xC4, 0xFF, A), (0xC4, 0x9E, 0xFF, A), (0xFF, 0xC4, 0x9E, A))
PNG_SIG = b"\x89PNG\r\n\x1a\n"

def h32(a, b=0, c=0):
    h = 0x811C9DC5
    for v in (a, b, c):
        h = ((h ^ (int(v) & 0xFFFFFFFF)) * 0x01000193) & 0xFFFFFFFF
        h ^= h >> 15
    return h

def put(px, w, h, x, y, col):
    if 0 <= x < w and 0 <= y < h: px[y * w + x] = col

def write_png(path, width, height, pixels):
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for col in pixels[y * width:(y + 1) * width]:
            if len(col) != 4: raise SystemExit("pixel is not RGBA: %r" % (col,))
            raw.extend(col)

    def chunk(tag, payload):
        body = tag + payload
        return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)
    data = PNG_SIG + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    if os.path.dirname(path): os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh: fh.write(data)
    return hashlib.sha256(data).hexdigest()

def read_png(path):
    """Decode the filter-0 RGBA8 PNGs written above (all that --check reads)."""
    with open(path, "rb") as fh: data = fh.read()
    if data[:8] != PNG_SIG: raise ValueError("%s: bad PNG signature" % path)
    pos, chunks, idat, hdr = 8, [], b"", None
    while pos + 8 <= len(data):
        (n,) = struct.unpack(">I", data[pos:pos + 4])
        tag, body = data[pos + 4:pos + 8], data[pos + 8:pos + 8 + n]
        pos += 12 + n
        chunks.append(tag.decode("ascii", "replace"))
        if tag == b"IHDR": hdr = struct.unpack(">IIBBBBB", body)
        elif tag == b"IDAT": idat += body
        elif tag == b"IEND": break
    if hdr is None: raise ValueError("%s: no IHDR" % path)
    w, h, depth, ctype = hdr[:4]
    raw, stride, off = zlib.decompress(idat), w * 4, 0
    px = [(0, 0, 0, 0)] * (w * h)
    for y in range(h):
        filt, off = raw[off], off + 1
        if filt != 0 or off + stride > len(raw): raise ValueError("%s: filter %d or truncated scanline at row %d" % (path, filt, y))
        line, off = raw[off:off + stride], off + stride
        px[y * w:(y + 1) * w] = [(line[i], line[i + 1], line[i + 2], line[i + 3]) for i in range(0, stride, 4)]
    return {"w": w, "h": h, "depth": depth, "ctype": ctype, "chunks": chunks, "px": px}

def face_rect(row, face):
    """ModelPart.Cube box-UV contract, vanilla 26.3 (ModelPart.java:287-322)."""
    _, w, h, d, ox, oy = row[:6]
    return {"down": (ox + d, oy, w, d), "up": (ox + d + w, oy, w, d),
            "west": (ox, oy + d, d, h), "north": (ox + d, oy + d, w, h),
            "east": (ox + d + w, oy + d, d, h),
            "south": (ox + d + w + d, oy + d, w, h)}[face]

def part_region(row):
    _, w, h, d, ox, oy = row[:6]
    return ox, oy, 2 * d + 2 * w, d + h

def stone_px(x, y, seed, bias=0):
    h = h32(x, y, seed)
    if (h >> 9) % 11 == 0: return STONE_DARK
    return STONE[max(0, min(4, (h % 5) + bias))]

def paint_head_front(px, x0, y0):
    for y in range(5):
        for x in range(6): put(px, EW, EH, x0 + x, y0 + y, stone_px(x, y, 4242))
    for x in range(6): put(px, EW, EH, x0 + x, y0, EDGE)
    for y in (1, 2):
        for x in (0, 1, 4, 5): put(px, EW, EH, x0 + x, y0 + y, AMBER)
    for x in (1, 5): put(px, EW, EH, x0 + x, y0 + 1, GLINT)
    for x, y in ((2, 3), (3, 3), (1, 4), (2, 4), (3, 4), (4, 4)):
        put(px, EW, EH, x0 + x, y0 + y, STONE_DARK)

def paint_face(px, name, face, x0, y0, fw, fh):
    seed = sum(ord(c) * (i + 7) for i, c in enumerate(name))
    if name == "underside":
        for y in range(fh):
            for x in range(fw):
                border = x in (0, fw - 1) or y in (0, fh - 1)
                put(px, EW, EH, x0 + x, y0 + y, UNDER[3] if border else UNDER[h32(x, y, seed) % 3])
        return
    if name == "head" and face == "north":
        paint_head_front(px, x0, y0)
        return
    for y in range(fh):
        for x in range(fw):
            edge = face in ("north", "south", "west", "east") and y == 0
            edge |= name.startswith("leg_") and y in (0, 1)
            edge |= name.startswith("plate") and face == "up" and (x in (0, fw - 1) or y in (0, fh - 1, fh - 2))
            edge |= name == "body" and face == "up" and (y % 4 == 0 or x in (0, fw - 1))
            edge |= name == "tail" and x == 0
            if edge: col = EDGE
            elif face == "down": col = STONE_DARK if h32(x, y, seed) & 3 else STONE[0]
            else: col = stone_px(x, y, seed, 1 if face == "up" else 0)
            put(px, EW, EH, x0 + x, y0 + y, col)

def make_entity():
    px = [(0, 0, 0, 0)] * (EW * EH)
    for row in UV_LAYOUT:
        for face in FACES: paint_face(px, row[0], face, *face_rect(row, face))
    return EW, EH, px

def make_icon(mask, seed):
    px = [(0, 0, 0, 0)] * (IW * IW)
    for y, line in enumerate(mask):
        if len(line) != IW: raise SystemExit("mask row %d is %d chars, expected %d" % (y, len(line), IW))
        for x, ch in enumerate(line):
            if ch == "L": px[y * IW + x] = EDGE
            elif ch == "D": px[y * IW + x] = STONE_DARK
            elif ch == "A": px[y * IW + x] = GLINT if h32(x, y, seed) & 1 else AMBER
            elif ch != ".": px[y * IW + x] = stone_px(x, y, seed, 1 if y < 8 else -1)
    return IW, IW, px

def targets():
    return ((ENTITY_PNG, make_entity()), (PLATE_PNG, make_icon(PLATE_MASK, 0x51A7E)),
            (EGG_PNG, make_icon(EGG_MASK, 0xE6611)))

def generate():
    return {rel: write_png(os.path.join(ROOT, rel), w, h, px) for rel, (w, h, px) in targets()}

def uv_contract():
    def rect(values): return dict(zip(("x", "y", "w", "h"), values))
    return {
        "texture": {"entity": "minemod:textures/entity/shellback.png", "width": EW, "height": EH},
        "uvOrigin": "top-left, box UV, ModelPart.Cube order down/up/west/north/east/south",
        "parts": [{
            "name": row[0], "cube": {"w": row[1], "h": row[2], "d": row[3]},
            "texOffs": {"x": row[4], "y": row[5]}, "region": rect(part_region(row)),
            "pivot": dict(zip("xyz", row[6])), "boxOrigin": dict(zip("xyz", row[7])),
            "deform": dict(zip("xyz", row[8])),
            "faces": {f: rect(face_rect(row, f)) for f in FACES},
        } for row in UV_LAYOUT],
    }

def draw_text(px, w, h, x, y, text, scale, col):
    cx = x
    for ch in text.upper():
        code = int(FONT.get(ch, FONT["?"]), 16)
        for gy in range(5):
            bits = (code >> (12 - 3 * gy)) & 7
            for gx in range(3):
                if bits & (4 >> gx):
                    for dy in range(scale):
                        for dx in range(scale):
                            put(px, w, h, cx + gx * scale + dx, y + gy * scale + dy, col)
        cx += 4 * scale

def frame(px, w, h, x0, y0, x1, y1, col):
    for x in range(x0, x1 + 1):
        for y in (y0, y1): put(px, w, h, x, y, col)
    for y in range(y0, y1 + 1):
        for x in (x0, x1): put(px, w, h, x, y, col)

def blit(px, W, H, img, ox, oy, scale):
    for y in range(img["h"]):
        for x in range(img["w"]):
            col = img["px"][y * img["w"] + x]
            col = col if col[3] else (0x10, 0x10, 0x14, A)
            for dy in range(scale):
                for dx in range(scale): put(px, W, H, ox + x * scale + dx, oy + y * scale + dy, col)

def preview():
    ent = read_png(os.path.join(ROOT, ENTITY_PNG))
    icons = ((read_png(os.path.join(ROOT, PLATE_PNG)), "PLATE X16"), (read_png(os.path.join(ROOT, EGG_PNG)), "EGG X16"))
    W, H, scale = 784, 806, 8
    px = [(0x18, 0x18, 0x1C, A)] * (W * H)
    blit(px, W, H, ent, 0, 0, scale)
    for i, row in enumerate(UV_LAYOUT):
        x, y, w, h = part_region(row)
        frame(px, W, H, x * scale, y * scale, (x + w) * scale - 1, (y + h) * scale - 1, PART_COLORS[i])
        draw_text(px, W, H, x * scale + 3, y * scale + 3, str(i + 1), 2, PART_COLORS[i])
        draw_text(px, W, H, 520, 8 + i * 22, "%d %s" % (i + 1, row[0]), 2, PART_COLORS[i])
    for i, (icon, label) in enumerate(icons):
        ox = 0 if i == 0 else 264
        blit(px, W, H, icon, ox, 528, 16)
        frame(px, W, H, ox, 528, ox + 255, 783, (0x9A, 0x9A, 0xA2, A))
        draw_text(px, W, H, ox + 2, 788, label, 2, (0xE8, 0xE8, 0xEE, A))
    write_png(os.path.join(ROOT, EVIDENCE, "contact_sheet.png"), W, H, px)
    with open(os.path.join(ROOT, EVIDENCE, "uv_layout.json"), "w", encoding="utf-8") as fh:
        json.dump(uv_contract(), fh, indent=2); fh.write("\n")

def mean_luma(png, rects):
    values = [0.299 * png["px"][y * png["w"] + x][0] + 0.587 * png["px"][y * png["w"] + x][1]
              + 0.114 * png["px"][y * png["w"] + x][2]
              for x1, y1, w, h in rects for y in range(y1, y1 + h) for x in range(x1, x1 + w)]
    return sum(values) / len(values) if values else 0.0

def is_amber(col):
    r, g, b, a = col
    return a == 255 and r >= 200 and 120 <= g <= 225 and b <= 145 and r > g > b

def check_png(probs):
    for rel, size in ((ENTITY_PNG, 64), (PLATE_PNG, 16), (EGG_PNG, 16)):
        png = read_png(os.path.join(ROOT, rel))
        if (png["w"], png["h"]) != (size, size):
            probs.append("%s: %dx%d, expected %dx%d" % (rel, png["w"], png["h"], size, size))
        if (png["depth"], png["ctype"]) != (8, 6):
            probs.append("%s: depth %d / colour type %d, expected 8/6" % (rel, png["depth"], png["ctype"]))
        extra = [c for c in png["chunks"] if c not in ("IHDR", "IDAT", "IEND")]
        if extra: probs.append("%s: unexpected chunks %s" % (rel, extra))

def check_determinism(probs):
    with tempfile.TemporaryDirectory(prefix="minemod-s9-") as tmp:
        for rel, (w, h, px) in targets():
            path = os.path.join(tmp, os.path.basename(rel))
            first = write_png(path, w, h, px)
            with open(os.path.join(ROOT, rel), "rb") as fh: disk = hashlib.sha256(fh.read()).hexdigest()
            if first != disk: probs.append("%s: regenerated %s != on-disk %s" % (rel, first[:16], disk[:16]))
            if write_png(path, w, h, px) != first: probs.append("%s: two in-process runs differ" % rel)

def check_entity_uv(probs):
    regions = {row[0]: part_region(row) for row in UV_LAYOUT}
    names = list(regions)
    for name, (x, y, w, h) in regions.items():
        if x < 0 or y < 0 or x + w > EW or y + h > EH: probs.append("%s region leaves the %dx%d texture" % (name, EW, EH))
    for i, a in enumerate(names):
        for b in names[i + 1:]:
            (ax, ay, aw, ah), (bx, by, bw, bh) = regions[a], regions[b]
            if ax < bx + bw and bx < ax + aw and ay < by + bh and by < ay + ah: probs.append("%s overlaps %s" % (a, b))
    for row in UV_LAYOUT:
        rx, ry, rw, rh = part_region(row)
        for face in FACES:
            x, y, w, h = face_rect(row, face)
            if x < rx or y < ry or x + w > rx + rw or y + h > ry + rh: probs.append("%s/%s escapes its region" % (row[0], face))

def check_seams(probs):
    png = read_png(os.path.join(ROOT, ENTITY_PNG))
    w, used = png["w"], set()
    for row in UV_LAYOUT:
        for face in FACES:
            x0, y0, fw, fh = face_rect(row, face)
            for y in range(y0, y0 + fh):
                for x in range(x0, x0 + fw):
                    used.add((x, y))
                    if png["px"][y * w + x][3] != 255: probs.append("%s/%s transparent at (%d,%d)" % (row[0], face, x, y))
    probs.extend("opaque pixel outside the layout at (%d,%d)" % (x, y)
                 for y in range(png["h"]) for x in range(w) if (x, y) not in used and png["px"][y * w + x][3])
    hx, hy, hw, hh = face_rect(BY_NAME["head"], "north")
    amber = sum(is_amber(png["px"][y * w + x]) for y in range(hy, hy + hh) for x in range(hx, hx + hw))
    if amber < 4: probs.append("head/north carries %d amber pixels, expected >= 4" % amber)
    plates = [face_rect(BY_NAME[n], f) for n in ("plate_front", "plate_mid", "plate_rear") for f in FACES]
    plate_luma, under_luma = mean_luma(png, plates), mean_luma(png, [face_rect(BY_NAME["underside"], f) for f in FACES])
    if under_luma <= plate_luma: probs.append("underside luma %.1f is not above plate luma %.1f" % (under_luma, plate_luma))

def check_items_alpha(probs):
    for rel in (PLATE_PNG, EGG_PNG):
        px = read_png(os.path.join(ROOT, rel))["px"]
        probs.extend("%s: corner (%d,%d) is not transparent" % (rel, x, y)
                     for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)) if px[y * IW + x][3])
        opaque, partial = sum(1 for c in px if c[3] == 255), sum(1 for c in px if 0 < c[3] < 255)
        if opaque * 4 < len(px): probs.append("%s: %d/%d opaque pixels, below 25%%" % (rel, opaque, len(px)))
        if partial: probs.append("%s: %d semi-transparent pixels" % (rel, partial))

def walk_strings(node, out):
    if isinstance(node, dict):
        for key, value in node.items(): walk_strings(key, out); walk_strings(value, out)
    elif isinstance(node, list):
        for value in node: walk_strings(value, out)
    elif isinstance(node, str): out.append(node)

def check_json_links(probs):
    def load(rel):
        with open(os.path.join(ROOT, rel), encoding="utf-8") as fh: return json.load(fh)
    for name in ITEMS:
        item_rel, model_rel = ITEMS_DIR + "/" + name + ".json", MODELS_DIR + "/" + name + ".json"
        try: item, model = load(item_rel), load(model_rel)
        except (OSError, ValueError) as exc:
            probs.append("%s: %s" % (name, exc)); continue
        want = "minemod:item/" + name
        if item.get("model", {}).get("model") != want:
            probs.append("%s: points at %r, expected %r" % (item_rel, item.get("model"), want))
        if model.get("parent") != "minecraft:item/generated":
            probs.append("%s: parent is %r" % (model_rel, model.get("parent")))
        if model.get("textures", {}).get("layer0") != want:
            probs.append("%s: layer0 is %r" % (model_rel, model.get("textures", {}).get("layer0")))
        if not os.path.exists(os.path.join(ROOT, TEXTURES, "item", name + ".png")):
            probs.append("%s: layer0 does not resolve to a PNG" % model_rel)
        strings = []
        walk_strings([item, model], strings)
        probs.extend("%s: vanilla reference %r" % (name, v)
                     for v in strings if v.startswith("minecraft:") and v not in ALLOWED_MINECRAFT)

def java_parts(path):
    """Parse ShellbackGeometry.java into {part name: (cube numbers, pivot numbers)}."""
    with open(path, encoding="utf-8") as fh: src = fh.read()
    names = dict(re.findall(r'String (\w+) = "([^"]+)";', src))
    helper = re.search(r"leg\(int u\)\s*\{\s*return cube\(([^)]*)\)", src)
    leg = [v.strip() for v in helper.group(1).split(",")] if helper else None
    parts = {}
    pattern = r"addOrReplaceChild\((\w+),\s*(cube|leg)\(([^)]*)\),\s*PartPose\.offset\(([^)]*)\)\)"
    for const, kind, args, pose in re.findall(pattern, src):
        values = [v.strip() for v in args.split(",")]
        if kind == "leg":
            if leg is None: raise ValueError("leg() helper is missing")
            u = values[0]; values = [u] + [v.replace("u", u) for v in leg[1:]]
        parts[names.get(const, const)] = ([float(v.rstrip("Ff")) for v in values],
                                          [float(v.rstrip("Ff")) for v in pose.split(",")])
    return parts

def java_aabb(values, pose):
    x, y, z = values[2] + pose[0], values[3] + pose[1], values[4] + pose[2]
    w, h, d = values[5:8]
    gx, gy, gz = (values[8:11] + [0.0, 0.0, 0.0])[:3]
    return (x - gx, y - gy, z - gz, x + w + gx, y + h + gy, z + d + gz)

def check_geometry(probs, path=JAVA):
    try: parts = java_parts(os.path.join(ROOT, path))
    except (OSError, ValueError) as exc:
        probs.append("%s: %s" % (path, exc)); return
    known = {row[0] for row in UV_LAYOUT}
    for row in UV_LAYOUT:
        name = row[0]
        if name not in parts:
            probs.append("%s: missing from %s" % (name, path)); continue
        values, pose = parts[name]
        values += [0.0] * (11 - len(values))
        want = [row[4], row[5], *row[7], row[1], row[2], row[3], *row[8]]
        for label, have, expect in zip("texU texV x y z w h d growX growY growZ".split(), values, want):
            if abs(have - expect) > 1e-6: probs.append("%s: Java %s=%g, UV_LAYOUT=%g" % (name, label, have, expect))
        for label, have, expect in zip(("pivotX", "pivotY", "pivotZ"), pose, row[6]):
            if abs(have - expect) > 1e-6: probs.append("%s: Java %s=%g, UV_LAYOUT=%g" % (name, label, have, expect))
    probs.extend("%s: unexpected Java part" % n for n in parts if n not in known)
    faces = []
    for name in parts:
        lo = java_aabb(parts[name][0], parts[name][1]); hi = lo[3:]
        for axis in range(3):
            u, v = (axis + 1) % 3, (axis + 2) % 3
            for side, plane in ((-1, lo[axis]), (1, hi[axis])):
                faces.append((name, axis, side, plane, lo[u], hi[u], lo[v], hi[v]))
    for i, a in enumerate(faces):
        for c in faces[i + 1:]:
            if a[0] == c[0] or a[1] != c[1] or a[2] != c[2] or abs(a[3] - c[3]) > 1e-6: continue
            du, dv = min(a[5], c[5]) - max(a[4], c[4]), min(a[7], c[7]) - max(a[6], c[6])
            if du > 1e-6 and dv > 1e-6:
                axis = "xyz"[a[1]]
                probs.append("%s and %s: same-facing %s-%s plane at %s=%g, overlap %gx%g"
                             % (a[0], c[0], axis, ("min", "max")[a[2] > 0], axis, a[3], du, dv))

def check():
    groups = (("png", check_png), ("determinism", check_determinism), ("entity_uv", check_entity_uv),
              ("seams", check_seams), ("items_alpha", check_items_alpha), ("json_links", check_json_links),
              ("geometry", check_geometry))
    failed = 0
    for label, fn in groups:
        probs = []
        try: fn(probs)
        except Exception as exc:  # noqa: BLE001 - a check must report, never crash
            probs.append("%s: %s" % (type(exc).__name__, exc))
        if probs:
            failed += 1
            print("%s FAIL" % label)
            for text in probs[:30]: print("  - " + text)
        else: print("%s OK" % label)
    print("ALL OK" if not failed else "CHECK FAILED")
    return 0 if not failed else 1

def main(argv=None):
    parser = argparse.ArgumentParser(description="Deterministic Shellback textures (scope S9)")
    parser.add_argument("--check", action="store_true", help="validate PNGs, seams, JSON links and geometry")
    parser.add_argument("--preview", action="store_true", help="write evidence/S9/contact_sheet.png")
    parser.add_argument("--uv-json", action="store_true", help="print the UV / geometry contract")
    args = parser.parse_args(argv)
    if args.uv_json: print(json.dumps(uv_contract(), indent=2)); return 0
    if args.check: return check()
    for rel, digest in generate().items(): print("%s  %s" % (digest, rel))
    if args.preview: preview(); print(EVIDENCE + "/contact_sheet.png")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""S10 generator: three original 64x64 RGBA plated-shield sprites.

The vanilla UV layout is preserved exactly (ShieldModel.createLayer():
plate texOffs(0,0) box 12x22x1, handle texOffs(26,0) box 2x6x6).
Dark layered stone plate, stone rim with amber rivets and an open centre.
stdlib only (struct, zlib), fully deterministic: no clock, no unseeded
randomness.
"""
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "src/main/resources/assets/minemod/textures/entity/shield"
SIG = b"\x89PNG\r\n\x1a\n"
SIZE = 64

STONE = (74, 78, 86)
STONE_DARK = (52, 55, 62)
STONE_LIGHT = (110, 116, 126)
SEAM = (34, 37, 43)
GRIP = (44, 47, 54)
GRIP_LIGHT = (86, 91, 100)
RIM = (124, 130, 140)
RIM_LIGHT = (172, 178, 188)
RIM_SHADOW = (72, 76, 85)
AMBER = (216, 150, 44)
AMBER_LIGHT = (250, 208, 110)
AMBER_DARK = (146, 92, 22)

FRONT = (1, 1, 13, 23)          # plate front face
SIDE_RIGHT = (0, 1, 1, 23)      # plate right side
SIDE_LEFT = (13, 1, 14, 23)     # plate left side
BACK = (14, 1, 26, 23)          # plate back face
TOP = (1, 0, 13, 1)             # plate top face
BOTTOM = (13, 0, 25, 1)         # plate bottom face
HANDLE = (                      # handle box faces
    (32, 0, 34, 6), (34, 0, 36, 6), (26, 6, 32, 12),
    (32, 6, 34, 12), (34, 6, 40, 12), (40, 6, 42, 12),
)
CENTRE = (3, 3, 11, 21)         # open centre of the overlay
BANDS = ((1, 6), (7, 12), (13, 18), (19, 23))  # plate layers, seams at y=6,12,18


def noise(x, y, salt=0):
    value = (x * 374761393 + y * 668265263 + salt * 2246822519) & 0xFFFFFFFF
    value = ((value ^ (value >> 13)) * 1274126177) & 0xFFFFFFFF
    return (value ^ (value >> 16)) & 0xFF


def stone(x, y, salt, base=STONE, spread=5):
    delta = (noise(x, y, salt) % (spread * 2 + 1)) - spread
    return tuple(max(0, min(255, channel + delta)) for channel in base)


def tint(colour, delta):
    return tuple(max(0, min(255, channel + delta)) for channel in colour)


class Canvas:
    def __init__(self):
        self.rows = [bytearray(SIZE * 4) for _ in range(SIZE)]

    def put(self, x, y, colour, alpha=255):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            self.rows[y][x * 4:x * 4 + 4] = bytes((*colour, alpha))

    def rect(self, rect, colour, alpha=255):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.put(x, y, colour, alpha)


def plate_face(canvas, rect, salt, tone=0):
    """Layered stone plates: bevels on the band edges, dark seams, stone noise."""
    x0, y0, x1, y1 = rect
    base = tint(STONE, tone)
    for y in range(y0, y1):
        for x in range(x0, x1):
            canvas.put(x, y, stone(x, y, salt, base))
    for index, (band_y0, band_y1) in enumerate(BANDS):
        if band_y1 >= y1:
            continue
        for x in range(x0, x1):
            canvas.put(x, band_y1, stone(x, band_y1, salt + index, SEAM))
            canvas.put(x, band_y0, stone(x, band_y0, salt, tint(base, 16)))
            canvas.put(x, band_y1 - 1, stone(x, band_y1 - 1, salt, tint(base, -12)))
    for x in range(x0, x1):
        canvas.put(x, y0, stone(x, y0, salt, tint(base, 20)))
    for y in range(y0, y1):
        canvas.put(x0, y, stone(x0, y, salt, tint(base, 10)))
        canvas.put(x1 - 1, y, stone(x1 - 1, y, salt, tint(base, -14)))


def paint_common(canvas):
    plate_face(canvas, FRONT, salt=1)
    for rect, delta in ((SIDE_RIGHT, -18), (SIDE_LEFT, -24)):
        canvas.rect(rect, STONE_DARK)
        for y in range(rect[1], rect[3]):
            canvas.put(rect[0], y, stone(rect[0], y, 11, tint(STONE_DARK, delta)))
    plate_face(canvas, BACK, salt=23, tone=-6)
    for x in range(TOP[0], TOP[2]):
        canvas.put(x, TOP[1], stone(x, TOP[1], 5, STONE_LIGHT))
    for x in range(BOTTOM[0], BOTTOM[2]):
        canvas.put(x, BOTTOM[1], stone(x, BOTTOM[1], 5, SEAM))
    for rect in HANDLE:
        canvas.rect(rect, GRIP)
        for y in range(rect[1], rect[3]):
            for x in range(rect[0], rect[2]):
                canvas.put(x, y, stone(x, y, 31, GRIP))
    for y in range(HANDLE[3][1], HANDLE[3][3]):
        canvas.put(HANDLE[3][0], y, stone(HANDLE[3][0], y, 31, GRIP_LIGHT))
    for y in range(HANDLE[0][1], HANDLE[0][3]):
        for x in range(HANDLE[0][0], HANDLE[0][2]):
            canvas.put(x, y, stone(x, y, 33, GRIP_LIGHT))
    for x in range(HANDLE[2][0], HANDLE[2][2]):
        canvas.put(x, HANDLE[2][1], stone(x, HANDLE[2][1], 33, GRIP_LIGHT))


def build_base(with_recess):
    canvas = Canvas()
    paint_common(canvas)
    if with_recess:
        x0, y0, x1, y1 = CENTRE
        for y in range(y0, y1):
            for x in range(x0, x1):
                canvas.put(x, y, stone(x, y, 41, tint(STONE, -14)))
        for x in range(x0, x1):
            canvas.put(x, y0, stone(x, y0, 43, SEAM))
            canvas.put(x, y1 - 1, stone(x, y1 - 1, 41, tint(STONE, 18)))
        for y in range(y0, y1):
            canvas.put(x0, y, stone(x0, y, 43, SEAM))
            canvas.put(x1 - 1, y, stone(x1 - 1, y, 41, tint(STONE, 14)))
    return canvas


def build_overlay():
    canvas = Canvas()
    fx0, fy0, fx1, fy1 = FRONT
    cx0, cy0, cx1, cy1 = CENTRE
    for y in range(fy0, fy1):
        for x in range(fx0, fx1):
            if cx0 <= x < cx1 and cy0 <= y < cy1:
                continue
            if x == fx0 or y == fy0:
                colour = stone(x, y, 51, RIM_LIGHT)
            elif x == cx0 - 1 or y == cy0 - 1:
                colour = stone(x, y, 53, RIM_SHADOW)
            else:
                colour = stone(x, y, 55, RIM)
            canvas.put(x, y, colour)

    def rivet(x, y):
        canvas.put(x, y, AMBER_LIGHT)
        canvas.put(x + 1, y, AMBER)
        canvas.put(x, y + 1, AMBER)
        canvas.put(x + 1, y + 1, AMBER_DARK)

    for x, y in ((fx0, fy0), (fx1 - 2, fy0), (fx0, fy1 - 2), (fx1 - 2, fy1 - 2)):
        rivet(x, y)
    middle = (cy0 + cy1) // 2 - 1
    canvas.put(fx0, middle, AMBER)
    canvas.put(fx1 - 1, middle, AMBER)
    return canvas


def write_png(path, canvas):
    def chunk(kind, payload):
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)

    raw = bytearray()
    for row in canvas.rows:
        raw.append(0)
        raw += row
    data = SIG
    data += chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    data += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def main():
    for name, canvas in (
        ("plated_shield_base.png", build_base(True)),
        ("plated_shield_base_nopattern.png", build_base(False)),
        ("plated_shield_overlay.png", build_overlay()),
    ):
        write_png(OUT / name, canvas)
        print("wrote " + (OUT / name).relative_to(ROOT).as_posix())


if __name__ == "__main__":
    main()

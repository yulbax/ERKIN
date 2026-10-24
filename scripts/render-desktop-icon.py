#!/usr/bin/env python3
import itertools
import struct
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
AAPT = "{http://schemas.android.com/aapt}"
GRADIENT_IDS = itertools.count()
REPO = Path(__file__).resolve().parent.parent
LAYERS = [
    REPO / "app/src/main/res/drawable/ic_launcher_background.xml",
    REPO / "app/src/main/res/drawable/ic_launcher_foreground.xml",
]
ICO_SIZES = [16, 20, 24, 32, 40, 48, 64, 96, 128, 256]
WINDOW_ICON_SIZE = 256
OUT_ICO = REPO / "desktop/icons/erkin.ico"
OUT_ICNS = REPO / "desktop/icons/erkin.icns"
ICNS_TYPES = [
    ("icp4", 16), ("icp5", 32), ("ic11", 32), ("icp6", 64), ("ic12", 64), ("ic07", 128),
    ("ic08", 256), ("ic13", 256), ("ic09", 512), ("ic14", 512), ("ic10", 1024),
]
MAC_CONTENT = 824 / 1024
MAC_CORNER = 185.4 / 824
OUT_PNG = REPO / "desktop/src/main/resources/erkin-icon.png"
LAYER_DP = 108.0
VISIBLE_DP = 72.0


def attr(element, name, default=None):
    return element.get(ANDROID + name, default)


def paint(color):
    value = color.lstrip("#")
    if len(value) == 8:
        return "#" + value[2:], int(value[:2], 16) / 255
    return "#" + value, 1.0


def fill_gradient(path):
    for holder in path:
        if holder.tag != AAPT + "attr" or holder.get("name") != "android:fillColor":
            continue
        gradient = holder.find("gradient")
        if gradient is None or attr(gradient, "type", "linear") != "linear":
            continue
        gradient_id = "gradient{}".format(next(GRADIENT_IDS))
        stops = []
        for item in gradient.findall("item"):
            color, opacity = paint(attr(item, "color"))
            stops.append('<stop offset="{}" stop-color="{}" stop-opacity="{:.4f}"/>'.format(
                attr(item, "offset"), color, opacity))
        defs = ('<defs><linearGradient id="{}" gradientUnits="userSpaceOnUse" x1="{}" y1="{}" x2="{}" y2="{}">'
                '{}</linearGradient></defs>').format(
            gradient_id, attr(gradient, "startX"), attr(gradient, "startY"),
            attr(gradient, "endX"), attr(gradient, "endY"), "".join(stops))
        return defs, "url(#{})".format(gradient_id), 1.0
    return None


def svg_children(element):
    out = []
    for child in element:
        tag = child.tag.split("}")[-1]
        if tag == "group":
            transform = "translate({} {}) scale({} {})".format(
                attr(child, "translateX", "0"), attr(child, "translateY", "0"),
                attr(child, "scaleX", "1"), attr(child, "scaleY", "1"),
            )
            out.append('<g transform="{}">{}</g>'.format(transform, "".join(svg_children(child))))
        elif tag == "path":
            gradient = fill_gradient(child)
            if gradient:
                defs, fill, fill_opacity = gradient
                out.append(defs)
            else:
                fill, fill_opacity = paint(attr(child, "fillColor", "#00000000"))
            parts = ['d="{}"'.format(attr(child, "pathData")), 'fill="{}"'.format(fill),
                     'fill-opacity="{:.4f}"'.format(fill_opacity)]
            stroke = attr(child, "strokeColor")
            if stroke:
                stroke_color, stroke_opacity = paint(stroke)
                parts += ['stroke="{}"'.format(stroke_color), 'stroke-opacity="{:.4f}"'.format(stroke_opacity),
                          'stroke-width="{}"'.format(attr(child, "strokeWidth", "1"))]
            out.append("<path {}/>".format(" ".join(parts)))
    return out


def layer(path):
    root = ET.parse(path).getroot()
    width = float(attr(root, "viewportWidth"))
    height = float(attr(root, "viewportHeight"))
    scale = "scale({} {})".format(LAYER_DP / width, LAYER_DP / height)
    return '<g transform="{}">{}</g>'.format(scale, "".join(svg_children(root)))


def launcher_svg():
    inset = (LAYER_DP - VISIBLE_DP) / 2
    center = LAYER_DP / 2
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="{0} {0} {1} {1}">'
        '<clipPath id="mask"><circle cx="{2}" cy="{2}" r="{3}"/></clipPath>'
        '<g clip-path="url(#mask)">{4}</g></svg>'
    ).format(inset, VISIBLE_DP, center, VISIBLE_DP / 2, "".join(layer(p) for p in LAYERS))


def mac_svg():
    size = VISIBLE_DP / MAC_CONTENT
    origin = (LAYER_DP - size) / 2
    inset = (LAYER_DP - VISIBLE_DP) / 2
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="{0} {0} {1} {1}">'
        '<clipPath id="mask"><rect x="{2}" y="{2}" width="{3}" height="{3}" rx="{4}"/></clipPath>'
        '<g clip-path="url(#mask)">{5}</g></svg>'
    ).format(origin, size, inset, VISIBLE_DP, VISIBLE_DP * MAC_CORNER, "".join(layer(p) for p in LAYERS))


def write_icns(entries, target):
    chunks = b"".join(kind.encode() + struct.pack(">I", len(data) + 8) + data for kind, data in entries)
    target.write_bytes(b"icns" + struct.pack(">I", len(chunks) + 8) + chunks)


def render(svg, size, target):
    subprocess.run(["rsvg-convert", "-w", str(size), "-h", str(size), "-o", str(target), str(svg)], check=True)


def main():
    with tempfile.TemporaryDirectory() as work:
        work = Path(work)
        svg = work / "icon.svg"
        svg.write_text(launcher_svg())
        sizes = []
        for size in ICO_SIZES:
            png = work / "icon-{}.png".format(size)
            render(svg, size, png)
            sizes.append(str(png))
        OUT_ICO.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(["magick", *sizes, str(OUT_ICO)], check=True)
        OUT_PNG.parent.mkdir(parents=True, exist_ok=True)
        render(svg, WINDOW_ICON_SIZE, OUT_PNG)
        mac = work / "mac.svg"
        mac.write_text(mac_svg())
        entries = []
        for kind, size in ICNS_TYPES:
            png = work / "mac-{}.png".format(size)
            if not png.exists():
                render(mac, size, png)
            entries.append((kind, png.read_bytes()))
        write_icns(entries, OUT_ICNS)
    print("wrote", OUT_ICO.relative_to(REPO), OUT_ICNS.relative_to(REPO), "and", OUT_PNG.relative_to(REPO))


if __name__ == "__main__":
    sys.exit(main())

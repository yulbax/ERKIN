#!/usr/bin/env python3
import hashlib
import io
import urllib.request
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.ttLib.scaleUpem import scale_upem
from fontTools.ttLib.tables import otTables
from fontTools.ttLib.tables._c_m_a_p import cmap_format_12

REPO = Path(__file__).resolve().parent.parent
TEXT_FONTS = REPO / "ui/src/commonMain/composeResources/font"
OUT = REPO / "desktop/fonts"
WEIGHTS = ["google_sans_regular.ttf", "google_sans_medium.ttf", "google_sans_bold.ttf"]
FLAGS_URL = "https://github.com/mozilla/twemoji-colr/releases/download/v0.7.0/Twemoji.Mozilla.ttf"
FLAGS_SHA256 = "6d90152ee0d29e82fe2a87793af5aa4b7ad13e6538360889e141e81ed299ee8e"
REGIONAL_INDICATORS = range(0x1F1E6, 0x1F200)
PREFIX = "flag."


def download_flags():
    data = urllib.request.urlopen(FLAGS_URL).read()
    actual = hashlib.sha256(data).hexdigest()
    if actual != FLAGS_SHA256:
        raise SystemExit(f"Checksum mismatch for {FLAGS_URL}: {actual}")
    return data


def flags_font(data, units_per_em):
    font = TTFont(io.BytesIO(data))
    options = subset.Options()
    options.layout_features = ["*"]
    options.drop_tables += ["FFTM"]
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=REGIONAL_INDICATORS)
    subsetter.subset(font)
    scale_upem(font, units_per_em)
    rename_glyphs(font)
    return font


def rename_glyphs(font):
    order = font.getGlyphOrder()
    names = {glyph: PREFIX + glyph for glyph in order if glyph != ".notdef"}
    rename = lambda glyph: names.get(glyph, glyph)
    for tag in ("glyf", "hmtx", "cmap", "COLR", "GSUB"):
        font[tag]
    font.setGlyphOrder([rename(glyph) for glyph in order])
    glyf = font["glyf"]
    glyf.glyphs = {rename(name): glyph for name, glyph in glyf.glyphs.items()}
    glyf.glyphOrder = font.getGlyphOrder()
    for glyph in glyf.glyphs.values():
        if glyph.isComposite():
            for component in glyph.components:
                component.glyphName = rename(component.glyphName)
    hmtx = font["hmtx"]
    hmtx.metrics = {rename(name): metrics for name, metrics in hmtx.metrics.items()}
    for table in font["cmap"].tables:
        table.cmap = {code: rename(glyph) for code, glyph in table.cmap.items()}
    colr = font["COLR"]
    colr.ColorLayers = {
        rename(name): [type(layer)(rename(layer.name), layer.colorID) for layer in layers]
        for name, layers in colr.ColorLayers.items()
    }
    for lookup in font["GSUB"].table.LookupList.Lookup:
        for table in lookup.SubTable:
            if lookup.LookupType == 1:
                table.mapping = {rename(src): rename(dst) for src, dst in table.mapping.items()}
            elif lookup.LookupType == 4:
                table.ligatures = {
                    rename(first): [ligature(rename(lig.LigGlyph), [rename(c) for c in lig.Component]) for lig in ligs]
                    for first, ligs in table.ligatures.items()
                }
            else:
                raise SystemExit(f"Unexpected GSUB lookup type {lookup.LookupType} in the flag font")


def ligature(glyph, components):
    result = otTables.Ligature()
    result.LigGlyph = glyph
    result.Component = components
    result.CompCount = len(components) + 1
    return result


def add_flags(text, flags):
    added = [glyph for glyph in flags.getGlyphOrder() if glyph.startswith(PREFIX)]
    text["glyf"]
    text["hmtx"]
    text.setGlyphOrder(text.getGlyphOrder() + added)
    text["glyf"].glyphOrder = text.getGlyphOrder()
    for glyph in added:
        text["glyf"].glyphs[glyph] = flags["glyf"].glyphs[glyph]
        text["hmtx"].metrics[glyph] = flags["hmtx"].metrics[glyph]
    add_codepoints(text, flags.getBestCmap())
    add_substitutions(text["GSUB"].table, flags["GSUB"].table)
    text["COLR"] = flags["COLR"]
    text["CPAL"] = flags["CPAL"]
    glyph_classes = text["GDEF"].table.GlyphClassDef if "GDEF" in text else None
    if glyph_classes is not None:
        for glyph in added:
            glyph_classes.classDefs.setdefault(glyph, 1)


def add_codepoints(text, codepoints):
    unicode_tables = [table for table in text["cmap"].tables if table.isUnicode()]
    full = [table for table in unicode_tables if table.format == 12]
    if not full:
        table = cmap_format_12(12)
        table.platformID, table.platEncID, table.language = 3, 10, 0
        table.cmap = dict(next(t for t in unicode_tables if t.format == 4).cmap)
        text["cmap"].tables.append(table)
        full = [table]
    for table in full:
        table.cmap.update(codepoints)


def add_substitutions(gsub, flag_gsub):
    offset = len(gsub.LookupList.Lookup)
    gsub.LookupList.Lookup.extend(flag_gsub.LookupList.Lookup)
    gsub.LookupList.LookupCount = len(gsub.LookupList.Lookup)
    features = {}
    for record in flag_gsub.FeatureList.FeatureRecord:
        features.setdefault(record.FeatureTag, set()).update(i + offset for i in record.Feature.LookupListIndex)
    existing = {record.FeatureTag for record in gsub.FeatureList.FeatureRecord}
    missing = set(features) - existing
    if missing:
        raise SystemExit(f"The text font has no {sorted(missing)} feature to carry the flag ligatures")
    for record in gsub.FeatureList.FeatureRecord:
        if record.FeatureTag in features:
            record.Feature.LookupListIndex = sorted(set(record.Feature.LookupListIndex) | features[record.FeatureTag])
            record.Feature.LookupCount = len(record.Feature.LookupListIndex)


def main():
    data = download_flags()
    OUT.mkdir(parents=True, exist_ok=True)
    for name in WEIGHTS:
        text = TTFont(TEXT_FONTS / name)
        add_flags(text, flags_font(data, text["head"].unitsPerEm))
        text.save(OUT / name)
        print(f"{OUT / name}: {(OUT / name).stat().st_size} bytes")


if __name__ == "__main__":
    main()

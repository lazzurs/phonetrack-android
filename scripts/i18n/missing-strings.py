#!/usr/bin/env python3
"""Lists the strings a translation still lacks, and checks it (see TRANSLATING.md).

    python3 scripts/i18n/missing-strings.py de-rDE
    python3 scripts/i18n/missing-strings.py --same-as-english de-rDE

The argument is the part of the folder name after "values-" (de-rDE for values-de-rDE,
b+sr+Latn for values-b+sr+Latn). The script:

  * checks that values-<code>/strings.xml is well-formed XML,
  * checks that every translated string has the same placeholders (%1$s, %d...) as the
    English one,
  * prints, ready to copy into that file, the English strings it does not have yet, with
    the comment that explains a string where there is one.

With --same-as-english it also prints the strings whose text is still the English text:
the original app's Crowdin export copied English into many languages for strings nobody
had translated.

Needs only Python 3.8 or newer. The unit test ResourceFormatStringsTest runs the same
placeholder check in CI on every pull request, and it is the one that counts.
"""

import os
import re
import sys
import xml.etree.ElementTree as ET

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "res")
# the same syntax as java.util.Formatter: group 1 = explicit index, group 2 = conversion
SPEC = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?([a-zA-Z%])")


def parse(path):
    parser = ET.XMLParser(target=ET.TreeBuilder(insert_comments=True))
    return ET.parse(path, parser).getroot()


def text(element):
    return "".join(element.itertext())


def specs(value):
    """[(explicit index or None, conversion)], leaving out %%"""
    return [(int(m.group(1)) if m.group(1) else None, m.group(2).lower())
            for m in SPEC.finditer(value) if m.group(2) != "%"]


def arguments(value):
    """argument index -> conversion, as java.util.Formatter numbers them"""
    args = {}
    following = 1
    for index, conversion in specs(value):
        if index is None:
            index = following
            following += 1
        args[index] = conversion
    return args


def texts(element):
    """name (plural items: name#quantity) -> text, for the format check"""
    name = element.get("name")
    if element.tag == "plurals":
        return {name + "#" + item.get("quantity"): text(item) for item in element.findall("item")}
    if element.get("formatted") == "false":
        return {}
    return {name: text(element)}


def default_for(defaults, key):
    if key in defaults:
        return defaults[key]
    if "#" in key:
        # plural forms differ between languages: compare any form with the English "other"
        return defaults.get(key.split("#")[0] + "#other")
    return None


def placeholder_problems(default_root, root):
    defaults = {}
    for e in default_root:
        if e.tag in ("string", "plurals"):
            defaults.update(texts(e))
    problems = []
    for e in root:
        if e.tag not in ("string", "plurals"):
            continue
        items = e.findall("item") if e.tag == "plurals" else [e]
        for item in items:
            value = text(item).strip()
            if len(value) > 1 and value.startswith('"') and value.endswith('"'):
                continue  # a quoted string keeps its apostrophes
            key = e.get("name") + ("#" + item.get("quantity") if e.tag == "plurals" else "")
            value = re.sub(r"<[^>]*>", "", value)  # quotes inside a link's HTML are fine
            if re.search(r"(?<!\\)'", value):
                problems.append("%s: write an apostrophe as \\' (the app does not build otherwise)" % key)
            if re.search(r'(?<!\\)"', value):
                problems.append('%s: write a double quote as \\" (Android leaves it out otherwise)' % key)
        for key, value in texts(e).items():
            found = specs(value)
            if len(found) > 1 and any(index is None for index, _ in found):
                problems.append("%s: with several placeholders, number them (%%1$s, %%2$d)" % key)
            base = default_for(defaults, key)
            if base is None:
                continue
            expected, actual = arguments(base), arguments(value)
            # a plural form may leave the count out ("one day"), a plain string may not
            ok = set(actual.items()) <= set(expected.items()) if "#" in key else expected == actual
            if not ok:
                problems.append("%s: placeholders %s, but the English text has %s" % (
                    key, sorted(actual.items()), sorted(expected.items())))
    return problems


def snippet(element, comment):
    element = ET.fromstring(ET.tostring(element, encoding="unicode"))
    element.tail = None
    for attribute in list(element.attrib):
        if attribute.startswith("{"):  # tools:ignore and the like are not for translations
            del element.attrib[attribute]
    lines = []
    if comment is not None:
        lines.append("    <!--%s-->" % comment.text)
    lines.append("    " + ET.tostring(element, encoding="unicode").strip())
    return "\n".join(lines)


def main(argv):
    same_as_english = "--same-as-english" in argv
    codes = [a for a in argv if not a.startswith("--")]
    if len(codes) != 1:
        print(__doc__.strip())
        return 2
    code = codes[0]
    if code.startswith("values-"):
        code = code[len("values-"):]
    default_root = parse(os.path.join(RES, "values", "strings.xml"))
    path = os.path.join(RES, "values-" + code, "strings.xml")
    shown = os.path.relpath(path)
    if os.path.exists(path):
        try:
            root = parse(path)
        except ET.ParseError as e:
            print("%s is not valid XML: %s" % (shown, e))
            return 1
    else:
        print("%s does not exist yet: every string below is to translate.\n" % shown)
        root = ET.Element("resources")

    status = 0
    problems = placeholder_problems(default_root, root)
    if problems:
        status = 1
        print("Placeholders to fix in %s:" % shown)
        for p in problems:
            print("  " + p)
        print()

    have = {e.get("name"): e for e in root if e.tag in ("string", "plurals")}
    missing, unchanged = [], []
    comment = None
    for e in default_root:
        if e.tag is ET.Comment:
            comment = e
            continue
        if e.tag in ("string", "plurals") and e.get("translatable") != "false":
            name = e.get("name")
            if name not in have:
                missing.append(snippet(e, comment))
            elif e.tag == "string" and text(have[name]).strip() == text(e).strip():
                unchanged.append(name)
        comment = None

    print("%d strings to translate (missing from %s):" % (len(missing), shown))
    print("\n".join(missing))
    if same_as_english:
        print("\n%d strings in %s still have the English text (some, such as \"GPS\", may be right as they are):"
              % (len(unchanged), shown))
        for name in unchanged:
            print("  " + name)
    return status


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

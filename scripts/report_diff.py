#!/usr/bin/env python3
"""Compare two SignalDeck device reports (report.json, W6 plan item 9) field by field.

    python3 scripts/report_diff.py <old report.json or folder> <new report.json or folder>

Prints what changed: the phone, the draft profile (value and confidence of every field), the summary lines added or
gone, and the sections added, gone or changed in size. Exit status 0 when nothing in the phone, the draft or the
summary changed (section sizes alone do not count: logs differ from run to run), 1 otherwise, 2 on bad input.
Standard library only.
"""
import json
import os
import sys


def load(path):
    if os.path.isdir(path):
        path = os.path.join(path, "report.json")
    with open(path, encoding="utf-8") as f:
        r = json.load(f)
    if r.get("schema") != 1:
        raise ValueError(f"{path}: schema {r.get('schema')!r}, this script reads schema 1")
    return r


def diff(a, b):
    """The differences as printable lines, and whether any of them counts."""
    out, counts = [], False
    if a.get("signaldeck") != b.get("signaldeck"):
        out.append(f"SignalDeck: {a.get('signaldeck')} -> {b.get('signaldeck')}")
    for k in sorted(set(a.get("phone", {})) | set(b.get("phone", {}))):
        va, vb = a["phone"].get(k), b["phone"].get(k)
        if va != vb:
            out.append(f"phone.{k}: {va!r} -> {vb!r}"); counts = True
    da, db = a.get("draft", {}), b.get("draft", {})
    for k in list(da) + [k for k in db if k not in da]:
        fa, fb = da.get(k), db.get(k)
        if fa is None:
            out.append(f"draft.{k}: new: {fb['value']} ({fb['confidence']})"); counts = True
        elif fb is None:
            out.append(f"draft.{k}: gone (was {fa['value']} ({fa['confidence']}))"); counts = True
        elif (fa["value"], fa["confidence"]) != (fb["value"], fb["confidence"]):
            out.append(f"draft.{k}: {fa['value']} ({fa['confidence']}) -> {fb['value']} ({fb['confidence']})"); counts = True
    sa, sb = a.get("summary", []), b.get("summary", [])
    for line in sa:
        if line not in sb:
            out.append(f"summary -  {line}"); counts = True
    for line in sb:
        if line not in sa:
            out.append(f"summary +  {line}"); counts = True
    ta = {s["title"]: s["lines"] for s in a.get("sections", [])}
    tb = {s["title"]: s["lines"] for s in b.get("sections", [])}
    for t in ta:
        if t not in tb:
            out.append(f"section gone: {t}")
    for t in tb:
        if t not in ta:
            out.append(f"section new: {t} ({tb[t]} lines)")
        elif ta[t] != tb[t]:
            out.append(f"section size: {t}: {ta[t]} -> {tb[t]} lines")
    return out, counts


def main(argv):
    if len(argv) != 3:
        print(__doc__.strip().split("\n\n")[1], file=sys.stderr)
        return 2
    try:
        a, b = load(argv[1]), load(argv[2])
    except (OSError, ValueError) as e:
        print(e, file=sys.stderr)
        return 2
    lines, counts = diff(a, b)
    print("\n".join(lines) if lines else "no differences")
    return 1 if counts else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

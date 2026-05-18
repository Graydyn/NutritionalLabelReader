#!/usr/bin/env python3
"""
Tag rows in tracker/src/main/assets/nutrition_all.csv with a foundational flag.

Reads the curated foundation list from ~/Downloads/nutrition_foundation.csv,
normalizes names (strip + casefold), then rewrites nutrition_all.csv in place
adding a sixth column 'foundational' with value '1' if the row's name is in
the foundation set, else '0'.

If nutrition_all.csv already has the 'foundational' column, it is replaced
based on the current foundation set (idempotent).

Inputs (read-only):
  ~/Downloads/nutrition_foundation.csv     (header: name,calories_kcal,protein_g,fat_g,carbs_g)
  tracker/src/main/assets/nutrition_all.csv

Output:
  tracker/src/main/assets/nutrition_all.csv (overwritten in place; header gains 'foundational')
"""

from __future__ import annotations

import csv
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
FOUNDATION_SRC = Path.home() / "Downloads" / "nutrition_foundation.csv"
ALL_CSV = REPO_ROOT / "tracker" / "src" / "main" / "assets" / "nutrition_all.csv"

BASE_HEADER = ["name", "calories_kcal", "protein_g", "fat_g", "carbs_g"]
NEW_HEADER = BASE_HEADER + ["foundational"]


def load_foundation_names(path: Path) -> set[str]:
    with path.open("r", newline="", encoding="utf-8") as fin:
        reader = csv.reader(fin)
        header = next(reader)
        if header != BASE_HEADER:
            print(
                f"ERROR: unexpected foundation header {header!r}, expected {BASE_HEADER!r}",
                file=sys.stderr,
            )
            sys.exit(1)
        return {row[0].strip().casefold() for row in reader if row}


def main() -> int:
    if not FOUNDATION_SRC.exists():
        print(f"ERROR: foundation CSV not found: {FOUNDATION_SRC}", file=sys.stderr)
        return 1
    if not ALL_CSV.exists():
        print(f"ERROR: nutrition_all.csv not found: {ALL_CSV}", file=sys.stderr)
        return 1

    foundation_names = load_foundation_names(FOUNDATION_SRC)
    print(f"Loaded {len(foundation_names)} foundation names from {FOUNDATION_SRC}")

    with ALL_CSV.open("r", newline="", encoding="utf-8") as fin:
        reader = csv.reader(fin)
        header = next(reader)

        if header == BASE_HEADER:
            has_existing_flag = False
        elif header == NEW_HEADER:
            has_existing_flag = True
        else:
            print(
                f"ERROR: unexpected header in nutrition_all.csv: {header!r}\n"
                f"  expected {BASE_HEADER!r} or {NEW_HEADER!r}",
                file=sys.stderr,
            )
            return 1

        rows = list(reader)

    matched = 0
    out_rows: list[list[str]] = []
    for row in rows:
        if len(row) < 5:
            out_rows.append(row)
            continue
        name = row[0].strip().casefold()
        is_foundational = name in foundation_names
        if is_foundational:
            matched += 1
        new_row = row[:5] + ["1" if is_foundational else "0"]
        out_rows.append(new_row)

    with ALL_CSV.open("w", newline="", encoding="utf-8") as fout:
        writer = csv.writer(fout, quoting=csv.QUOTE_MINIMAL)
        writer.writerow(NEW_HEADER)
        writer.writerows(out_rows)

    print(f"Wrote {len(out_rows)} rows to {ALL_CSV.relative_to(REPO_ROOT)}")
    print(f"Marked foundational=1 on {matched} rows")
    if matched < len(foundation_names):
        print(
            f"WARN: {len(foundation_names) - matched} foundation names had no "
            f"matching row in nutrition_all.csv (possible name drift)"
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
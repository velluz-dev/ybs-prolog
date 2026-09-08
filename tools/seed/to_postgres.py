"""
Generate the Postgres seed from the MySQL one.

`data.sql` is written for MySQL and uses `INSERT IGNORE`, which is how the seed stays safe
to re-run on every startup against a database that persists. Postgres has no such syntax;
the equivalent is `ON CONFLICT DO NOTHING`, and there is no third form both accept. So one
file is generated from the other rather than maintained twice by hand.

    python tools/seed/to_postgres.py

`SeedFilesInSyncTest` runs this same transformation in memory and fails if the checked-in
Postgres file does not match, so the two cannot drift apart unnoticed.
"""

import pathlib
import re
import sys

RESOURCES = pathlib.Path(__file__).resolve().parents[2] / "backend" / "src" / "main" / "resources"
SOURCE = RESOURCES / "data.sql"
TARGET = RESOURCES / "data-postgres.sql"

HEADER = """--  GENERATED FILE - DO NOT EDIT.
--
--  Produced from data.sql by tools/seed/to_postgres.py. Edit data.sql and regenerate;
--  SeedFilesInSyncTest fails if this file and that transformation disagree.
--
--  The only difference is how the seed stays idempotent: MySQL says INSERT IGNORE,
--  Postgres says ON CONFLICT DO NOTHING, and nothing says both.

"""


def convert(sql: str) -> str:
    """MySQL seed text to Postgres seed text.

    Two edits and nothing else. Keeping the transformation this narrow is deliberate: a
    converter that rewrites values or reformats rows would be a second place bugs could
    enter the data, and the data is the one thing here that is not reproducible.
    """
    # INSERT IGNORE INTO x  ->  INSERT INTO x
    out = re.sub(r"^INSERT IGNORE INTO ", "INSERT INTO ", sql, flags=re.M)

    # ...and the idempotency moves to the end of each statement. Statements are the only
    # things in this file that end in a bare semicolon at end-of-line.
    out = re.sub(r";\s*$", "\nON CONFLICT DO NOTHING;", out, flags=re.M)
    return out


def main() -> int:
    if not SOURCE.exists():
        print(f"missing {SOURCE}", file=sys.stderr)
        return 1

    sql = SOURCE.read_text(encoding="utf-8")
    body = convert(sql)
    TARGET.write_text(HEADER + body, encoding="utf-8", newline="\n")

    # Counted on the SQL alone, not on HEADER + SQL. The header explains the transformation
    # and so contains both phrases; counting the whole file made it audit its own prose and
    # report ten guards for nine statements.
    inserts = body.count("INSERT INTO ")
    conflicts = body.count("ON CONFLICT DO NOTHING")
    print(f"{TARGET.name}: {len(body)//1024} KB, {inserts} inserts, {conflicts} guarded")
    if inserts != conflicts:
        print("  every INSERT must carry its own ON CONFLICT - they do not match", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

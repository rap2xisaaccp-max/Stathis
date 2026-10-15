"""Apply V4 APSLE indexes via direct Supabase host (not transaction pooler). DDL only."""
import re
from pathlib import Path

import psycopg2

root = Path(__file__).resolve().parents[1]
props = (root / "src/main/resources/application.properties").read_text(encoding="utf-8")
sql = (
    root / "src/main/resources/db/migration/V4__apsle_feedback_indexes_and_response_unique.sql"
).read_text(encoding="utf-8")


def prop(key: str) -> str | None:
    m = re.search(rf"^{re.escape(key)}=(.*)$", props, re.M)
    return m.group(1).strip() if m else None


user = prop("spring.datasource.username")
password = prop("spring.datasource.password")
ref = user.split(".", 1)[1]
candidates = [
    (f"db.{ref}.supabase.co", 5432),
    ("aws-1-ap-northeast-1.pooler.supabase.com", 5432),
]

statements = []
buf = []
for line in sql.splitlines():
    if line.strip().startswith("--"):
        continue
    buf.append(line)
    if ";" in line:
        stmt = "\n".join(buf).strip()
        if stmt:
            statements.append(stmt)
        buf = []

last_err: Exception | None = None
for host, port in candidates:
    try:
        print(f"TRY {host}:{port}")
        conn = psycopg2.connect(
            host=host,
            port=port,
            dbname="postgres",
            user=user,
            password=password,
            sslmode="require",
        )
        conn.autocommit = True
        cur = conn.cursor()
        cur.execute("SHOW transaction_read_only")
        print("transaction_read_only=", cur.fetchone()[0])
        for stmt in statements:
            print("EXEC", " ".join(stmt.split()[:6]), "...")
            cur.execute(stmt)
            print("  OK")
        cur.execute(
            """
            SELECT indexname FROM pg_indexes
            WHERE tablename IN ('feedback_intervention', 'feedback_response')
              AND (
                indexname LIKE 'uq_fr%'
                OR indexname LIKE 'idx_fi_student_delivered%'
                OR indexname LIKE 'idx_fi_experiment%'
                OR indexname LIKE 'idx_fi_classroom%'
                OR indexname LIKE 'idx_fr_student_created%'
              )
            ORDER BY 1
            """
        )
        print("=== NEW INDEXES ===")
        for row in cur.fetchall():
            print(row[0])
        cur.close()
        conn.close()
        print("SUCCESS")
        break
    except Exception as e:
        last_err = e
        print("FAIL", type(e).__name__, e)
else:
    raise SystemExit(str(last_err))

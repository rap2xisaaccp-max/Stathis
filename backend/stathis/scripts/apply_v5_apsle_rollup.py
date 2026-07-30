"""Apply V5 rollup table via direct Supabase host. DDL only; no deletes."""
import re
from pathlib import Path

import psycopg2

root = Path(__file__).resolve().parents[1]
props = (root / "src/main/resources/application.properties").read_text(encoding="utf-8")
sql = (root / "src/main/resources/db/migration/V5__apsle_arm_session_rollup.sql").read_text(
    encoding="utf-8"
)


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
        for stmt in statements:
            print("EXEC", " ".join(stmt.split()[:5]), "...")
            cur.execute(stmt)
            print("  OK")
        cur.execute(
            """
            SELECT to_regclass('public.adaptive_arm_session_rollup')
            """
        )
        print("table=", cur.fetchone()[0])
        cur.close()
        conn.close()
        print("SUCCESS")
        break
    except Exception as e:
        last_err = e
        print("FAIL", type(e).__name__, e)
else:
    raise SystemExit(str(last_err))

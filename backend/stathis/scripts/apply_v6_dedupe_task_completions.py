"""Apply V6 dedupe + unique index for task_completions. Writes result to debug-b7147e.log."""
from __future__ import annotations

import json
import time
from pathlib import Path

import psycopg2

LOG = Path(r"C:\Users\ASUS\Stathis\debug-b7147e.log")
SQL = Path(
    r"C:\Users\ASUS\Stathis\backend\stathis\src\main\resources\db\migration\V6__dedupe_task_completions_unique.sql"
)
STUDENT_ID = "26-6681-628"
TASK_TWO = "TASK-AF9BE34E-9815-4340-9DCB-17FBB243DFB5"


def emit(message: str, data: dict) -> None:
    line = {
        "sessionId": "b7147e",
        "hypothesisId": "B",
        "location": "apply_v6_dedupe",
        "message": message,
        "data": data,
        "timestamp": int(time.time() * 1000),
        "runId": "fix-db",
    }
    with LOG.open("a", encoding="utf-8") as f:
        f.write(json.dumps(line, ensure_ascii=False) + "\n")


def main() -> None:
    sql = SQL.read_text(encoding="utf-8")
    # Prefer direct/session pooler (5432); 6543 transaction pooler can be read-only for DDL/DML batches.
    conn = psycopg2.connect(
        host="aws-1-ap-northeast-1.pooler.supabase.com",
        port=5432,
        dbname="postgres",
        user="postgres.xhgzpmtsmgiamiqrxgfh",
        password="ItCITUCollegeCourse.CC2004",
        sslmode="require",
    )
    try:
        conn.set_session(readonly=False, autocommit=False)
        cur = conn.cursor()
        cur.execute("SET SESSION CHARACTERISTICS AS TRANSACTION READ WRITE")
        cur.execute(
            "SELECT COUNT(*) FROM task_completions WHERE student_id=%s AND task_id=%s",
            (STUDENT_ID, TASK_TWO),
        )
        before = cur.fetchone()[0]
        emit("before dedupe", {"taskTwoRowCount": before})

        cur.execute(sql)
        conn.commit()

        cur.execute(
            "SELECT COUNT(*) FROM task_completions WHERE student_id=%s AND task_id=%s",
            (STUDENT_ID, TASK_TWO),
        )
        after = cur.fetchone()[0]
        cur.execute(
            """
            SELECT indexname FROM pg_indexes
            WHERE tablename='task_completions' AND indexname='uq_task_completions_student_task'
            """
        )
        idx = [r[0] for r in cur.fetchall()]
        emit("after dedupe", {"taskTwoRowCount": after, "uniqueIndex": idx})
        print(f"OK before={before} after={after} index={idx}")
        cur.close()
    finally:
        conn.close()


if __name__ == "__main__":
    main()

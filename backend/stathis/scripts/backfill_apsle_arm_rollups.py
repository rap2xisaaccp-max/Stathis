"""One-time backfill of adaptive_arm_session_rollup from existing FI/FR. No deletes."""
import re
import uuid
from collections import defaultdict
from pathlib import Path

import psycopg2

root = Path(__file__).resolve().parents[1]
props = (root / "src/main/resources/application.properties").read_text(encoding="utf-8")


def prop(key: str) -> str | None:
    m = re.search(rf"^{re.escape(key)}=(.*)$", props, re.M)
    return m.group(1).strip() if m else None


def base_arm(raw: str | None) -> str:
    if not raw:
        return "ADAPTIVE"
    u = raw.strip().upper()
    return "STATIC" if u.startswith("STATIC") else "ADAPTIVE"


user = prop("spring.datasource.username")
password = prop("spring.datasource.password")
conn = psycopg2.connect(
    host="aws-1-ap-northeast-1.pooler.supabase.com",
    port=5432,
    dbname="postgres",
    user=user,
    password=password,
    sslmode="require",
)
conn.autocommit = True
cur = conn.cursor()

cur.execute(
    """
    SELECT i.student_id, i.session_id, i.classroom_id, i.experiment_arm,
           r.delta, r.success
    FROM feedback_response r
    JOIN feedback_intervention i ON i.physical_id = r.intervention_physical_id
    """
)
rows = cur.fetchall()
agg = defaultdict(lambda: {"n": 0, "succ": 0, "sum": 0.0, "sumsq": 0.0, "classroom": None})
for student_id, session_id, classroom_id, arm, delta, success in rows:
    key = (student_id, session_id, base_arm(arm))
    a = agg[key]
    a["n"] += 1
    a["succ"] += 1 if success else 0
    a["sum"] += float(delta)
    a["sumsq"] += float(delta) * float(delta)
    if classroom_id:
        a["classroom"] = classroom_id

upserted = 0
for (student_id, session_id, arm), a in agg.items():
    physical_id = "AASR-" + uuid.uuid4().hex.upper()
    cur.execute(
        """
        INSERT INTO adaptive_arm_session_rollup (
          adaptive_arm_session_rollup_id, physical_id, created_at, updated_at,
          student_id, session_id, classroom_id, base_arm,
          n_interventions, n_responses, successes, sum_delta, sum_delta_sq
        ) VALUES (
          %s, %s, NOW(), NOW(), %s, %s, %s, %s, %s, %s, %s, %s, %s
        )
        ON CONFLICT (student_id, session_id, base_arm) DO UPDATE SET
          n_interventions = EXCLUDED.n_interventions,
          n_responses = EXCLUDED.n_responses,
          successes = EXCLUDED.successes,
          sum_delta = EXCLUDED.sum_delta,
          sum_delta_sq = EXCLUDED.sum_delta_sq,
          classroom_id = COALESCE(EXCLUDED.classroom_id, adaptive_arm_session_rollup.classroom_id),
          updated_at = NOW()
        """,
        (
            str(uuid.uuid4()),
            physical_id,
            student_id,
            session_id,
            a["classroom"],
            arm,
            a["n"],
            a["n"],
            a["succ"],
            a["sum"],
            a["sumsq"],
        ),
    )
    upserted += 1

cur.execute("SELECT COUNT(*) FROM adaptive_arm_session_rollup")
print(f"backfilled_keys={upserted} rollup_rows={cur.fetchone()[0]}")
cur.close()
conn.close()

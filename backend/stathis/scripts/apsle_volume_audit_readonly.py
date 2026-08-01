"""Read-only APSLE volume audit against configured Postgres. Do not delete data."""
import re
from pathlib import Path

import psycopg2

props = Path(__file__).resolve().parents[1] / "src/main/resources/application.properties"
text = props.read_text(encoding="utf-8")


def prop(key: str) -> str | None:
    m = re.search(rf"^{re.escape(key)}=(.*)$", text, re.M)
    return m.group(1).strip() if m else None


url = prop("spring.datasource.url")
m = re.match(r"jdbc:postgresql://([^:/]+):(\d+)/([^?]+)", url or "")
if not m:
    raise SystemExit(f"Cannot parse JDBC URL: {url}")
host, port, db = m.group(1), int(m.group(2)), m.group(3)
user = prop("spring.datasource.username")
password = prop("spring.datasource.password")

conn = psycopg2.connect(
    host=host, port=port, dbname=db, user=user, password=password, sslmode="require"
)
conn.set_session(readonly=True, autocommit=True)
cur = conn.cursor()

print("=== TABLE COUNTS ===")
cur.execute(
    """
SELECT 'feedback_intervention' AS t, COUNT(*) FROM feedback_intervention
UNION ALL SELECT 'feedback_response', COUNT(*) FROM feedback_response
UNION ALL SELECT 'student_learning_profile', COUNT(*) FROM student_learning_profile
UNION ALL SELECT 'exercise_mastery', COUNT(*) FROM exercise_mastery
UNION ALL SELECT 'learning_profile_history', COUNT(*) FROM learning_profile_history
ORDER BY 1
"""
)
for row in cur.fetchall():
    print(f"{row[0]}: {row[1]}")

print("\n=== DUPLICATE RESPONSES BY intervention_physical_id ===")
cur.execute(
    """
SELECT intervention_physical_id, COUNT(*) c
FROM feedback_response GROUP BY 1 HAVING COUNT(*) > 1
ORDER BY c DESC LIMIT 50
"""
)
dups = cur.fetchall()
print(f"duplicate_groups={len(dups)}")
for row in dups[:10]:
    print(row)

print("\n=== VOLUME BY DAY (FI last 30) ===")
cur.execute(
    """
SELECT date_trunc('day', created_at) d, COUNT(*)
FROM feedback_intervention GROUP BY 1 ORDER BY 1 DESC NULLS LAST LIMIT 30
"""
)
for row in cur.fetchall():
    print(row)

print("\n=== TOP STUDENTS BY FI COUNT ===")
cur.execute(
    """
SELECT student_id, COUNT(*) c FROM feedback_intervention
GROUP BY 1 ORDER BY c DESC LIMIT 20
"""
)
for row in cur.fetchall():
    print(row)

print("\n=== FI/FR RATIO AND SESSION STATS ===")
cur.execute(
    """
SELECT
  (SELECT COUNT(*) FROM feedback_intervention) AS fi,
  (SELECT COUNT(*) FROM feedback_response) AS fr,
  (SELECT COUNT(DISTINCT session_id) FROM feedback_intervention) AS sessions,
  (SELECT COUNT(DISTINCT student_id) FROM feedback_intervention) AS students
"""
)
print(cur.fetchone())

cur.execute(
    """
SELECT AVG(cnt)::float, MAX(cnt), PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY cnt)
FROM (
  SELECT session_id, COUNT(*) cnt FROM feedback_intervention GROUP BY session_id
) s
"""
)
print("per_session_fi avg/max/p50:", cur.fetchone())

print("\n=== BUSIEST HOURS (7d) ===")
cur.execute(
    """
SELECT date_trunc('hour', created_at) h, COUNT(*)
FROM feedback_intervention
WHERE created_at > NOW() - INTERVAL '7 days'
GROUP BY 1 ORDER BY 2 DESC LIMIT 5
"""
)
for row in cur.fetchall():
    print(row)

cur.close()
conn.close()
print("\nDONE")

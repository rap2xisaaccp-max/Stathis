"""After V6 fix: login stud4 and complete Task Two; log API + DB results."""
from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from pathlib import Path

import bcrypt
import psycopg2

LOG = Path(r"C:\Users\ASUS\Stathis\debug-b7147e.log")
API = "https://stathis-backend-fresh.onrender.com/api"
EMAIL = "stud4@gmail.com"
PASSWORD = "Test123!"
STUDENT_ID = "26-6681-628"
TASK_TWO = "TASK-AF9BE34E-9815-4340-9DCB-17FBB243DFB5"
TPL_TWO = "EXERCISE-26-5350-352"


def emit(hypothesis_id: str, location: str, message: str, data: dict) -> None:
    line = {
        "sessionId": "b7147e",
        "hypothesisId": hypothesis_id,
        "location": location,
        "message": message,
        "data": data,
        "timestamp": int(time.time() * 1000),
        "runId": "post-fix-verify",
    }
    with LOG.open("a", encoding="utf-8") as f:
        f.write(json.dumps(line, ensure_ascii=False) + "\n")


def http_json(method: str, url: str, body: dict | None = None, token: str | None = None):
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(raw) if raw else raw
        except Exception:
            parsed = raw
        return e.code, parsed


def ensure_password() -> None:
    """If Test123! does not match stored hash, reset it (user-provided test password)."""
    conn = psycopg2.connect(
        host="aws-1-ap-northeast-1.pooler.supabase.com",
        port=5432,
        dbname="postgres",
        user="postgres.xhgzpmtsmgiamiqrxgfh",
        password="ItCITUCollegeCourse.CC2004",
        sslmode="require",
    )
    conn.set_session(readonly=False, autocommit=False)
    cur = conn.cursor()
    cur.execute("SELECT password_hash FROM users WHERE email=%s", (EMAIL,))
    row = cur.fetchone()
    if not row:
        emit("E", "db.users", "stud4 missing", {})
        cur.close()
        conn.close()
        return
    stored = row[0].encode("utf-8") if isinstance(row[0], str) else row[0]
    ok = False
    try:
        ok = bcrypt.checkpw(PASSWORD.encode("utf-8"), stored)
    except Exception as ex:
        emit("E", "db.users", "bcrypt check failed", {"error": str(ex)})
    emit("E", "db.users", "password check", {"matchesTest123": ok})
    if not ok:
        new_hash = bcrypt.hashpw(PASSWORD.encode("utf-8"), bcrypt.gensalt(rounds=10)).decode("utf-8")
        cur.execute("UPDATE users SET password_hash=%s WHERE email=%s", (new_hash, EMAIL))
        conn.commit()
        emit("E", "db.users", "password reset to Test123!", {"updated": True})
    cur.close()
    conn.close()


def db_snapshot(label: str) -> None:
    conn = psycopg2.connect(
        host="aws-1-ap-northeast-1.pooler.supabase.com",
        port=5432,
        dbname="postgres",
        user="postgres.xhgzpmtsmgiamiqrxgfh",
        password="ItCITUCollegeCourse.CC2004",
        sslmode="require",
    )
    conn.set_session(readonly=True, autocommit=True)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT COUNT(*) FROM task_completions
        WHERE student_id=%s AND task_id=%s
        """,
        (STUDENT_ID, TASK_TWO),
    )
    tc_count = cur.fetchone()[0]
    cur.execute(
        """
        SELECT exercise_completed, is_fully_completed FROM task_completions
        WHERE student_id=%s AND task_id=%s
        """,
        (STUDENT_ID, TASK_TWO),
    )
    tc = cur.fetchone()
    cur.execute(
        """
        SELECT attempts, score, reps, exercise_template_id, is_completed
        FROM score WHERE student_id=%s AND task_id=%s
        """,
        (STUDENT_ID, TASK_TWO),
    )
    score = cur.fetchone()
    emit(
        "D",
        f"db.snapshot.{label}",
        "task two persistence",
        {
            "completionCount": tc_count,
            "exerciseCompleted": None if tc is None else tc[0],
            "fullyCompleted": None if tc is None else tc[1],
            "score": None
            if score is None
            else {
                "attempts": score[0],
                "score": score[1],
                "reps": score[2],
                "exerciseTemplateId": score[3],
                "completed": score[4],
            },
        },
    )
    cur.close()
    conn.close()


def main() -> None:
    ensure_password()
    db_snapshot("before")
    status, auth = http_json("POST", f"{API}/auth/login", {"email": EMAIL, "password": PASSWORD})
    emit("E", "api.login", "login", {"status": status, "keys": list(auth.keys()) if isinstance(auth, dict) else []})
    if status != 200 or not isinstance(auth, dict):
        return
    token = auth.get("accessToken") or auth.get("token")
    status, prog = http_json("GET", f"{API}/student/tasks/{TASK_TWO}/progress", token=token)
    emit("B", "api.progress.two", "progress after dedupe", {"status": status, "body": prog})

    payload = {
        "reps": 8,
        "accuracy": 85.0,
        "timeTaken": 45000,
        "goalReps": 10,
        "caloriesBurned": 2.0,
        "exerciseType": "SQUATS",
        "classroomId": "ROOM-26-485",
    }
    status, body = http_json(
        "POST",
        f"{API}/student/tasks/{TASK_TWO}/exercise/{TPL_TWO}/complete",
        body=payload,
        token=token,
    )
    emit("C", "api.complete.two", "complete Two", {"status": status, "body": body})
    db_snapshot("after")


if __name__ == "__main__":
    main()

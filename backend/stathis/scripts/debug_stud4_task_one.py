"""Runtime probe: stud4 Task One complete + DB column/type checks. Writes NDJSON to debug-b7147e.log."""
from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from pathlib import Path

import psycopg2

LOG = Path(r"C:\Users\ASUS\Stathis\debug-b7147e.log")
API = "https://stathis-u8s6.onrender.com/api"
STUDENT = "stud4@gmail.com"
PASSWORD = "Test123!"
TASK_ONE = "TASK-03687C67-295C-4CC5-9CE4-648ACBE775A2"
TPL_ONE = "EXERCISE-26-2877-308"
TASK_TWO = "TASK-AF9BE34E-9815-4340-9DCB-17FBB243DFB5"
TPL_TWO = "EXERCISE-26-5350-352"
STUDENT_ID = "26-6681-628"


def emit(hypothesis_id: str, location: str, message: str, data: dict) -> None:
    line = {
        "sessionId": "b7147e",
        "hypothesisId": hypothesis_id,
        "location": location,
        "message": message,
        "data": data,
        "timestamp": int(time.time() * 1000),
        "runId": "pre-fix-api",
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
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, json.loads(raw) if raw else None, None
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(raw) if raw else None
        except Exception:
            parsed = raw
        return e.code, parsed, str(e)
    except Exception as e:
        return 0, None, str(e)


def main() -> None:
    # Hypothesis A: enum / column types for the two templates
    conn = psycopg2.connect(
        host="aws-1-ap-northeast-1.pooler.supabase.com",
        port=6543,
        dbname="postgres",
        user="postgres.xhgzpmtsmgiamiqrxgfh",
        password="ItCITUCollegeCourse.CC2004",
        sslmode="require",
    )
    conn.set_session(readonly=True, autocommit=True)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT column_name, data_type, udt_name
        FROM information_schema.columns
        WHERE table_schema='public' AND table_name='exercise_template'
          AND column_name IN ('exercise_type','exercise_difficulty')
        ORDER BY column_name
        """
    )
    cols = [{"column": r[0], "data_type": r[1], "udt": r[2]} for r in cur.fetchall()]
    emit("A", "db.exercise_template.columns", "column types", {"columns": cols})

    cur.execute(
        """
        SELECT physical_id, title, exercise_type::text, exercise_difficulty::text
        FROM exercise_template
        WHERE physical_id IN (%s, %s)
        """,
        (TPL_ONE, TPL_TWO),
    )
    templates = [
        {"physicalId": r[0], "title": r[1], "exerciseType": r[2], "difficulty": r[3]}
        for r in cur.fetchall()
    ]
    emit("A", "db.exercise_template.rows", "One/Two template rows", {"templates": templates})

    cur.execute(
        """
        SELECT physical_id, student_id, task_id, exercise_template_id, attempts, score, reps, is_completed
        FROM score
        WHERE student_id=%s AND task_id IN (%s, %s)
        """,
        (STUDENT_ID, TASK_ONE, TASK_TWO),
    )
    scores_before = [
        {
            "physicalId": r[0],
            "studentId": r[1],
            "taskId": r[2],
            "exerciseTemplateId": r[3],
            "attempts": r[4],
            "score": r[5],
            "reps": r[6],
            "completed": r[7],
        }
        for r in cur.fetchall()
    ]
    emit("D", "db.score.before", "scores before complete", {"rows": scores_before})

    cur.execute(
        """
        SELECT physical_id, student_id, task_id, exercise_completed, is_fully_completed
        FROM task_completions
        WHERE student_id=%s AND task_id IN (%s, %s)
        """,
        (STUDENT_ID, TASK_ONE, TASK_TWO),
    )
    tc_before = [
        {
            "physicalId": r[0],
            "studentId": r[1],
            "taskId": r[2],
            "exerciseCompleted": r[3],
            "fullyCompleted": r[4],
        }
        for r in cur.fetchall()
    ]
    emit("B", "db.task_completions.before", "completions before", {"rows": tc_before})
    cur.close()
    conn.close()

    # Login + API probes
    status, auth, err = http_json("POST", f"{API}/auth/login", {"email": STUDENT, "password": PASSWORD})
    emit("E", "api.auth.login", "login result", {"status": status, "err": err, "hasToken": bool(auth and auth.get("accessToken") or auth and auth.get("token"))})
    if status != 200 or not auth:
        return
    token = auth.get("accessToken") or auth.get("token") or auth.get("access_token")
    if not token and isinstance(auth, dict):
        # common alternate shapes
        token = auth.get("jwt") or (auth.get("data") or {}).get("accessToken")
    emit("E", "api.auth.tokenShape", "auth keys", {"keys": list(auth.keys()) if isinstance(auth, dict) else []})

    status, body, err = http_json("GET", f"{API}/templates/exercises/{TPL_ONE}", token=token)
    emit("A", "api.getTemplate.one", "GET Static template", {"status": status, "err": err, "body": body})

    status, body, err = http_json("GET", f"{API}/templates/exercises/{TPL_TWO}", token=token)
    emit("A", "api.getTemplate.two", "GET Squat template", {"status": status, "err": err, "body": body})

    status, body, err = http_json("GET", f"{API}/student/tasks/{TASK_ONE}/progress", token=token)
    emit("B", "api.progress.one", "GET progress One", {"status": status, "err": err, "body": body})

    payload = {
        "reps": 5,
        "accuracy": 80.0,
        "timeTaken": 30000,
        "goalReps": 10,
        "caloriesBurned": 1.0,
        "exerciseType": "STATIC_LUNGES",
        "classroomId": "ROOM-26-485",
    }
    status, body, err = http_json(
        "POST",
        f"{API}/student/tasks/{TASK_ONE}/exercise/{TPL_ONE}/complete",
        body=payload,
        token=token,
    )
    emit("C", "api.complete.one", "POST complete One", {"status": status, "err": err, "body": body})

    # After-complete DB check (new connection)
    conn = psycopg2.connect(
        host="aws-1-ap-northeast-1.pooler.supabase.com",
        port=6543,
        dbname="postgres",
        user="postgres.xhgzpmtsmgiamiqrxgfh",
        password="ItCITUCollegeCourse.CC2004",
        sslmode="require",
    )
    conn.set_session(readonly=True, autocommit=True)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT physical_id, task_id, exercise_template_id, attempts, score, reps, is_completed
        FROM score
        WHERE student_id=%s AND task_id=%s
        """,
        (STUDENT_ID, TASK_ONE),
    )
    scores_after = [
        {
            "physicalId": r[0],
            "taskId": r[1],
            "exerciseTemplateId": r[2],
            "attempts": r[3],
            "score": r[4],
            "reps": r[5],
            "completed": r[6],
        }
        for r in cur.fetchall()
    ]
    emit("D", "db.score.afterOne", "scores after complete One", {"rows": scores_after})
    cur.execute(
        """
        SELECT physical_id, task_id, exercise_completed, is_fully_completed
        FROM task_completions
        WHERE student_id=%s AND task_id=%s
        """,
        (STUDENT_ID, TASK_ONE),
    )
    tc_after = [
        {
            "physicalId": r[0],
            "taskId": r[1],
            "exerciseCompleted": r[2],
            "fullyCompleted": r[3],
        }
        for r in cur.fetchall()
    ]
    emit("D", "db.task_completions.afterOne", "completions after One", {"rows": tc_after})
    cur.close()
    conn.close()


if __name__ == "__main__":
    main()

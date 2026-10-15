# APSLE Phase 10 — E2E validation matrix

Classroom reference: `ROOM-26-485` · Student: `stud4@gmail.com` · Password: `Test123!`

For each exercise × modality path, mark PASS only after the closed loop is observed.

| Exercise | Detect error | Intervene (TEXT) | VISUAL | TTS | No spam | FR measured | Preferred-by-exercise update | Auto-complete / Finish | Attempt persists | Teacher Adaptive widgets | Result |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Push-up | | | | | | | | | | | PENDING |
| Squat | | | | | | | | | | | PENDING |
| Static Lunge | | | | | | | | | | | PENDING |
| Glute Bridge | | | | | | | | | | | PENDING |
| Lying Leg Raise | | | | | | | | | | | PENDING |

## Cold-start / UI checks

| Check | Result |
| --- | --- |
| New student shows Insufficient data / Learning (not fake 0%) | PENDING |
| RCT Adaptive vs Static hidden without `?research=1` | PENDING |
| Preferred Modality by Exercise card visible | PENDING |
| Charts use theme tokens (no blank fills) | PENDING |
| Practice sessions arm as `*_PRACTICE` | PENDING |

## API smoke

| Call | Expected | Result |
| --- | --- | --- |
| `POST /api/adaptive/recommend` | Catalog `messageCode` | PENDING |
| `POST /api/adaptive/batch` (retry same IDs) | Idempotent FI/FR | PENDING |
| `GET /api/adaptive/insights/{studentId}` | preferredByExercise + recent deltas | PENDING |
| Task complete exercise | 200, attempts increment | PENDING |

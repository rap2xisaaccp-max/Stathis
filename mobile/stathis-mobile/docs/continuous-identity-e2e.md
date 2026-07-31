# Continuous Exercise Identity — Manual E2E Matrix

Use a device/emulator build with face enrollment completed for the test student.

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Face-visible exercise (e.g. squat): verify → start → complete reps | Counting runs; no pause if same student stays in frame |
| 2 | Face-out exercise (e.g. glute bridge): verify (enrolls body) → start with face often out of view | Counting continues via skeletal body signature |
| 3 | After verify+start, second person takes over (face visible) | Session pauses; reps/time unchanged; message about face mismatch or multi-person |
| 4 | Second person enters frame (two faces) | Pause; multi-person message; Scan face to resume as verified student |
| 5 | Verified student leaves frame >5s | Pause; leave-frame message; soft recovery or Scan face |
| 6 | Student returns after pause (face) | TRUSTED; timer/reps resume from saved values (not zero) |
| 7 | Student returns after pause (face-out / skeletal rematch) | Soft recovery restores TRUSTED without wiping reps |
| 8 | Finish/Complete while paused | Submits performance with preserved reps; does not reset to zero |
| 9 | Cancel while paused | Session ends; next attempt starts clean |

## Notes

- Graded Score completion alone does not drive this path; identity runs on-device during the exercise camera session.
- Hard face scan uses dedicated `FaceAnalyzer`; mid-session integrity uses `IntegrityFrameAnalyzer` (pose + opportunistic face).

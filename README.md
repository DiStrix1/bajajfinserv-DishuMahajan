# Quiz Leaderboard System

Internship assignment — SRM / Bajaj Finserv Health Java Qualifier

---

## What this does

The assignment gives you a validator API that sends quiz scores across 10 polls. The catch is that the same score event can show up in multiple polls, so you can't just add everything up. This program polls the API, filters out the duplicates, totals up each participant's score, and submits the leaderboard once.

---

## Project structure

```
quiz-leaderboard/
├── pom.xml
├── README.md
└── src/
    └── main/
        └── java/
            └── com/
                └── quiz/
                    ├── Main.java                    # entry point, handles CLI flags
                    ├── api/
                    │   └── QuizApiClient.java       # GET and POST calls, retry logic
                    ├── service/
                    │   └── LeaderboardService.java  # dedup, aggregation, sorting
                    ├── util/
                    │   └── DeduplicationUtil.java   # builds the composite key
                    └── model/
                        ├── Event.java
                        └── LeaderboardEntry.java
```

---

## API

Base URL: `https://devapigw.vidalhealthtpa.com/srm-quiz-task`

**Polling:**
```
GET /quiz/messages?regNo=RA2311003050296&poll=0
```
Run this 10 times, poll=0 through poll=9. Wait 5 seconds between each call — the API requires it.

Sample response:
```json
{
  "regNo": "RA2311003050296",
  "setId": "SET_1",
  "pollIndex": 0,
  "events": [
    { "roundId": "R1", "participant": "Alice", "score": 10 },
    { "roundId": "R1", "participant": "Bob", "score": 20 }
  ]
}
```

**Submitting:**
```
POST /quiz/submit
Content-Type: application/json

{
  "regNo": "RA2311003050296",
  "leaderboard": [
    { "participant": "Bob", "totalScore": 120 },
    { "participant": "Alice", "totalScore": 100 }
  ]
}
```

A correct submission returns:
```json
{
  "isCorrect": true,
  "isIdempotent": true,
  "submittedTotal": 220,
  "expectedTotal": 220,
  "message": "Correct!"
}
```

---

## How duplicates are handled

Each event is identified by a composite key:
```
roundId + "|" + participant
```

The first time a key is seen it gets stored. Any later poll that returns the same key is ignored. Pretty straightforward — the tricky part is just remembering to do this before aggregating.

---

## How to run

**Prerequisites:** Java 11+, Maven 3.6+

```bash
# clone and build
git clone https://github.com/your-username/quiz-leaderboard.git
cd quiz-leaderboard
mvn clean package

# run without submitting (safe to test)
java -jar target/quiz-leaderboard-1.0-SNAPSHOT.jar RA2311003050296

# run and actually submit
java -jar target/quiz-leaderboard-1.0-SNAPSHOT.jar RA2311003050296 --submit

# if you need to resubmit and the lock file is blocking you
java -jar target/quiz-leaderboard-1.0-SNAPSHOT.jar RA2311003050296 --force-submit
```

**Without Maven:**
```bash
# Linux/Mac
javac -cp "target/classes:../json-20231013.jar" src/main/java/com/quiz/**/*.java
java  -cp "target/classes:../json-20231013.jar" com.quiz.Main RA2311003050296 --submit

# Windows (swap : for ;)
javac -cp "target/classes;../json-20231013.jar" src/main/java/com/quiz/**/*.java
java  -cp "target/classes;../json-20231013.jar" com.quiz.Main RA2311003050296 --submit
```

---

## Avoiding duplicate submissions

The API tracks how many times you've submitted (`attemptCount`), so submitting multiple times is bad. Two things prevent this:

1. By default the program just prints the leaderboard and stops — it won't POST anything unless you pass `--submit`.
2. After a successful submission it writes a local lock file (`submission-lock-RA2311003050296.json`). If that file exists, `--submit` does nothing. You need `--force-submit` to bypass it.

---

## What happens when things go wrong

- **HTTP 503 or network hiccup** — retries 3 times with a short backoff, then moves on
- **No `isCorrect` in the response** — prints a warning so you know to check
- **`attemptCount` comes back greater than 1** — also prints a warning
- **Garbled JSON** — logged and that poll is skipped; the rest continue normally

---

## Sample output

```
Polling 0...
  Added    R1|George  = 300
  Added    R2|Hannah  = 250
Polling 1...
  Added    R1|Ivan    = 185
...
Polling 4...
  Skipped  R1|George  (duplicate)
...
Polling 9...
  Skipped  R3|George  (duplicate)

===== LEADERBOARD =====
George               795
Hannah               750
Ivan                 745
=======================
Total Score: 2290

[SAFE MODE] Leaderboard ready. Pass --submit to send it.
```

---

## A few things worth noting

- Polls run one at a time because of the 5-second delay rule — no multithreading needed or attempted
- The lock file only lives on your machine, so running from a different machine won't be blocked
- Everything is in memory for the duration of one run, nothing is written to a database

---

## Author

**Name:** Dishu Mahajan  
**Reg No:** RA2311003050296  
**College:** SRM Institute of Science and Technology
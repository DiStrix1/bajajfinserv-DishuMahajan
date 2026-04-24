package com.quiz;

import com.quiz.api.QuizApiClient;
import com.quiz.model.LeaderboardEntry;
import com.quiz.service.LeaderboardService;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

public class Main {
    private static final String BASE_URL = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";
    private static final String DEFAULT_REG_NO = "2024CS101";
    private static final int TOTAL_POLLS = 10;
    private static final int DELAY_MS = 5000;

    public static void main(String[] args) throws Exception {
        RunConfig config = RunConfig.parse(args);

        QuizApiClient apiClient = new QuizApiClient(BASE_URL);
        LeaderboardService leaderboardService = new LeaderboardService(apiClient, TOTAL_POLLS, DELAY_MS);

        List<LeaderboardEntry> leaderboard = leaderboardService.buildLeaderboard(config.getRegNo());
        printLeaderboard(leaderboard);

        if (!config.isSubmitRequested()) {
            System.out.println("\nSubmission skipped (safe mode).");
            System.out.println("Use --submit once after verifying the leaderboard.");
            return;
        }

        Path lockFile = buildLockFilePath(config.getRegNo());
        if (Files.exists(lockFile) && !config.isForceSubmit()) {
            System.out.println("\nSubmission blocked: lock file already exists at " + lockFile.toAbsolutePath());
            System.out.println("This protects you from accidental re-submission.");
            System.out.println("If you intentionally want to resubmit, run with --force-submit.");
            return;
        }

        JSONObject submissionResult = leaderboardService.submitLeaderboard(config.getRegNo(), leaderboard);
        printSubmissionResult(submissionResult);
        warnIfSubmissionLooksSuspicious(submissionResult);
        persistSubmissionLock(lockFile, config.getRegNo(), submissionResult);
    }

    private static void printLeaderboard(List<LeaderboardEntry> leaderboard) {
        int totalScore = 0;

        System.out.println("\n===== LEADERBOARD =====");
        for (LeaderboardEntry entry : leaderboard) {
            System.out.printf("%-20s %d%n", entry.getParticipant(), entry.getTotalScore());
            totalScore += entry.getTotalScore();
        }
        System.out.println("=======================");
        System.out.println("Total Score: " + totalScore);
    }

    private static Path buildLockFilePath(String regNo) {
        return Path.of("submission-lock-" + regNo + ".json");
    }

    private static void printSubmissionResult(JSONObject submissionResult) {
        System.out.println("\n===== SUBMISSION RESULT =====");
        System.out.println(submissionResult.toString(2));
    }

    private static void warnIfSubmissionLooksSuspicious(JSONObject submissionResult) {
        if (!submissionResult.has("isCorrect")) {
            System.out.println("\nWarning: response does not include 'isCorrect'.");
            System.out.println("The server has not explicitly confirmed correctness.");
        }

        int attemptCount = submissionResult.optInt("attemptCount", -1);
        if (attemptCount > 1) {
            System.out.printf("Warning: attemptCount is %d. The assignment usually expects a single final submission.%n", attemptCount);
        }
    }

    private static void persistSubmissionLock(Path lockFile, String regNo, JSONObject submissionResult) throws Exception {
        JSONObject lockData = new JSONObject();
        lockData.put("regNo", regNo);
        lockData.put("submittedAtUtc", Instant.now().toString());
        lockData.put("response", submissionResult);

        Files.writeString(
                lockFile,
                lockData.toString(2),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    private static final class RunConfig {
        private final String regNo;
        private final boolean submitRequested;
        private final boolean forceSubmit;

        private RunConfig(String regNo, boolean submitRequested, boolean forceSubmit) {
            this.regNo = regNo;
            this.submitRequested = submitRequested;
            this.forceSubmit = forceSubmit;
        }

        public static RunConfig parse(String[] args) {
            String regNo = DEFAULT_REG_NO;
            boolean submitRequested = false;
            boolean forceSubmit = false;

            for (String arg : args) {
                if ("--submit".equalsIgnoreCase(arg)) {
                    submitRequested = true;
                } else if ("--force-submit".equalsIgnoreCase(arg)) {
                    submitRequested = true;
                    forceSubmit = true;
                } else if (!arg.startsWith("--")) {
                    regNo = arg;
                }
            }

            return new RunConfig(regNo, submitRequested, forceSubmit);
        }

        public String getRegNo() {
            return regNo;
        }

        public boolean isSubmitRequested() {
            return submitRequested;
        }

        public boolean isForceSubmit() {
            return forceSubmit;
        }
    }
}

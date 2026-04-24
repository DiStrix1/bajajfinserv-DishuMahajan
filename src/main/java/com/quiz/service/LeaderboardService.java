package com.quiz.service;

import com.quiz.api.QuizApiClient;
import com.quiz.model.Event;
import com.quiz.model.LeaderboardEntry;
import com.quiz.util.DeduplicationUtil;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LeaderboardService {
    private final QuizApiClient apiClient;
    private final int totalPolls;
    private final int delayMs;

    public LeaderboardService(QuizApiClient apiClient, int totalPolls, int delayMs) {
        this.apiClient = apiClient;
        this.totalPolls = totalPolls;
        this.delayMs = delayMs;
    }

    public List<LeaderboardEntry> buildLeaderboard(String regNo) throws IOException, InterruptedException {
        Map<String, Event> uniqueEvents = collectUniqueEventsAcrossPolls(regNo);
        Map<String, Integer> totalsByParticipant = computeTotals(uniqueEvents);
        return buildSortedLeaderboard(totalsByParticipant);
    }

    public JSONObject submitLeaderboard(String regNo, List<LeaderboardEntry> leaderboard) throws IOException, InterruptedException {
        return apiClient.submitLeaderboard(regNo, leaderboard);
    }

    private Map<String, Event> collectUniqueEventsAcrossPolls(String regNo) throws IOException, InterruptedException {
        Map<String, Event> uniqueEvents = new LinkedHashMap<>();

        for (int poll = 0; poll < totalPolls; poll++) {
            System.out.printf("Polling %d...%n", poll);

            List<Event> events = apiClient.fetchEvents(regNo, poll);
            for (Event event : events) {
                registerEventIfNew(uniqueEvents, event);
            }

            sleepBetweenPollsIfNeeded(poll);
        }
        return uniqueEvents;
    }

    private void registerEventIfNew(Map<String, Event> uniqueEvents, Event event) {
        String eventKey = DeduplicationUtil.buildEventKey(event);
        Event previous = uniqueEvents.putIfAbsent(eventKey, event);

        if (previous == null) {
            System.out.printf("  Added %s = %d%n", eventKey, event.getScore());
        } else {
            System.out.printf("  Skipped duplicate %s%n", eventKey);
        }
    }

    private void sleepBetweenPollsIfNeeded(int pollIndex) throws InterruptedException {
        if (pollIndex < totalPolls - 1) {
            Thread.sleep(delayMs);
        }
    }

    private Map<String, Integer> computeTotals(Map<String, Event> uniqueEvents) {
        Map<String, Integer> totalsByParticipant = new LinkedHashMap<>();
        for (Event event : uniqueEvents.values()) {
            totalsByParticipant.merge(event.getParticipant(), event.getScore(), Integer::sum);
        }
        return totalsByParticipant;
    }

    private List<LeaderboardEntry> buildSortedLeaderboard(Map<String, Integer> totalsByParticipant) {
        List<LeaderboardEntry> leaderboard = new ArrayList<>();
        for (Map.Entry<String, Integer> scoreEntry : totalsByParticipant.entrySet()) {
            leaderboard.add(new LeaderboardEntry(scoreEntry.getKey(), scoreEntry.getValue()));
        }

        leaderboard.sort(Comparator.comparingInt(LeaderboardEntry::getTotalScore).reversed());
        return leaderboard;
    }
}

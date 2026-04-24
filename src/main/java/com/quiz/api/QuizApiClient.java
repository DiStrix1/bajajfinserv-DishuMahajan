package com.quiz.api;

import com.quiz.model.Event;
import com.quiz.model.LeaderboardEntry;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class QuizApiClient {
    private static final String MESSAGES_ENDPOINT = "/quiz/messages";
    private static final String SUBMIT_ENDPOINT = "/quiz/submit";
    private static final int MAX_RETRIES = 6;
    private static final int RETRY_BASE_DELAY_MS = 3000;

    private final HttpClient client;
    private final String baseUrl;

    public QuizApiClient(String baseUrl) {
        this.client = HttpClient.newHttpClient();
        this.baseUrl = baseUrl;
    }

    public List<Event> fetchEvents(String regNo, int pollIndex) throws IOException, InterruptedException {
        String url = String.format("%s%s?regNo=%s&poll=%d", baseUrl, MESSAGES_ENDPOINT, regNo, pollIndex);
        JSONObject payload = getJsonWithRetry(url, "poll=" + pollIndex);
        JSONArray events = payload.optJSONArray("events");

        if (events == null) {
            throw new IOException("poll=" + pollIndex + " response does not contain an 'events' array.");
        }
        return parseEvents(events);
    }

    public JSONObject submitLeaderboard(String regNo, List<LeaderboardEntry> leaderboard) throws IOException, InterruptedException {
        JSONObject requestBody = new JSONObject();
        requestBody.put("regNo", regNo);
        requestBody.put("leaderboard", toJsonArray(leaderboard));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + SUBMIT_ENDPOINT))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return parseHttpResponseAsJson(response, "submit");
    }

    private List<Event> parseEvents(JSONArray events) {
        List<Event> parsedEvents = new ArrayList<>(events.length());
        for (int index = 0; index < events.length(); index++) {
            JSONObject rawEvent = events.getJSONObject(index);
            parsedEvents.add(new Event(
                    rawEvent.getString("roundId"),
                    rawEvent.getString("participant"),
                    rawEvent.getInt("score")
            ));
        }
        return parsedEvents;
    }

    private JSONObject getJsonWithRetry(String url, String context) throws IOException, InterruptedException {
        IOException latestError = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            try {
                return parseHttpResponseAsJson(response, context);
            } catch (IOException parsingOrHttpError) {
                latestError = parsingOrHttpError;
                if (attempt < MAX_RETRIES) {
                    int waitMs = RETRY_BASE_DELAY_MS * attempt;
                    System.out.printf("  Warning: %s request issue (attempt %d/%d). Retrying in %d ms...%n",
                            context, attempt, MAX_RETRIES, waitMs);
                    Thread.sleep(waitMs);
                }
            }
        }

        throw latestError != null
                ? latestError
                : new IOException("Unexpected error while reading " + context + ".");
    }

    private JSONObject parseHttpResponseAsJson(HttpResponse<String> response, String context) throws IOException {
        int statusCode = response.statusCode();
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String body = normalizeBody(response.body());

        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException(context + " failed. HTTP " + statusCode
                    + ", contentType=" + contentType + ", body=" + preview(body));
        }

        try {
            return new JSONObject(body);
        } catch (JSONException ex) {
            throw new IOException(context + " returned non-JSON body. contentType="
                    + contentType + ", body=" + preview(body), ex);
        }
    }

    private JSONArray toJsonArray(List<LeaderboardEntry> leaderboard) {
        JSONArray array = new JSONArray();
        for (LeaderboardEntry entry : leaderboard) {
            JSONObject jsonEntry = new JSONObject();
            jsonEntry.put("participant", entry.getParticipant());
            jsonEntry.put("totalScore", entry.getTotalScore());
            array.put(jsonEntry);
        }
        return array;
    }

    private String normalizeBody(String body) {
        if (body == null) {
            return "";
        }

        String cleaned = body;
        if (!cleaned.isEmpty() && cleaned.charAt(0) == '\uFEFF') {
            cleaned = cleaned.substring(1);
        }
        return cleaned.trim();
    }

    private String preview(String body) {
        String compact = Optional.ofNullable(body)
                .orElse("")
                .replaceAll("\\s+", " ")
                .trim();

        if (compact.length() <= 180) {
            return compact;
        }
        return compact.substring(0, 180) + "...";
    }
}

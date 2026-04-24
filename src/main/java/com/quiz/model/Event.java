package com.quiz.model;

import java.util.Objects;

public class Event {
    private final String roundId;
    private final String participant;
    private final int score;

    public Event(String roundId, String participant, int score) {
        this.roundId = Objects.requireNonNull(roundId, "roundId");
        this.participant = Objects.requireNonNull(participant, "participant");
        this.score = score;
    }

    public String getRoundId() {
        return roundId;
    }

    public String getParticipant() {
        return participant;
    }

    public int getScore() {
        return score;
    }

    @Override
    public String toString() {
        return "Event{" +
                "roundId='" + roundId + '\'' +
                ", participant='" + participant + '\'' +
                ", score=" + score +
                '}';
    }
}

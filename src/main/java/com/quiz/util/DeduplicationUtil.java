package com.quiz.util;

import com.quiz.model.Event;

public final class DeduplicationUtil {
    private static final String KEY_SEPARATOR = "|";

    private DeduplicationUtil() {
    }

    public static String buildEventKey(Event event) {
        return buildEventKey(event.getRoundId(), event.getParticipant());
    }

    public static String buildEventKey(String roundId, String participant) {
        return roundId + KEY_SEPARATOR + participant;
    }
}

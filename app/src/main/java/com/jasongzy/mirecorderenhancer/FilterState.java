package com.jasongzy.mirecorderenhancer;

import java.util.ArrayList;
import java.util.List;

final class FilterState {
    enum Location {
        ALL(R.string.filter_all, null),
        LOCAL_ONLY(R.string.filter_local_only, "in_local = 1 AND in_cloud = 0"),
        SYNCED(R.string.filter_synced, "in_local = 1 AND in_cloud = 1"),
        CLOUD_ONLY(R.string.filter_cloud_only, "in_local = 0 AND in_cloud = 1");

        private final int label;
        private final String predicate;

        Location(int label, String predicate) {
            this.label = label;
            this.predicate = predicate;
        }

        int label() {
            return label;
        }
    }

    enum DurationOperator {
        ALL,
        LESS_THAN,
        AT_LEAST
    }

    enum Transcription {
        ALL(R.string.filter_all, null),
        COMPLETED(
                R.string.transcription_completed,
                "sha1 IN (SELECT sha1 FROM recognition_sentence WHERE sha1 IS NOT NULL)"),
        NOT_COMPLETED(
                R.string.transcription_not_completed,
                "(sha1 IS NULL OR sha1 NOT IN "
                        + "(SELECT sha1 FROM recognition_sentence WHERE sha1 IS NOT NULL))");

        private final int label;
        private final String predicate;

        Transcription(int label, String predicate) {
            this.label = label;
            this.predicate = predicate;
        }

        int label() {
            return label;
        }
    }

    static final FilterState EMPTY =
            new FilterState(Location.ALL, DurationOperator.ALL, 3, null, null, Transcription.ALL);

    private final Location location;
    private final DurationOperator durationOperator;
    private final long durationSeconds;
    private final Long startTime;
    private final Long endTime;
    private final Transcription transcription;

    FilterState(
            Location location,
            DurationOperator durationOperator,
            long durationSeconds,
            Long startTime,
            Long endTime,
            Transcription transcription) {
        this.location = location;
        this.durationOperator = durationOperator;
        this.durationSeconds = durationSeconds;
        this.startTime = startTime;
        this.endTime = endTime;
        this.transcription = transcription;
    }

    Location location() {
        return location;
    }

    DurationOperator durationOperator() {
        return durationOperator;
    }

    long durationSeconds() {
        return durationSeconds;
    }

    Long startTime() {
        return startTime;
    }

    Long endTime() {
        return endTime;
    }

    Transcription transcription() {
        return transcription;
    }

    FilterState withLocation(Location value) {
        return new FilterState(value, durationOperator, durationSeconds, startTime, endTime, transcription);
    }

    FilterState withDuration(DurationOperator operator, long seconds) {
        return new FilterState(location, operator, seconds, startTime, endTime, transcription);
    }

    FilterState withDate(Long start, Long end) {
        return new FilterState(location, durationOperator, durationSeconds, start, end, transcription);
    }

    FilterState withTranscription(Transcription value) {
        return new FilterState(location, durationOperator, durationSeconds, startTime, endTime, value);
    }

    boolean isEmpty() {
        return location == Location.ALL
                && durationOperator == DurationOperator.ALL
                && startTime == null
                && endTime == null
                && transcription == Transcription.ALL;
    }

    String predicate() {
        List<String> clauses = new ArrayList<>();
        if (location.predicate != null) {
            clauses.add(location.predicate);
        }
        if (durationOperator == DurationOperator.LESS_THAN) {
            clauses.add("duration < " + durationSeconds);
        } else if (durationOperator == DurationOperator.AT_LEAST) {
            clauses.add("duration >= " + durationSeconds);
        }
        if (startTime != null) {
            clauses.add("create_time >= " + startTime);
        }
        if (endTime != null) {
            clauses.add("create_time < " + endTime);
        }
        if (transcription.predicate != null) {
            clauses.add(transcription.predicate);
        }
        return String.join(" AND ", clauses);
    }
}

package com.jasongzy.mirecorderenhancer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class QueryFilterTest {
    @Test
    public void limitsFilteringToRecordListQueries() {
        assertTrue(QueryFilter.isRecordListQuery("records", "records", true, "create_time DESC LIMIT 51"));
        assertTrue(QueryFilter.isRecordListQuery("records", "call_records_view", true, " create_time DESC"));
        assertFalse(QueryFilter.isRecordListQuery("records", "records", false, "create_time DESC"));
        assertFalse(QueryFilter.isRecordListQuery("records", "downloads", true, "create_time DESC"));
    }

    @Test
    public void appendsPredicateWithExplicitGrouping() {
        assertEquals("(duration < 3)", QueryFilter.appendPredicate(null, "duration < 3"));
        assertEquals(
                "(rec_type = ?) AND (in_local = 1 AND in_cloud = 0)",
                QueryFilter.appendPredicate("rec_type = ?", "in_local = 1 AND in_cloud = 0"));
    }

    @Test
    public void combinesIndependentFilterCategories() {
        FilterState state = FilterState.EMPTY
                .withLocation(FilterState.Location.SYNCED)
                .withDuration(FilterState.DurationOperator.AT_LEAST, 10)
                .withDate(1000L, 2000L)
                .withTranscription(FilterState.Transcription.COMPLETED);

        assertEquals(
                "in_local = 1 AND in_cloud = 1 AND duration >= 10 AND create_time >= 1000 AND create_time < 2000 "
                        + "AND sha1 IN (SELECT sha1 FROM recognition_sentence WHERE sha1 IS NOT NULL)",
                state.predicate());
    }
}

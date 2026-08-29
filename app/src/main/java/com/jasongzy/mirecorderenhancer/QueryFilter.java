package com.jasongzy.mirecorderenhancer;

import java.util.Locale;
import java.util.Set;

final class QueryFilter {
    private static final String AUTHORITY = "records";
    private static final Set<String> LIST_PATHS = Set.of("records", "call_records_view");

    private QueryFilter() {}

    static boolean isRecordListQuery(String authority, String path, boolean defaultProjection, String sortOrder) {
        if (!AUTHORITY.equals(authority) || !LIST_PATHS.contains(path) || !defaultProjection || sortOrder == null) {
            return false;
        }
        return sortOrder.stripLeading().toLowerCase(Locale.ROOT).startsWith("create_time");
    }

    static String appendPredicate(String selection, String predicate) {
        if (selection == null || selection.isBlank()) {
            return "(" + predicate + ")";
        }
        return "(" + selection + ") AND (" + predicate + ")";
    }
}

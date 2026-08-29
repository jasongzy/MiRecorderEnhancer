package com.jasongzy.mirecorderenhancer;

import java.util.concurrent.atomic.AtomicReference;

final class FilterController {
    private static final AtomicReference<FilterState> STATE = new AtomicReference<>(FilterState.EMPTY);

    private FilterController() {}

    static FilterState get() {
        return STATE.get();
    }

    static void set(FilterState state) {
        STATE.set(state);
    }
}

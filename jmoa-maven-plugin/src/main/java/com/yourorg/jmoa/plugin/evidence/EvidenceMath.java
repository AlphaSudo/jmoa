package com.yourorg.jmoa.plugin.evidence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class EvidenceMath {

    private EvidenceMath() {
    }

    static long medianLong(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return Math.round((sorted.get(middle - 1) + sorted.get(middle)) / 2.0d);
    }
}

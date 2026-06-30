package com.example.tier2;

import java.util.ArrayList;
import java.util.List;

public class Tier2FixtureHost {

    static final List<Integer> RECORDED_IDS = new ArrayList<>();

    static String decorate(String value) {
        return "tier2-" + value.trim().toUpperCase();
    }

    static void recordId(int value) {
        RECORDED_IDS.add(value);
    }

    String label() {
        return "patient-tier2";
    }

    interface Tier2View {
        String externalId();
    }

    static final class Tier2ViewImpl implements Tier2View {
        private final String externalId;

        Tier2ViewImpl(String externalId) {
            this.externalId = externalId;
        }

        @Override
        public String externalId() {
            return externalId;
        }
    }

    static final class Tier2Record {
        private final String marker;

        Tier2Record() {
            this("constructed-tier2");
        }

        Tier2Record(String marker) {
            this.marker = marker;
        }

        String marker() {
            return marker;
        }
    }
}

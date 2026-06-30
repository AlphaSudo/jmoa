package com.example.tier2;

import java.util.List;
import java.util.function.Function;

public class Tier2OrdinalShiftFixture {

    public List<Function<String, String>> functions() {
        return List.of(
            Tier2FixtureHost::decorate,
            String::trim
        );
    }
}

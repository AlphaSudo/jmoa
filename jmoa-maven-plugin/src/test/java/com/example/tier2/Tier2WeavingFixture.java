package com.example.tier2;

import java.util.function.Function;

public class Tier2WeavingFixture {

    public Function<String, Integer> tier1Function() {
        return String::length;
    }

    public Function<String, String> tier2Function() {
        return Tier2FixtureHost::decorate;
    }

    public Function<String, String> untouchedFunction() {
        return String::trim;
    }
}

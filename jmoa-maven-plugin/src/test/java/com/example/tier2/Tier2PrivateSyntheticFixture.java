package com.example.tier2;

import java.util.function.Predicate;

public class Tier2PrivateSyntheticFixture {

    public Predicate<String> tier2Predicate() {
        return value -> value != null && !value.isBlank();
    }
}

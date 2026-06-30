package com.example.tier2;

import java.util.function.Predicate;

public interface Tier2PrivateSyntheticInterfaceFixture {

    static Predicate<String> tier2Predicate() {
        return value -> !value.isBlank();
    }
}

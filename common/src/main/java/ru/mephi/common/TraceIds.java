package ru.mephi.common;

import java.util.UUID;
public final class TraceIds {
    private TraceIds() {
    }
    public static String safe(String value) {
        return value != null && value.matches("[a-zA-Z0-9_-]{1,64}") ? value : UUID.randomUUID().toString();
    }
}

package ru.mephi.common;

import java.time.Instant;
import java.util.Map;
public record ErrorDto(Instant timestamp, int status, String code, String message, String path,
        String traceId, Map<String, String> fields) {
}

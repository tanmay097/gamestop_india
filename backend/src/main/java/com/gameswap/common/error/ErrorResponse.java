package com.gameswap.common.error;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
    String code,
    String message,
    int status,
    String path,
    Instant timestamp,
    Map<String, String> fieldErrors
) {}

package com.gameswap.common.security;

import java.util.UUID;

public record AuthenticatedUser(
    UUID userId,
    boolean isAdmin
) {}

package com.gameswap.common.api;

import java.util.List;

public record CursorPage<T>(
    List<T> items,
    String nextCursor,
    boolean hasMore
) {
    public static <T> CursorPage<T> of(List<T> items, String nextCursor, boolean hasMore) {
        return new CursorPage<>(items, nextCursor, hasMore);
    }
}

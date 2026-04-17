package com.example.training_platform.common.dto;

import java.util.List;

public record PagedResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PagedResponse<T> of(List<T> items, int page, int size, long totalElements) {
        int safeSize = size <= 0 ? 1 : size;
        int pages = (int) Math.ceil((double) totalElements / safeSize);
        return new PagedResponse<>(items, page, size, totalElements, pages);
    }
}

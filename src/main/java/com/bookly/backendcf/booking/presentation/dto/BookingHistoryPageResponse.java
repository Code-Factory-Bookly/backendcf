package com.bookly.backendcf.booking.presentation.dto;

import java.util.List;

public record BookingHistoryPageResponse(
        List<BookingResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String message) {
}

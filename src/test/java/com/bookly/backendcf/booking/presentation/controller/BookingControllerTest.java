package com.bookly.backendcf.booking.presentation.controller;

import com.bookly.backendcf.booking.application.service.CreateBookingService;
import com.bookly.backendcf.booking.application.exception.BookingConflictException;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bookly.backendcf.booking.presentation.dto.CreateBookingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreateBookingService createBookingService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID customerId;
    private UUID professionalId;
    private UUID serviceId;
    private CreateBookingRequest validRequest;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        professionalId = UUID.randomUUID();
        serviceId = UUID.randomUUID();

        LocalDateTime startTime = LocalDateTime.now().plusDays(1);
        LocalDateTime endTime = startTime.plusMinutes(45);

        validRequest = new CreateBookingRequest(professionalId, serviceId, startTime, endTime);
    }

    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000", roles = "CUSTOMER")
    void shouldReturn201WhenBookingCreatedSuccessfully() throws Exception {
        Booking mockBooking = new Booking(customerId, professionalId, serviceId,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusMinutes(45));

        when(createBookingService.createBooking(any(), any(), any(), any(), any()))
                .thenReturn(mockBooking);

        mockMvc.perform(post("/api/v1/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("CONFIRMADA"));
    }

    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000", roles = "CUSTOMER")
    void shouldReturn409WhenSlotOccupied() throws Exception {
        when(createBookingService.createBooking(any(), any(), any(), any(), any()))
                .thenThrow(new BookingConflictException("SLOT_OCUPADO", "Slot no disponible"));

        mockMvc.perform(post("/api/v1/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SLOT_OCUPADO"));
    }

    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000", roles = "CUSTOMER")
    void shouldReturn400WhenInvalidTimeRange() throws Exception {
        LocalDateTime startTime = LocalDateTime.now().plusDays(1);
        CreateBookingRequest invalidRequest = new CreateBookingRequest(
                professionalId, serviceId, startTime, startTime.minusMinutes(30)
        );

        when(createBookingService.createBooking(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("startTime debe ser antes que endTime"));

        mockMvc.perform(post("/api/v1/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isUnauthorized());
    }
}
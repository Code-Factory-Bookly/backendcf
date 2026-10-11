
package com.bookly.backendcf.booking.presentation.controller;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.booking.application.service.CancelBookingService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CancelBookingErrorResponseTest {

    private static final String ENDPOINT =
            "/api/v1/reservas/{bookingId}/cancelacion";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    @MockitoBean
    private CancelBookingService cancelBookingService;

    @Test
    void anotherCustomerReceives403WithStandardError() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String token = customerToken();

        when(cancelBookingService.cancelBooking(
                eq(bookingId),
                any(Authentication.class),
                isNull()
        )).thenThrow(new AccessDeniedException(
                "Solo el cliente propietario puede cancelar la reserva"
        ));

        mockMvc.perform(
                        patch(ENDPOINT, bookingId)
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value(
                        "Solo el cliente propietario puede cancelar la reserva"
                ))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        verify(cancelBookingService, times(1)).cancelBooking(
                eq(bookingId),
                any(Authentication.class),
                isNull()
        );
    }

    @Test
    void nonexistentBookingReceives404WithStandardError() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String token = customerToken();

        when(cancelBookingService.cancelBooking(
                eq(bookingId),
                any(Authentication.class),
                isNull()
        )).thenThrow(new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Reserva no encontrada"
        ));

        mockMvc.perform(
                        patch(ENDPOINT, bookingId)
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(
                        "Reserva no encontrada"
                ))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void alreadyCancelledBookingReceives409() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String token = customerToken();

        when(cancelBookingService.cancelBooking(
                eq(bookingId),
                any(Authentication.class),
                isNull()
        )).thenThrow(new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Solo se pueden cancelar reservas confirmadas"
        ));

        mockMvc.perform(
                        patch(ENDPOINT, bookingId)
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "Solo se pueden cancelar reservas confirmadas"
                ))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void cancellationOutsideDeadlineReceives409() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String token = customerToken();

        when(cancelBookingService.cancelBooking(
                eq(bookingId),
                any(Authentication.class),
                isNull()
        )).thenThrow(new ResponseStatusException(
                HttpStatus.CONFLICT,
                "La reserva debe cancelarse con al menos "
                        + "120 minutos de anticipacion"
        ));

        mockMvc.perform(
                        patch(ENDPOINT, bookingId)
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "La reserva debe cancelarse con al menos "
                                + "120 minutos de anticipacion"
                ))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    private String customerToken() {
        UserAccount customer = new UserAccount(
                "hu09-review-" + UUID.randomUUID() + "@example.com",
                "hashed-password",
                "Cliente HU09 Review",
                UserRole.CUSTOMER
        );

        UserAccount saved = accountRepository.save(customer);

        return tokenService.createToken(saved);
    }
}

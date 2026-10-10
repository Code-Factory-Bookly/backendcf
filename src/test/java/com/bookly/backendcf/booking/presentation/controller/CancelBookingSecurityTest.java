
package com.bookly.backendcf.booking.presentation.controller;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.booking.application.service.CancelBookingService;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CancelBookingSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    @MockitoBean
    private CancelBookingService cancelBookingService;

    private static final String ENDPOINT =
            "/api/v1/reservas/{bookingId}/cancelacion";

    @Test
    void sinTokenDevuelve401() throws Exception {
        UUID bookingId = UUID.randomUUID();

        mockMvc.perform(
                        patch(ENDPOINT, bookingId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Cambio de planes\"}")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verifyNoInteractions(cancelBookingService);
    }

    @Test
    void administradorNoPuedeCancelarDevuelve403() throws Exception {
        String adminToken = tokenFor(UserRole.ADMIN);
        UUID bookingId = UUID.randomUUID();

        mockMvc.perform(
                        patch(ENDPOINT, bookingId)
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Cambio de planes\"}")
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        verifyNoInteractions(cancelBookingService);
    }

    @Test
    void profesionalNoPuedeCancelarDevuelve403() throws Exception {
        String professionalToken = tokenFor(UserRole.PROFESSIONAL);
        UUID bookingId = UUID.randomUUID();

        mockMvc.perform(
                        patch(ENDPOINT, bookingId)
                                .header("Authorization", "Bearer " + professionalToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Cambio de planes\"}")
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        verifyNoInteractions(cancelBookingService);
    }

    @Test
    void clienteAutenticadoPuedeAccederAlEndpoint() throws Exception {
        UserAccount customer = createAccount(UserRole.CUSTOMER);
        String customerToken = tokenService.createToken(customer);

        UUID bookingId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        LocalDateTime start = LocalDateTime.now().plusDays(3);
        LocalDateTime cancelledAt = LocalDateTime.now();

        Booking booking = new Booking(
                customer.getId(),
                professionalId,
                serviceId,
                start,
                start.plusHours(1)
        );

        ReflectionTestUtils.setField(booking, "id", bookingId);

        booking.setStatus(BookingStatus.CANCELADA);
        booking.setCancelledAt(cancelledAt);
        booking.setCancellationReason("Cambio de planes");

        when(cancelBookingService.cancelBooking(
                eq(bookingId),
                any(),
                eq("Cambio de planes")
        )).thenReturn(booking);

        mockMvc.perform(
                        patch(ENDPOINT, bookingId)
                                .header("Authorization", "Bearer " + customerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Cambio de planes\"}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId")
                        .value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELADA"))
                .andExpect(jsonPath("$.cancellationReason")
                        .value("Cambio de planes"));

        verify(cancelBookingService, times(1))
                .cancelBooking(
                        eq(bookingId),
                        argThat(authentication ->
                                authentication != null
                                        && authentication.isAuthenticated()
                                        && customer.getId().toString().equals(
                                        authentication.getName())
                        ),
                        eq("Cambio de planes")
                );
    }

    private String tokenFor(UserRole role) {
        return tokenService.createToken(createAccount(role));
    }

    private UserAccount createAccount(UserRole role) {
        UserAccount account = new UserAccount(
                role.name().toLowerCase()
                        + "-"
                        + UUID.randomUUID()
                        + "@example.com",
                "hashed-password",
                "Usuario prueba HU09",
                role
        );

        return accountRepository.save(account);
    }
}

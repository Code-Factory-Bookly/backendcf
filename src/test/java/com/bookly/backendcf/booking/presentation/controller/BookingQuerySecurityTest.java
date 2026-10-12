package com.bookly.backendcf.booking.presentation.controller;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingQuerySecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    @MockitoBean
    private BookingRepository bookingRepository;

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/reservas/cliente/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void clientePuedeVerSusPropiasReservas() throws Exception {
        UserAccount customer = createAccount(UserRole.CUSTOMER);
        String token = tokenService.createToken(customer);
        when(bookingRepository.findByCustomerId(customer.getId()))
                .thenReturn(List.of(booking(customer.getId(), UUID.randomUUID())));

        mockMvc.perform(get("/api/v1/reservas/cliente/{id}", customer.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value(customer.getId().toString()));
    }

    @Test
    void clienteNoPuedeVerLasReservasDeOtroClienteDevuelve403() throws Exception {
        UserAccount customer = createAccount(UserRole.CUSTOMER);
        String token = tokenService.createToken(customer);

        mockMvc.perform(get("/api/v1/reservas/cliente/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void profesionalPuedeVerSusPropiasReservas() throws Exception {
        UserAccount professional = createAccount(UserRole.PROFESSIONAL);
        String token = tokenService.createToken(professional);
        when(bookingRepository.findByProfessionalId(professional.getId()))
                .thenReturn(List.of(booking(UUID.randomUUID(), professional.getId())));

        mockMvc.perform(get("/api/v1/reservas/profesional/{id}", professional.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].professionalId").value(professional.getId().toString()));
    }

    @Test
    void administradorPuedeVerLasReservasDeCualquierCliente() throws Exception {
        UserAccount admin = createAccount(UserRole.ADMIN);
        String token = tokenService.createToken(admin);
        UUID customerId = UUID.randomUUID();
        when(bookingRepository.findByCustomerId(customerId))
                .thenReturn(List.of(booking(customerId, UUID.randomUUID())));

        mockMvc.perform(get("/api/v1/reservas/cliente/{id}", customerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value(customerId.toString()));
    }

    private Booking booking(UUID customerId, UUID professionalId) {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        return new Booking(customerId, professionalId, UUID.randomUUID(), start, start.plusHours(1));
    }

    private UserAccount createAccount(UserRole role) {
        UserAccount account = new UserAccount(
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com",
                "hashed-password",
                "Usuario prueba",
                role);
        return accountRepository.save(account);
    }
}

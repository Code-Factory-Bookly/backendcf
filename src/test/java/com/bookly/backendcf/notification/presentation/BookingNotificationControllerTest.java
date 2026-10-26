package com.bookly.backendcf.notification.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.notification.domain.model.NotificationDelivery;
import com.bookly.backendcf.notification.domain.model.NotificationEventType;
import com.bookly.backendcf.notification.infrastructure.persistence.NotificationDeliveryRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BookingNotificationControllerTest {

    private static final String ENDPOINT = "/api/v1/reservas/{bookingId}/notificaciones";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private NotificationDeliveryRepository deliveryRepository;

    private UserAccount customer;
    private UserAccount professional;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        customer = persist(UserRole.CUSTOMER);
        professional = persist(UserRole.PROFESSIONAL);
        LocalDateTime start = LocalDateTime.now().plusDays(3).withNano(0);
        Booking booking = bookingRepository.saveAndFlush(new Booking(
                customer.getId(), professional.getId(), UUID.randomUUID(), start, start.plusMinutes(30)));
        bookingId = booking.getId();
        deliveryRepository.saveAndFlush(new NotificationDelivery(
                UUID.randomUUID(), bookingId, NotificationEventType.BOOKING_CREATED,
                customer.getEmail(), "Tu reserva fue confirmada", OffsetDateTime.now()));
    }

    @Test
    void sinTokenDevuelveUnauthorized() throws Exception {
        mockMvc.perform(get(ENDPOINT, bookingId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elClienteDuenoVeLosCorreosDeSuReserva() throws Exception {
        mockMvc.perform(get(ENDPOINT, bookingId).header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryMode").value("logging"))
                .andExpect(jsonPath("$.deliveries[0].eventType").value("BOOKING_CREATED"))
                .andExpect(jsonPath("$.deliveries[0].recipientEmail").value(customer.getEmail()))
                .andExpect(jsonPath("$.deliveries[0].status").value("PENDING"));
    }

    @Test
    void elProfesionalDeLaReservaTambienLosVe() throws Exception {
        mockMvc.perform(get(ENDPOINT, bookingId).header("Authorization", bearer(professional)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveries.length()").value(1));
    }

    @Test
    void elAdministradorPuedeVerCualquierReserva() throws Exception {
        mockMvc.perform(get(ENDPOINT, bookingId).header("Authorization", bearer(persist(UserRole.ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    void otroClienteRecibe404ParaNoRevelarQueLaReservaExiste() throws Exception {
        mockMvc.perform(get(ENDPOINT, bookingId).header("Authorization", bearer(persist(UserRole.CUSTOMER))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void reservaInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get(ENDPOINT, UUID.randomUUID()).header("Authorization", bearer(customer)))
                .andExpect(status().isNotFound());
    }

    private UserAccount persist(UserRole role) {
        return accountRepository.save(new UserAccount(
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com",
                "hashed-password", "Usuario " + role.name(), role));
    }

    private String bearer(UserAccount account) {
        return "Bearer " + tokenService.createToken(account);
    }
}

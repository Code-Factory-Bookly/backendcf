package com.bookly.backendcf.booking.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * S3-08 (MEDIA, Azure #219) · La duracion de una reserva debe coincidir con la del servicio.
 *
 * <p><b>Defecto que se reproduce.</b> {@code CreateBookingService} valida que el rango sea futuro,
 * que caiga dentro del horario del profesional, que no haya bloqueos y que no se solape con otra
 * reserva, pero no comprueba que {@code endTime - startTime} sea la duracion del servicio
 * contratado. Una reserva de 15 minutos de un servicio de 60 se crea confirmada: el reporte de
 * ocupacion suma 15 minutos y el resto de la franja vuelve a ofrecerse en la disponibilidad.
 *
 * <p><b>Alcance.</b> Solo se prueba el caso del informe: una reserva mas corta que el servicio. Si
 * el negocio admite duraciones variables (decision pendiente), el esperado cambia.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class BookingDurationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID professionalId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final LocalDate lunes = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @BeforeEach
    void setUp() {
        insertProfessional(professionalId, uniqueEmail("profesional"), "Profesional Duracion");
        insertService(serviceId, "Servicio Duracion " + suffix, 60);
        insertWeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(18, 0));
    }

    @AfterEach
    void cleanUp() {
        limpiarBaseDeDatos();
    }

    // ----------------------------------------------------------------------------------
    // Control: una reserva con la duracion exacta del servicio se crea.
    // ----------------------------------------------------------------------------------

    @Test
    void unaReservaConLaDuracionExactaDelServicioSeCrea() throws Exception {
        mockMvc.perform(post("/api/v1/reservas")
                        .header("Authorization", "Bearer " + tokenDeCliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(lunes.atTime(9, 0), lunes.atTime(10, 0))))
                .andExpect(status().isCreated());
    }

    // ----------------------------------------------------------------------------------
    // S3-08: una reserva mas corta que el servicio debe rechazarse.
    // ----------------------------------------------------------------------------------

    // Bug conocido S3-08 (Azure #219): falla a proposito hasta que se corrija. Excluido del build por defecto;
    // se ejecuta aparte con: ./mvnw test -Pknown-bugs
    @Tag("known-bug")
    @Test
    void unaReservaMasCortaQueElServicioDebeRechazarse() throws Exception {
        // Servicio de 60 minutos, reserva de 15 minutos.
        MvcResult resultado = mockMvc.perform(post("/api/v1/reservas")
                        .header("Authorization", "Bearer " + tokenDeCliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(lunes.atTime(9, 0), lunes.atTime(9, 15))))
                .andReturn();

        int codigo = resultado.getResponse().getStatus();
        assertThat(codigo)
                .as("Una reserva de 15 min de un servicio de 60 min debe rechazarse con un error de "
                        + "negocio (4xx), no crearse. Respuesta: %s", resultado.getResponse().getContentAsString())
                .isBetween(400, 499);

        Long reservas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE professional_id = ?", Long.class, professionalId);
        assertThat(reservas)
                .as("No debe quedar ninguna reserva de duracion invalida en la base")
                .isZero();
    }

    // ----------------------------------------------------------------------------------
    // Ayudantes
    // ----------------------------------------------------------------------------------

    private String cuerpo(LocalDateTime inicio, LocalDateTime fin) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("professionalId", professionalId.toString());
        cuerpo.put("serviceId", serviceId.toString());
        cuerpo.put("startTime", inicio.toString());
        cuerpo.put("endTime", fin.toString());
        return objectMapper.writeValueAsString(cuerpo);
    }

    private String tokenDeCliente() {
        UserAccount cuenta = accountRepository.save(new UserAccount(
                uniqueEmail("cliente"), "test-not-a-real-password", "Cliente Duracion", UserRole.CUSTOMER));
        registrarUsuario(cuenta.getId());
        return tokenService.createToken(cuenta);
    }
}

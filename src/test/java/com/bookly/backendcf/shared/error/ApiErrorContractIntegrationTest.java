package com.bookly.backendcf.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.support.IntegrationTestSupport;
import com.fasterxml.jackson.core.type.TypeReference;
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
 * S3-09 (MEDIA) · Contrato de error de la API: toda respuesta de error debe traer
 * {@code errorCode}, {@code message}, {@code details}, {@code traceId} y {@code timestamp}.
 *
 * <p><b>Defecto que se reproduce.</b> La plataforma expone dos formatos de error distintos. El
 * extendido es {@code ApiErrorResponse}, que es el que define el contrato publicado en
 * {@code docs/openapi.yaml} ({@code required: [errorCode, message, details, traceId, timestamp]}).
 * Pero {@code BookingController} declara un record local
 * {@code ErrorResponse(String error, String message)} y lo usa en el {@code catch} de
 * {@code BookingConflictException} y de {@code IllegalArgumentException}. El resultado es que
 * {@code POST /api/v1/reservas} responde {@code {"error":"SLOT_OCUPADO","message":"..."}}: un
 * cliente que sigue la documentacion no encuentra {@code errorCode}, y no puede correlacionar el
 * error con la traza del servidor porque no hay {@code traceId}.
 *
 * <p><b>Como se arma el token valido.</b> {@code JwtAuthenticationFilter} no se fia del {@code sub}
 * del token: busca la cuenta en {@code app_user} y usa su rol actual. Si la cuenta no existe, la
 * peticion queda sin autenticar y el filtro de seguridad responde 401 antes de llegar al
 * controlador. Por eso el usuario de la prueba se siembra en la base real y el token se firma con
 * el {@code JwtTokenService} de la aplicacion (misma clave secreta que usa el filtro).
 *
 * <p><b>Como se provoca el conflicto.</b> Se siembra una reserva {@code CONFIRMADA} que ocupa la
 * franja y se intenta reservar la misma franja. El servicio detecta el solapamiento y lanza
 * {@code BookingConflictException}, que el controlador captura para devolver su record reducido.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class ApiErrorContractIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UUID professionalId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();

    /** Manana siguiente: siempre futuro, porque la reserva exige un startTime futuro. */
    private final LocalDate manana = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @BeforeEach
    void setUp() {
        insertProfessional(professionalId, uniqueEmail("profesional"), "Profesional Contrato");
        insertService(serviceId, "Servicio Contrato " + suffix, 60);
        insertWeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(18, 0));
    }

    @AfterEach
    void cleanUp() {
        limpiarBaseDeDatos();
    }

    // ----------------------------------------------------------------------------------
    // S3-09: el cuerpo del error debe cumplir el contrato publicado en openapi.yaml.
    // ----------------------------------------------------------------------------------

    @Test
    void elErrorDeConflictoDeReservaCumpleElContratoDeErrorDeLaApi() throws Exception {
        // Arrange: un cliente autenticado y una franja ya ocupada por una reserva confirmada.
        UserAccount cliente = crearCuentaAutenticada("CUSTOMER", "Cliente Contrato");
        String token = tokenService.createToken(cliente);

        LocalDateTime inicio = manana.atTime(9, 0);
        LocalDateTime fin = manana.atTime(10, 0);
        insertBooking(cliente.getId(), professionalId, serviceId, inicio, fin, "CONFIRMADA");

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("professionalId", professionalId.toString());
        cuerpo.put("serviceId", serviceId.toString());
        cuerpo.put("startTime", inicio.toString());
        cuerpo.put("endTime", fin.toString());

        // Act
        MvcResult resultado = mockMvc.perform(post("/api/v1/reservas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isConflict())
                .andReturn();

        // Assert: el cuerpo trae los cinco campos del contrato ApiError.
        String respuesta = resultado.getResponse().getContentAsString();
        Map<String, Object> json = objectMapper.readValue(respuesta, new TypeReference<Map<String, Object>>() {});

        assertThat(json)
                .as("El cuerpo del error debe traer los cinco campos del contrato ApiError. "
                        + "Respuesta real: %s", respuesta)
                .containsKeys("errorCode", "message", "details", "traceId", "timestamp");

        assertThat(json.get("errorCode"))
                .as("El campo errorCode debe traer el codigo de negocio del conflicto. "
                        + "Respuesta real: %s", respuesta)
                .isEqualTo("SLOT_OCUPADO");

        assertThat(json.get("message"))
                .as("El campo message debe traer el mensaje del error. Respuesta real: %s", respuesta)
                .isNotNull();

        assertThat(json.get("traceId"))
                .as("El campo traceId debe estar presente para correlacionar con la traza. "
                        + "Respuesta real: %s", respuesta)
                .isNotNull();

        assertThat(json.get("timestamp"))
                .as("El campo timestamp debe estar presente. Respuesta real: %s", respuesta)
                .isNotNull();

        // Assert: el contrato no admite el campo reducido "error" en lugar de "errorCode".
        assertThat(json)
                .as("El contrato publicado no define un campo 'error': debe usarse 'errorCode'. "
                        + "Respuesta real: %s", respuesta)
                .doesNotContainKey("error");
    }

    // ----------------------------------------------------------------------------------
    // Contraste: un error de la plataforma que si usa el contrato global.
    // ----------------------------------------------------------------------------------

    @Test
    void unErrorDeLaPlataformaSiUsaElContratoGlobalDeError() throws Exception {
        // Arrange: un cliente autenticado y un payload invalido (falta el servicio).
        UserAccount cliente = crearCuentaAutenticada("CUSTOMER", "Cliente Contrato Global");
        String token = tokenService.createToken(cliente);

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("professionalId", professionalId.toString());
        cuerpo.put("startTime", manana.atTime(9, 0).toString());
        cuerpo.put("endTime", manana.atTime(10, 0).toString());

        // Act
        MvcResult resultado = mockMvc.perform(post("/api/v1/reservas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // Assert: el manejador global ya devuelve el contrato completo; sirve de contraste
        // para demostrar que la plataforma tiene un unico contrato definido y que
        // BookingController es la excepcion.
        String respuesta = resultado.getResponse().getContentAsString();
        Map<String, Object> json = objectMapper.readValue(respuesta, new TypeReference<Map<String, Object>>() {});

        assertThat(json)
                .as("El manejador global ya cumple el contrato completo. Respuesta real: %s", respuesta)
                .containsKeys("errorCode", "message", "details", "traceId", "timestamp");
    }

    // ----------------------------------------------------------------------------------
    // Ayudantes
    // ----------------------------------------------------------------------------------

    /**
     * Crea una cuenta en {@code app_user} y la devuelve como entidad de dominio.
     *
     * <p>Es indispensable que la cuenta exista en la base real: {@code JwtAuthenticationFilter}
     * busca el {@code sub} del token en {@code app_user} y, si no lo encuentra, deja la peticion
     * sin autenticar (respuesta 401) sin llegar al controlador.
     */
    private UserAccount crearCuentaAutenticada(String rol, String nombreCompleto) {
        UserAccount cuenta = new UserAccount(
                uniqueEmail("cliente"),
                "test-not-a-real-password",
                nombreCompleto,
                UserRole.valueOf(rol));
        UserAccount guardada = accountRepository.save(cuenta);
        // La cuenta se creo por el repositorio JPA, no por insertUser(): se registra para que
        // la limpieza la borre y no deje huerfanos en app_user.
        registrarUsuario(guardada.getId());
        return guardada;
    }
}

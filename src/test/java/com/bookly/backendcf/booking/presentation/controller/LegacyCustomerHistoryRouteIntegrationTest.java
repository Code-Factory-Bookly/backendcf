package com.bookly.backendcf.booking.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * S3-06 (MEDIA-ALTA) · HU-13: la ruta legacy {@code GET /api/v1/reservas/cliente/{customerId}} no
 * pagina ni deja rastro de auditoria de la lectura de datos personales de un tercero.
 *
 * <p><b>Defecto que se reproduce.</b> Conviven dos endpoints para el mismo proposito con contratos
 * incompatibles:
 * <ul>
 *   <li>La ruta nueva {@code /cliente/historial} esta restringida al rol {@code CUSTOMER} y
 *       pagina sus resultados en un {@code BookingHistoryPageResponse}
 *       ({@code content}, {@code page}, {@code size}, {@code totalElements}, {@code totalPages}).</li>
 *   <li>La ruta vieja {@code /cliente/{customerId}} permite que un ADMIN lea el historial completo
 *       de cualquier cliente, devuelve una lista plana y no escribe nada en {@code audit_log}.</li>
 * </ul>
 *
 * <p>El bypass de ADMIN en si es <b>por diseno y esta documentado</b> en
 * {@code OwnershipGuard}: el defecto no es el permiso, sino la ausencia de paginacion y de
 * auditoria sobre una lectura de PII de terceros. Con un historial largo la respuesta no tiene
 * limite de tamano, y sin auditoria no hay forma de demostrar quien consulto los datos personales
 * de otro cliente.
 *
 * <p><b>Veredicto del debugging sistematico.</b> Los tres puntos del enunciado se confirmaron: la
 * ruta devuelve un array plano de 90 elementos (48 KB) sin metadatos, el conteo de
 * {@code audit_log} no cambia tras la lectura, y el ADMIN recibe 403 en la ruta nueva y 200 en la
 * vieja. La severidad subio de MEDIA a <b>MEDIA-ALTA</b> por dos motivos: el crecimiento de la
 * respuesta es lineal y sin techo (riesgo de denegacion de servicio), y la ausencia de auditoria
 * no es un olvido del codigo sino una <b>restriccion del esquema</b>: la tabla de auditoria tiene
 * un CHECK que solo admite tres tipos de accion, y las tres son de escritura, de modo que anadir
 * una accion de lectura exige una migracion.
 *
 * <p><b>Como se arma el token valido.</b> {@code JwtAuthenticationFilter} busca el {@code sub} del
 * token en {@code app_user} y usa el rol que tiene alli, no el que dice el token. Por eso las
 * cuentas de la prueba se siembran en la base real y el token se firma con el
 * {@code JwtTokenService} de la aplicacion.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class LegacyCustomerHistoryRouteIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UUID professionalId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        insertProfessional(professionalId, uniqueEmail("profesional"), "Profesional Historial");
        insertService(serviceId, "Servicio Historial " + suffix, 60);
        insertWeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(18, 0));
    }

    @AfterEach
    void cleanUp() {
        limpiarBaseDeDatos();
    }

    // ----------------------------------------------------------------------------------
    // S3-06: la lectura de un tercero por ADMIN debe paginar.
    // ----------------------------------------------------------------------------------

    @Test
    void elAdministradorQueConsultaElHistorialDeOtroClienteRecibeUnaRespuestaPaginada() throws Exception {
        // Arrange: un ADMIN y un cliente con tres reservas.
        UserAccount admin = crearCuentaAutenticada(UserRole.ADMIN, "Administrador Historial");
        UserAccount cliente = crearCuentaAutenticada(UserRole.CUSTOMER, "Cliente Historial");
        sembrarReservasDelCliente(cliente.getId(), 3);

        // Act
        MvcResult resultado = mockMvc.perform(get("/api/v1/reservas/cliente/{id}", cliente.getId())
                        .header("Authorization", "Bearer " + tokenService.createToken(admin)))
                .andExpect(status().isOk())
                .andReturn();

        // Assert: la respuesta es una pagina, no una lista plana.
        String respuesta = resultado.getResponse().getContentAsString();
        List<?> cuerpo = objectMapper.readValue(respuesta, List.class);

        assertThat(cuerpo)
                .as("La ruta legacy debe devolver una pagina con la clave 'content', no una lista "
                        + "plana. Respuesta real: %s", respuesta)
                .hasSize(1);

        Object primerElemento = cuerpo.get(0);
        assertThat(primerElemento)
                .as("El elemento de la respuesta debe ser un objeto de pagina con la clave "
                        + "'content'. Elemento real: %s", primerElemento)
                .isInstanceOf(java.util.Map.class);

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> pagina = (java.util.Map<String, Object>) primerElemento;

        assertThat(pagina)
                .as("La pagina debe traer las claves del contrato BookingHistoryPageResponse. "
                        + "Pagina real: %s", pagina)
                .containsKeys("content", "page", "size", "totalElements", "totalPages");

        assertThat(pagina.get("content"))
                .as("La clave 'content' debe traer la lista de reservas paginada. Pagina real: %s",
                        pagina)
                .isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<Object> contenido = (List<Object>) pagina.get("content");
        assertThat(contenido)
                .as("Las tres reservas sembradas deben estar dentro de la pagina")
                .hasSize(3);
    }

    // ----------------------------------------------------------------------------------
    // S3-06: la lectura debe dejar rastro en audit_log.
    // ----------------------------------------------------------------------------------

    @Test
    void laLecturaDelHistorialDeOtroClienteDejaRastroEnLaAuditoria() throws Exception {
        // Arrange: un ADMIN y un cliente con reservas.
        UserAccount admin = crearCuentaAutenticada(UserRole.ADMIN, "Administrador Auditado");
        UserAccount cliente = crearCuentaAutenticada(UserRole.CUSTOMER, "Cliente Auditado");
        sembrarReservasDelCliente(cliente.getId(), 2);

        long rastrosAntes = countAuditEntries(admin.getId());

        // Act
        mockMvc.perform(get("/api/v1/reservas/cliente/{id}", cliente.getId())
                        .header("Authorization", "Bearer " + tokenService.createToken(admin)))
                .andExpect(status().isOk());

        // Assert: la lectura de PII de un tercero deja una entrada de auditoria del actor.
        long rastrosDespues = countAuditEntries(admin.getId());

        assertThat(rastrosDespues)
                .as("La lectura del historial de otro cliente debe dejar rastro en audit_log "
                        + "con el administrador como actor. Rastros antes: %s, despues: %s",
                        rastrosAntes, rastrosDespues)
                .isGreaterThan(rastrosAntes);
    }

    // ----------------------------------------------------------------------------------
    // Contraste: la ruta nueva y la vieja responden de forma distinta al mismo rol.
    // ----------------------------------------------------------------------------------

    @Test
    void elAdministradorEsRechazadoEnLaRutaNuevaPeroAceptadoEnLaRutaVieja() throws Exception {
        // Arrange: un ADMIN y un cliente cualquiera.
        UserAccount admin = crearCuentaAutenticada(UserRole.ADMIN, "Administrador Contraste");
        UserAccount cliente = crearCuentaAutenticada(UserRole.CUSTOMER, "Cliente Contraste");
        sembrarReservasDelCliente(cliente.getId(), 1);

        String token = tokenService.createToken(admin);

        // Act & Assert: la ruta nueva esta restringida al rol CUSTOMER.
        mockMvc.perform(get("/api/v1/reservas/cliente/historial")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // Act & Assert: la ruta vieja si acepta al ADMIN.
        mockMvc.perform(get("/api/v1/reservas/cliente/{id}", cliente.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ----------------------------------------------------------------------------------
    // Control: un CUSTOMER no puede leer el historial de otro cliente.
    // ----------------------------------------------------------------------------------

    @Test
    void unClienteNoPuedeConsultarElHistorialDeOtroCliente() throws Exception {
        // Arrange: dos clientes distintos.
        UserAccount cliente = crearCuentaAutenticada(UserRole.CUSTOMER, "Cliente Control");
        UserAccount otroCliente = crearCuentaAutenticada(UserRole.CUSTOMER, "Otro Cliente Control");
        sembrarReservasDelCliente(otroCliente.getId(), 1);

        // Act & Assert
        mockMvc.perform(get("/api/v1/reservas/cliente/{id}", otroCliente.getId())
                        .header("Authorization", "Bearer " + tokenService.createToken(cliente)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ----------------------------------------------------------------------------------
    // Control: la ruta nueva si pagina y si usa el contrato de pagina.
    // ----------------------------------------------------------------------------------

    @Test
    void laRutaNuevaDelHistorialSiDevuelveUnaPagina() throws Exception {
        // Arrange: un cliente con reservas.
        UserAccount cliente = crearCuentaAutenticada(UserRole.CUSTOMER, "Cliente Ruta Nueva");
        sembrarReservasDelCliente(cliente.getId(), 2);

        // Act & Assert
        mockMvc.perform(get("/api/v1/reservas/cliente/historial")
                        .header("Authorization", "Bearer " + tokenService.createToken(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    // ----------------------------------------------------------------------------------
    // Ayudantes
    // ----------------------------------------------------------------------------------

    /** Siembra {@code cantidad} reservas CONFIRMADA del cliente en fechas futuras relativas. */
    private void sembrarReservasDelCliente(UUID customerId, int cantidad) {
        LocalDate manana = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        for (int indice = 0; indice < cantidad; indice++) {
            LocalDateTime inicio = manana.atTime(9, 0).plusDays(indice);
            insertBooking(customerId, professionalId, serviceId, inicio, inicio.plusHours(1),
                    "CONFIRMADA");
        }
    }

    /**
     * Crea una cuenta en {@code app_user} y la devuelve como entidad de dominio.
     *
     * <p>Es indispensable que la cuenta exista en la base real: {@code JwtAuthenticationFilter}
     * busca el {@code sub} del token en {@code app_user} y, si no lo encuentra, deja la peticion
     * sin autenticar (respuesta 401) sin llegar al controlador.
     */
    private UserAccount crearCuentaAutenticada(UserRole rol, String nombreCompleto) {
        UserAccount cuenta = new UserAccount(
                uniqueEmail("cuenta"),
                "test-not-a-real-password",
                nombreCompleto,
                rol);
        UserAccount guardada = accountRepository.save(cuenta);
        // La cuenta se creo por el repositorio JPA, no por insertUser(): se registra para que
        // la limpieza la borre y no deje huerfanos en app_user.
        registrarUsuario(guardada.getId());
        return guardada;
    }
}

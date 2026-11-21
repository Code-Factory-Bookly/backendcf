package com.bookly.backendcf.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * S3-03 (ALTA, Azure #214) · Una excepcion no modelada no debe traducirse en un 401 falso.
 *
 * <p><b>Defecto que se reproduce.</b> {@code GlobalExceptionHandler} no tiene un manejador
 * generico. Cuando una excepcion no modelada llega al despachador, el servidor reenvia la peticion
 * a {@code /error}; esa ruta esta protegida, y el filtro de seguridad responde con el punto de
 * entrada de autenticacion: un 401 "Se requiere autenticacion" aunque el token sea valido.
 *
 * <p><b>Por que se usa un servidor HTTP real.</b> Ese reenvio a {@code /error} lo hace el
 * contenedor de servlets. {@code MockMvc} no lo simula: devuelve el codigo que fijo el manejador
 * sin reenviar, por lo que una prueba con {@code MockMvc} pasaria sin reproducir el defecto. Por
 * eso esta clase arranca Tomcat en un puerto aleatorio y usa {@link HttpClient}.
 *
 * <p><b>Derivado (S3-14 original).</b> Dos reemplazos simultaneos del horario semanal del mismo
 * profesional: la excepcion de bloqueo optimista no tiene manejador y termina en 401.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UnmodeledExceptionHttpIntegrationTest extends IntegrationTestSupport {

    /** Rondas de la carrera: el defecto es de temporizacion, una sola ronda puede no tocarlo. */
    private static final int RONDAS = 6;

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private UserAccountRepository accountRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final UUID professionalId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        insertProfessional(professionalId, uniqueEmail("profesional"), "Profesional Excepciones");
    }

    @AfterEach
    void cleanUp() {
        // Las franjas las crea el servicio en el reemplazo de horario: no pasan por
        // insertWeeklyWindow, no quedan registradas en la limpieza comun y su clave foranea
        // impediria borrar al profesional. Si quedaran, contaminarian el reporte de ocupacion.
        jdbc.update("DELETE FROM horario_semanal WHERE profesional_id = ?", professionalId);
        limpiarBaseDeDatos();
    }

    // ----------------------------------------------------------------------------------
    // Control: el token y la ruta protegida funcionan, asi que un 401 posterior no es del token.
    // ----------------------------------------------------------------------------------

    @Test
    void unTokenValidoPuedeConsultarElHorarioPropio() throws Exception {
        HttpResponse<String> respuesta = enviar("GET", "/api/v1/horarios/" + professionalId, null, tokenDelProfesional());

        assertThat(respuesta.statusCode())
                .as("Control: con token valido la ruta protegida debe responder 200. Respuesta: %s",
                        respuesta.body())
                .isEqualTo(200);
    }

    // ----------------------------------------------------------------------------------
    // S3-03: una ruta sin manejador no debe responder 401 a quien tiene un token valido.
    // ----------------------------------------------------------------------------------

    // Bug conocido S3-03 (Azure #214): falla a proposito hasta que se corrija. Excluido del build por defecto;
    // se ejecuta aparte con: ./mvnw test -Pknown-bugs
    @Tag("known-bug")
    @Test
    void unaRutaInexistenteBajoUnaRutaProtegidaNoDebeResponder401() throws Exception {
        String rutaInexistente = "/api/v1/reservas/ruta-que-no-existe/" + UUID.randomUUID();

        HttpResponse<String> respuesta = enviar("GET", rutaInexistente, null, tokenDeCliente());

        assertThat(respuesta.statusCode())
                .as("Una ruta inexistente con token valido debe responder 404, nunca 401. "
                        + "Respuesta: %s", respuesta.body())
                .isNotEqualTo(401)
                .isEqualTo(404);
    }

    // Bug conocido S3-03 (Azure #214): falla a proposito hasta que se corrija. Excluido del build por defecto;
    // se ejecuta aparte con: ./mvnw test -Pknown-bugs
    @Tag("known-bug")
    @Test
    void unMetodoNoPermitidoNoDebeResponder401() throws Exception {
        // /api/v1/horarios/{id} solo admite GET y PUT; un DELETE es un error del cliente (405).
        HttpResponse<String> respuesta =
                enviar("DELETE", "/api/v1/horarios/" + professionalId, null, tokenDelProfesional());

        assertThat(respuesta.statusCode())
                .as("Un metodo no permitido con token valido debe responder 405, nunca 401. "
                        + "Respuesta: %s", respuesta.body())
                .isNotEqualTo(401)
                .isEqualTo(405);
    }

    // ----------------------------------------------------------------------------------
    // S3-03 derivado: dos reemplazos simultaneos del horario no deben dar 401.
    // ----------------------------------------------------------------------------------

    // Bug conocido S3-03 (Azure #214): falla a proposito hasta que se corrija. Excluido del build por defecto;
    // se ejecuta aparte con: ./mvnw test -Pknown-bugs
    @Tag("known-bug")
    @Test
    void dosReemplazosConcurrentesDelHorarioNoDebenResponder401NiPerderDatosEnSilencio() throws Exception {
        String token = tokenDelProfesional();
        String ruta = "/api/v1/horarios/" + professionalId;
        String horarioA = horario(
                Map.of("dayOfWeek", "MONDAY", "startTime", "09:00", "endTime", "12:00"),
                Map.of("dayOfWeek", "TUESDAY", "startTime", "09:00", "endTime", "12:00"),
                Map.of("dayOfWeek", "WEDNESDAY", "startTime", "09:00", "endTime", "12:00"));
        String horarioB = horario(
                Map.of("dayOfWeek", "MONDAY", "startTime", "14:00", "endTime", "18:00"),
                Map.of("dayOfWeek", "WEDNESDAY", "startTime", "14:00", "endTime", "18:00"));

        List<Integer> codigos = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int ronda = 0; ronda < RONDAS; ronda++) {
                CyclicBarrier salida = new CyclicBarrier(2);
                Callable<Integer> a = () -> {
                    salida.await();
                    return enviar("PUT", ruta, horarioA, token).statusCode();
                };
                Callable<Integer> b = () -> {
                    salida.await();
                    return enviar("PUT", ruta, horarioB, token).statusCode();
                };
                Future<Integer> fa = pool.submit(a);
                Future<Integer> fb = pool.submit(b);
                codigos.add(fa.get());
                codigos.add(fb.get());
            }
        } finally {
            pool.shutdownNow();
        }

        // Esperado: cada reemplazo gana (200) o pierde con un conflicto legible (409).
        assertThat(codigos)
                .as("Dos reemplazos concurrentes del horario solo pueden dar 200 o 409; "
                        + "un 401 (o un 5xx) es el defecto S3-03. Codigos de %d rondas: %s", RONDAS, codigos)
                .allMatch(codigo -> codigo == 200 || codigo == 409);
    }

    // ----------------------------------------------------------------------------------
    // Ayudantes
    // ----------------------------------------------------------------------------------

    private HttpResponse<String> enviar(String metodo, String ruta, String cuerpo, String token)
            throws Exception {
        HttpRequest.BodyPublisher publicador = cuerpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo);
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + port + ruta))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .method(metodo, publicador)
                .build();
        return http.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    @SafeVarargs
    private String horario(Map<String, String>... franjas) throws Exception {
        return objectMapper.writeValueAsString(Map.of("slots", List.of(franjas)));
    }

    /** El profesional sembrado ya existe en {@code app_user}: se firma su token con la clave de la app. */
    private String tokenDelProfesional() {
        UserAccount cuenta = accountRepository.findById(professionalId).orElseThrow();
        return tokenService.createToken(cuenta);
    }

    /** Un cliente cualquiera: {@code JwtAuthenticationFilter} exige que la cuenta exista en la base. */
    private String tokenDeCliente() {
        UserAccount cuenta = accountRepository.save(new UserAccount(
                uniqueEmail("cliente"), "test-not-a-real-password", "Cliente Excepciones", UserRole.CUSTOMER));
        registrarUsuario(cuenta.getId());
        return tokenService.createToken(cuenta);
    }
}

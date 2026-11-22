package com.bookly.backendcf.support;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Clase base de soporte para las pruebas de integracion contra PostgreSQL real.
 *
 * <p>Centraliza el bloque de propiedades que conecta la aplicacion con la base de datos de
 * pruebas ({@code bookly_test} en el puerto 55432) y ofrece los ayudantes de siembra y limpieza
 * que todas las pruebas de integracion del Sprint 3 necesitan.
 *
 * <p><b>Por que una clase base.</b> Las ocho clases de integracion existentes repiten literalmente
 * el mismo bloque de siete propiedades de Spring y cada una reimplementa sus propios metodos
 * {@code insertUser}, {@code insertBooking} y {@code cleanUp}. Esa duplicacion es la causa de que
 * las limpiezas se desincronicen entre clases: el orden de borrado importa porque las claves
 * foraneas van de {@code bloqueo_agenda} a {@code app_user}, y un orden incorrecto deja filas
 * huerfanas que contaminan las corridas siguientes. Alcentralizar la limpieza aqui, el orden por
 * clave foranea queda garantizado y auditable en un unico lugar.
 *
 * <p><b>Contrato de limpieza.</b> {@link #cleanUp(UUID, UUID, UUID)} borra en el orden inverso al
 * de las dependencias:
 * <pre>
 * bloqueo_agenda -> bookings -> horario_semanal -> profesionales -> servicios -> app_user
 * </pre>
 * Los bloqueos de agenda se borran primero porque referencian a {@code profesionales} y a nada mas;
 * las reservas borradas liberan la referencia a {@code app_user} (cliente) y a {@code servicios}.
 *
 * <p><b>Por que el cliente con auditoria no se borra.</b> {@code audit_log} es append-only: la
 * migracion {@code V40} crea el disparador {@code trg_audit_log_immutable} que impide borrar o
 * actualizar sus filas, y la clave foranea {@code fk_audit_log_actor} apunta a {@code app_user}.
 * Un cliente que tenga filas en {@code audit_log} no se puede eliminar sin romper esa integridad, de
 * modo que la limpieza lo conserva. Por eso {@link #uniqueEmail(String)} construye correos con el
 * dominio {@code @bookly.local}, unicamente para que la verificacion de la base de datos pueda
 * contarlos de forma estable.
 *
 * <p><b>Nota sobre la concurrencia.</b> Las pruebas de concurrencia necesitan que la siembra
 * sobreviva a la carrera entre hilos, por eso los ayudantes devuelven el identificador insertado
 * y no dependen de estado mutable compartido.
 *
 * @see com.bookly.backendcf.schedule.application.AgendaBlockIntegrationTest
 */
public abstract class IntegrationTestSupport {

    /**
     * Propiedades de Spring que apuntan la aplicacion a PostgreSQL real de pruebas.
     *
     * <p>Es la unica fuente de verdad del bloque de siete propiedades. Se declara como
     * {@code String[]} para poder inspeccionarla como una lista y para que la lectura del bloque
     * sea explicita.
     *
     * <p><b>Por que no se usa directamente en {@code @SpringBootTest(properties = ...)}.</b> El
     * lenguaje Java no permite desplegar un array constante dentro de una anotacion: los valores
     * de los elementos de una anotacion deben ser expresiones constantes, y una referencia a un
     * campo {@code static final String[]} no lo es (JLS 9.6.2). Referenciar el array desde la
     * anotacion no compila. Por eso el bloque se inyecta con
     * {@link #registrarPropiedadesDePostgres(DynamicPropertyRegistry)}, que Spring hereda a
     * todas las subclases y que produce exactamente el mismo efecto que
     * {@code properties = {...}}: las mismas siete propiedades con la misma precedencia.
     */
    public static final String[] POSTGRES_PROPERTIES = {
            "spring.datasource.url=jdbc:postgresql://localhost:55432/bookly_test",
            "spring.datasource.username=bookly_test",
            "spring.datasource.password=bookly_test_local_2026",
            "spring.flyway.enabled=true",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
            "security.jwt.secret=BooklyIntegrationTestOnlySecretWithMoreThanSixtyFourCharacters123456789"
    };

    /**
     * Inyecta el bloque de propiedades de PostgreSQL en el contexto de Spring.
     *
     * <p>Spring busca este metodo en la clase de prueba y en sus superclases, de modo que todas
     * las clases que extienden {@code IntegrationTestSupport} quedan conectadas a la base real
     * sin repetir el bloque de siete lineas.
     *
     * @param registro registro de propiedades dinamicas de Spring
     */
    @DynamicPropertySource
    static void registrarPropiedadesDePostgres(DynamicPropertyRegistry registro) {
        for (String propiedad : POSTGRES_PROPERTIES) {
            int separador = propiedad.indexOf('=');
            if (separador <= 0) {
                throw new IllegalStateException(
                        "La propiedad de Spring debe tener la forma clave=valor: " + propiedad);
            }
            registro.add(propiedad.substring(0, separador), () -> propiedad.substring(separador + 1));
        }
    }

    /** Acceso a la base de datos real para sembrar y verificar el estado persistido. */
    @Autowired
    protected JdbcTemplate jdbc;

    /** Instante de referencia de la prueba: las marcas de tiempo se derivan de el. */
    protected final OffsetDateTime now = OffsetDateTime.now();

    /** Sufijo unico por clase de prueba, para que dos pruebas no colisionen por correo. */
    protected final String suffix = UUID.randomUUID().toString();

    /** Identificadores de filas sembradas que se limpian al final de la prueba. */
    private final List<UUID> idsDeUsuarios = new ArrayList<>();
    private final List<UUID> idsDeProfesionales = new ArrayList<>();
    private final List<UUID> idsDeServicios = new ArrayList<>();
    private final List<UUID> idsDeVentanas = new ArrayList<>();
    private final List<UUID> idsDeReservas = new ArrayList<>();
    private final List<UUID> idsDeBloqueos = new ArrayList<>();

    /**
     * Correo unico y estable por prueba.
     *
     * <p>Usa el dominio {@code @bookly.local} para que la verificacion de limpieza de la base de
     * datos ({@code SELECT count(*) FROM app_user WHERE email LIKE '%@bookly.local'}) pueda medir
     * exactamente las filas que estas pruebas crean.
     *
     * @param prefix etiqueta legible que identifica al propietario de la fila (por ejemplo
     *               {@code "cliente"} o {@code "admin"})
     * @return un correo que no puede repetir entre pruebas ni entre corridas
     */
    protected String uniqueEmail(String prefix) {
        String base = prefix + "-" + suffix + "-" + UUID.randomUUID() + "@bookly.local";
        return base;
    }

    // ----------------------------------------------------------------------------------
    // Ayudantes de siembra
    // ----------------------------------------------------------------------------------

    /**
     * Inserta un usuario en {@code app_user} y registra su id para la limpieza.
     *
     * @param id identificador del usuario (tambien sera el id del profesional si el rol lo es)
     * @param email correo unico del usuario
     * @param role rol del usuario: {@code CUSTOMER}, {@code PROFESSIONAL} o {@code ADMIN}
     * @param fullName nombre completo que el reporte de ocupacion muestra
     * @return el mismo identificador, para encadenar llamadas
     */
    protected UUID insertUser(UUID id, String email, String role, String fullName) {
        jdbc.update("INSERT INTO app_user (id, email, password_hash, full_name, role, enabled, "
                        + "created_at, updated_at) VALUES (?, ?, 'test-not-a-real-password', ?, ?, true, ?, ?)",
                id, email, fullName, role, now, now);
        idsDeUsuarios.add(id);
        return id;
    }

    /**
     * Registra un usuario creado fuera de esta clase (por ejemplo, con un repositorio JPA) para que
     * la limpieza lo borre.
     *
     * <p>Las pruebas que arrancan MockMvc crean las cuentas con
     * {@code UserAccountRepository.save(...)} porque el token JWT debe firmarse sobre una entidad
     * de dominio. Ese camino no pasa por {@link #insertUser(UUID, String, String, String)}, asi que
     * la cuenta se registra aqui para que la limpieza no la deje huerfana en la base.
     *
     * @param id identificador del usuario a registrar
     * @return el mismo identificador, para encadenar llamadas
     */
    protected UUID registrarUsuario(UUID id) {
        idsDeUsuarios.add(id);
        return id;
    }

    /**
     * Inserta un profesional en {@code profesionales}. El identificador es el mismo que el de su
     * usuario en {@code app_user} porque la clave foranea compuesta {@code (id, rol)} lo exige.
     *
     * @param id identificador del profesional
     * @param email correo del usuario asociado
     * @param fullName nombre completo del profesional
     * @return el mismo identificador, para encadenar llamadas
     */
    protected UUID insertProfessional(UUID id, String email, String fullName) {
        insertUser(id, email, "PROFESSIONAL", fullName);
        jdbc.update("INSERT INTO profesionales (id, rol, especialidad, created_at, updated_at) "
                + "VALUES (?, 'PROFESSIONAL', 'General', ?, ?)", id, now, now);
        idsDeProfesionales.add(id);
        return id;
    }

    /**
     * Inserta un servicio en {@code servicios} con una duracion y un precio validos.
     *
     * @param id identificador del servicio
     * @param name nombre unico del servicio (la base exige unicidad case-insensitive)
     * @param durationMinutes duracion del servicio en minutos
     * @return el mismo identificador, para encadenar llamadas
     */
    protected UUID insertService(UUID id, String name, int durationMinutes) {
        jdbc.update("INSERT INTO servicios (id, nombre, categoria, duracion_minutos, precio, estado, "
                        + "created_at, updated_at) VALUES (?, ?, 'General', ?, ?, 'ACTIVO', ?, ?)",
                id, name, durationMinutes, new BigDecimal("100.00"), now, now);
        idsDeServicios.add(id);
        return id;
    }

    /**
     * Inserta una franja de horario semanal en {@code horario_semanal}.
     *
     * <p>La base de datos solo impide dos franjas que empiecen a la misma hora
     * ({@code uk_horario_semanal_inicio}); el solapamiento parcial lo permite. Esa permision es
     * justamente la causa de los defectos S3-05 (capacidad inflada) y S3-07 (slots duplicados), y
     * esta prueba la necesita para reproducirlos.
     *
     * @param professionalId profesional al que pertenece la franja
     * @param day dia de la semana de la franja
     * @param start hora de inicio
     * @param end hora de fin (debe ser posterior a {@code start})
     * @return el identificador de la franja insertada
     */
    protected UUID insertWeeklyWindow(
            UUID professionalId, java.time.DayOfWeek day, LocalTime start, LocalTime end) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO horario_semanal (id, profesional_id, dia_semana, hora_inicio, "
                        + "hora_fin, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, professionalId, day.name(), start, end, now, now);
        idsDeVentanas.add(id);
        return id;
    }

    /**
     * Inserta una reserva en {@code bookings} con el estado indicado.
     *
     * @param customerId cliente de la reserva
     * @param professionalId profesional de la reserva
     * @param serviceId servicio reservado
     * @param start inicio de la reserva
     * @param end fin de la reserva
     * @param status estado: {@code CONFIRMADA}, {@code CANCELADA}, {@code RESCHEDULED} o
     *               {@code COMPLETED}
     * @return el identificador de la reserva insertada
     */
    protected UUID insertBooking(
            UUID customerId, UUID professionalId, UUID serviceId,
            LocalDateTime start, LocalDateTime end, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO bookings (id, customer_id, professional_id, service_id, start_time, "
                        + "end_time, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, customerId, professionalId, serviceId, start, end, status,
                LocalDateTime.now(), LocalDateTime.now());
        idsDeReservas.add(id);
        return id;
    }

    /**
     * Inserta un bloqueo de agenda en {@code bloqueo_agenda} de forma directa por JDBC.
     *
     * <p>Se inserta por SQL y no por el servicio porque el servicio rechaza los bloqueos en el
     * pasado y porque la restriccion de exclusion {@code bloqueo_agenda_sin_solapamiento} solo se
     * puede exertar de esta forma.
     *
     * @param professionalId profesional al que pertenece el bloqueo
     * @param start inicio del rango bloqueado
     * @param end fin del rango bloqueado
     * @param reason motivo legible del bloqueo
     * @return el identificador del bloqueo insertado
     */
    protected UUID insertAgendaBlock(
            UUID professionalId, LocalDateTime start, LocalDateTime end, String reason) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO bloqueo_agenda (id, profesional_id, inicio, fin, motivo, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", id, professionalId, start, end, reason, now);
        idsDeBloqueos.add(id);
        return id;
    }

    // ----------------------------------------------------------------------------------
    // Limpieza
    // ----------------------------------------------------------------------------------

    /**
     * Limpia las filas sembradas por la prueba en el orden que exigen las claves foraneas.
     *
     * <p>Orden: {@code bloqueo_agenda} -> {@code bookings} -> {@code horario_semanal} ->
     * {@code profesional_servicio} -> {@code profesionales} -> {@code servicios} -> {@code app_user}.
     *
     * <p>El cliente que tenga filas en {@code audit_log} se conserva: {@code audit_log} es
     * append-only y su clave foranea {@code fk_audit_log_actor} impide borrar al actor. Los
     * profesionales y los servicios si se borran siempre, porque los bloqueos de agenda no se
     * auditan.
     *
     * @param idsUsuariosConAuditoria identificadores de usuarios que se conservan si tienen
     *                              auditoria; el metodo decide de forma automatica y el parametro
     *                              documenta la intencion del llamador
     */
    /**
     * Alias de {@link #limpiarBaseDeDatos(UUID...)} con el nombre que usa el resto del suite.
     *
     * <p>Las clases de integracion declaran su propio {@code cleanUp()} como {@code @AfterEach};
     * si el metodo de la clase base tambien se llamara {@code cleanUp}, la invocacion desde la
     * subclase se resolveria contra el metodo de la propia subclase y produciria una recursion
     * infinita. Por eso la logica real vive en {@link #limpiarBaseDeDatos(UUID...)} y este alias
     * ofrece el nombre canonico.
     *
     * @param idsUsuariosConAuditoria identificadores de usuarios que se conservan si tienen auditoria
     */
    protected void cleanUp(UUID... idsUsuariosConAuditoria) {
        limpiarBaseDeDatos(idsUsuariosConAuditoria);
    }

    protected void limpiarBaseDeDatos(UUID... idsUsuariosConAuditoria) {
        // 1. Los bloqueos de agenda referencian a profesionales: primero.
        //    Se borran por identificador sembrado y, ademas, por profesional, porque los
        //    bloqueos que crea el servicio (a traves de AgendaBlockService) no pasan por el
        //    ayudante insertAgendaBlock y no quedan registrados en la lista.
        for (UUID id : idsDeBloqueos) {
            jdbc.update("DELETE FROM bloqueo_agenda WHERE id = ?", id);
        }
        for (UUID id : idsDeProfesionales) {
            jdbc.update("DELETE FROM bloqueo_agenda WHERE profesional_id = ?", id);
        }
        idsDeBloqueos.clear();

        // 2. Las reservas referencian a clientes, profesionales y servicios.
        //    Se borran por identificador sembrado y, ademas, por cliente y por profesional:
        //    asi tambien se limpian las reservas insertadas por JDBC sin registrar (por ejemplo,
        //    la que el caso de S3-10 intenta insertar para comprobar que la base la rechaza).
        for (UUID id : idsDeReservas) {
            jdbc.update("DELETE FROM bookings WHERE id = ?", id);
        }
        for (UUID id : idsDeUsuarios) {
            jdbc.update("DELETE FROM bookings WHERE customer_id = ?", id);
            jdbc.update("DELETE FROM bookings WHERE professional_id = ?", id);
        }
        idsDeReservas.clear();

        // 3. Las franjas de horario referencian a profesionales.
        for (UUID id : idsDeVentanas) {
            jdbc.update("DELETE FROM horario_semanal WHERE id = ?", id);
        }
        idsDeVentanas.clear();

        // 4. La asignacion profesional-servicio referencian a ambos lados.
        for (UUID id : idsDeProfesionales) {
            jdbc.update("DELETE FROM profesional_servicio WHERE profesional_id = ?", id);
        }

        // 5. Los profesionales referencian a su usuario por la clave compuesta (id, rol).
        for (UUID id : idsDeProfesionales) {
            jdbc.update("DELETE FROM profesionales WHERE id = ?", id);
        }
        idsDeProfesionales.clear();

        // 6. Los servicios referencian a las reservas y a la asignacion de profesionales.
        for (UUID id : idsDeServicios) {
            jdbc.update("DELETE FROM profesional_servicio WHERE servicio_id = ?", id);
            jdbc.update("DELETE FROM servicios WHERE id = ?", id);
        }
        idsDeServicios.clear();

        // 7. Los usuarios: solo si no tienen auditoria que los referencie.
        //    Antes hay que liberar las claves foraneas auxiliares que los apuntan
        //    (codigos de recuperacion y dispositivos de confianza de MFA).
        for (UUID id : idsDeUsuarios) {
            Integer filasDeAuditoria = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_log WHERE actor_user_id = ?", Integer.class, id);
            if (filasDeAuditoria == null || filasDeAuditoria == 0) {
                jdbc.update("DELETE FROM mfa_recovery_code WHERE user_id = ?", id);
                jdbc.update("DELETE FROM mfa_trusted_device WHERE user_id = ?", id);
                jdbc.update("DELETE FROM app_user WHERE id = ?", id);
            }
        }
        idsDeUsuarios.clear();
    }

    /**
     * Cuenta las filas de auditoria de un actor.
     *
     * @param actorUserId usuario cuyo rastro de auditoria se quiere medir
     * @return numero de filas en {@code audit_log} cuyo {@code actor_user_id} es el indicado
     */
    protected long countAuditEntries(UUID actorUserId) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE actor_user_id = ?", Long.class, actorUserId);
        return total == null ? 0L : total;
    }
}

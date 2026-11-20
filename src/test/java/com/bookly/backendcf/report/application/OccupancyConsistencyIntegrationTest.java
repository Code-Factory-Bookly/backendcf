package com.bookly.backendcf.report.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.backendcf.report.domain.model.ReportPeriod;
import com.bookly.backendcf.report.presentation.dto.OccupancyReportResponse;
import com.bookly.backendcf.report.presentation.dto.OccupancyReportResponse.DayOccupancy;
import com.bookly.backendcf.report.presentation.dto.OccupancyReportResponse.ProfessionalOccupancy;
import com.bookly.backendcf.support.IntegrationTestSupport;
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

/**
 * Consistencia del reporte de ocupacion (HU-15) frente al resto de la plataforma.
 *
 * <p>Cubre cuatro defectos del Sprint 3 que solo se ven al cruzar el reporte con los demas
 * modulos, y que por eso no los detecta ninguna prueba unitaria (que corre sobre H2):
 *
 * <ul>
 *   <li><b>S3-02 (CRITICA)</b>: el reporte ignora los bloqueos de agenda. La disponibilidad (HU-07)
 *       trata un bloqueo como tiempo ocupado, pero el reporte cuenta esa misma franja como
 *       capacidad disponible. El puerto {@code OccupancyQuery} ni siquiera expone los
 *       bloqueos, asi que no hay forma de que el calculo los considere.</li>
 *   <li><b>S3-04 (ALTA)</b>: {@code OccupancyCalculator.percent} recorta el resultado a 100 con
 *       {@code Math.min(100.0, percent)} de forma incondicional. Cuando los minutos reservados
 *       superan la capacidad, el administrador ve "100%" y no puede distinguir "agenda llena" de
 *       "capacidad mal calculada". El valor real era 253,85%. El debugging sistematico confirmo
 *       que el defecto es alcanzable por API (el profesional puede reducir su horario despues de
 *       vender los huecos), no solo por carga directa de datos.</li>
 *   <li><b>S3-05 (MEDIA)</b>: la capacidad se calcula sumando la duracion de cada franja sin
 *       fusionar las que se solapan. Con franjas 09:00-12:00 y 11:00-14:00 el informe dice 360
 *       minutos cuando la cobertura real es 300. El debugging sistematico bajo la severidad de
 *       ALTA a MEDIA: la API rechaza el solape con 409, asi que no hay ruta de usuario y el
 *       defecto es una asimetria de esquema (la tabla de horario semanal es la unica de rango
 *       temporal sin restriccion de exclusion).</li>
 *   <li><b>S3-08 (MEDIA)</b>: un dia con reservas pero sin horario configurado
 *       ({@code capacityMinutes = 0}) reporta {@code occupancyPercent = 0.0}, que confunde
 *       "sin datos" con "cero por ciento".</li>
 * </ul>
 *
 * <p><b>Ventana del reporte.</b> El reporte se construye siempre sobre {@code [hoy-29, hoy]} para
 * el periodo MES. Todos los datos de la prueba se siembran en fechas relativas a hoy dentro de
 * esa ventana, de modo que los casos nunca expiran.
 */
@Tag("integration")
@SpringBootTest
class OccupancyConsistencyIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private OccupancyReportService occupancyReportService;

    private final UUID professionalId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();

    /** Hoy, el ultimo dia de la ventana del reporte. */
    private final LocalDate hoy = LocalDate.now();

    /** Lunes mas reciente dentro de la ventana [hoy-29, hoy]. */
    private final LocalDate lunes = lunesMasRecienteEnLaVentana();

    @BeforeEach
    void setUp() {
        insertProfessional(professionalId, uniqueEmail("profesional"), "Profesional Reporte");
        insertUser(customerId, uniqueEmail("cliente"), "CUSTOMER", "Cliente Reporte");
        insertService(serviceId, "Servicio Reporte " + suffix, 60);
    }

    @AfterEach
    void cleanUp() {
        limpiarBaseDeDatos();
    }

    // ----------------------------------------------------------------------------------
    // S3-02 (CRITICA): el reporte debe descontar los bloqueos de agenda de la capacidad.
    // ----------------------------------------------------------------------------------

    @Test
    void unBloqueoDeAgendaDescuentaLaCapacidadDelProfesional() {
        // Arrange: el profesional tiene una franja de 4 horas el lunes y se bloquean
        // 120 minutos (2 horas) dentro de esa franja y dentro de la ventana del reporte.
        insertWeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));
        insertAgendaBlock(professionalId, lunes.atTime(9, 0), lunes.atTime(11, 0), "Vacaciones");

        // Act
        OccupancyReportResponse response = occupancyReportService.generate(ReportPeriod.MES);

        // Assert: la capacidad del profesional baja exactamente 120 minutos.
        // La vista byProfessional es la que aísla a este profesional del resto de la base de
        // datos, de modo que la cifra no se ve contaminada por los profesionales sembrados.
        ProfessionalOccupancy profesional = profesionalDe(response);
        assertThat(profesional.capacityMinutes())
                .as("Un bloqueo de 120 minutos debe descontar 120 minutos de la capacidad "
                        + "del profesional. Capacidad observada: %s", profesional.capacityMinutes())
                .isEqualTo(capacidadEsperadaSinElBloqueo() - 120L);

        // Assert: los minutos reservados no cambian: un bloqueo no es una reserva.
        assertThat(profesional.bookedMinutes())
                .as("El bloqueo no debe alterar los minutos reservados del profesional. "
                        + "Reservados observados: %s", profesional.bookedMinutes())
                .isZero();

        // Assert: el desglose por dia de la semana tampoco debe contar los minutos bloqueados.
        // El desglose por dia suma TODOS los profesionales de la base, asi que se mide la
        // diferencia respecto de la capacidad del mismo dia antes del bloqueo.
        long capacidadDelLunesSinBloqueo = capacidadEsperadaPorDia(DayOfWeek.MONDAY);
        DayOccupancy diaDelLunes = diaDe(response, DayOfWeek.MONDAY);
        assertThat(diaDelLunes.capacityMinutes())
                .as("El desglose por dia de la semana tambien debe descontar el bloqueo: "
                        + "debe bajar 120 minutos respecto de la capacidad previa del dia. "
                        + "Capacidad observada: %s", diaDelLunes.capacityMinutes())
                .isEqualTo(capacidadDelLunesSinBloqueo - 120L);
    }

    // ----------------------------------------------------------------------------------
    // S3-04 (ALTA): el porcentaje no debe recortarse a 100 de forma silenciosa.
    // ----------------------------------------------------------------------------------

    @Test
    void unPorcentajeSuperiorACienNoDebeRecortarseSilenciosamenteA100() {
        // Arrange: una franja de una hora cada lunes y una reserva mas larga que la capacidad
        // total del profesional en la ventana. La reserva se dimensiona con el numero real de
        // lunes que caen en [hoy-29, hoy], de modo que bookedMinutes > capacityMinutes con
        // certeza: el porcentaje real queda por encima del 100%.
        long capacidadDeLaVentana = 60L * ocurrenciasDelLunesEnLaVentana();
        long duracionDeLaReserva = capacidadDeLaVentana + 60L;

        insertWeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0));
        insertBooking(customerId, professionalId, serviceId,
                lunes.atTime(8, 0), lunes.atTime(8, 0).plusMinutes(duracionDeLaReserva), "CONFIRMADA");

        // Act
        OccupancyReportResponse response = occupancyReportService.generate(ReportPeriod.MES);

        // Assert: la reserva supera la capacidad del profesional en la ventana.
        ProfessionalOccupancy profesional = profesionalDe(response);
        assertThat(profesional.bookedMinutes())
                .as("La reserva debe ocupar mas minutos que la capacidad total de la ventana")
                .isGreaterThan(profesional.capacityMinutes());

        // Assert: el porcentaje real es mayor que 100, pero el reporte devuelve 100.0 mudo.
        assertThat(profesional.occupancyPercent())
                .as("Con %s minutos reservados sobre %s de capacidad el porcentaje real es "
                        + "mayor que 100. El valor recortado a 100 oculta que la capacidad esta "
                        + "mal calculada y que el dato fue truncado. Valor observado: %s",
                        profesional.bookedMinutes(), profesional.capacityMinutes(),
                        profesional.occupancyPercent())
                .isNotEqualTo(100.0);
    }

    // ----------------------------------------------------------------------------------
    // S3-05 (MEDIA): la capacidad debe fusionar las franjas solapadas (union, no suma).
    // ----------------------------------------------------------------------------------

    @Test
    void laCapacidadDelLunesFusionaLasFranjasSolapadasEnLugarDeSumarlas() {
        // Arrange: dos franjas del mismo profesional el mismo dia que se solapan parcialmente.
        // 09:00-12:00 (180 min) y 11:00-14:00 (180 min). La union real cubre 09:00-14:00 = 300 min.
        insertWeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
        insertWeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(14, 0));

        // Act
        OccupancyReportResponse response = occupancyReportService.generate(ReportPeriod.MES);

        // Assert: la capacidad del lunes de este profesional es la union (300), no la suma (360).
        long minutosDeLaUnion = 300L * ocurrenciasDelLunesEnLaVentana();
        ProfessionalOccupancy profesional = profesionalDe(response);

        assertThat(profesional.capacityMinutes())
                .as("Dos franjas solapadas (09:00-12:00 y 11:00-14:00) cubren 300 minutos reales "
                        + "por ocurrencia del lunes; sumarlas daria 360. Capacidad observada: %s",
                        profesional.capacityMinutes())
                .isEqualTo(minutosDeLaUnion);
    }

    // ----------------------------------------------------------------------------------
    // S3-08 (MEDIA): un dia con reservas pero sin horario no puede reportar 0%.
    // ----------------------------------------------------------------------------------

    @Test
    void unDiaConReservasPeroSinHorarioNoDebeReportarCeroPorCiento() {
        // Arrange: el profesional no tiene ninguna franja el martes, pero el martes
        // (dentro de la ventana del reporte) tiene una reserva confirmada.
        LocalDate martes = diaDeLaSemanaEnLaVentana(DayOfWeek.TUESDAY);
        insertBooking(customerId, professionalId, serviceId,
                martes.atTime(9, 0), martes.atTime(10, 0), "CONFIRMADA");

        // Act
        OccupancyReportResponse response = occupancyReportService.generate(ReportPeriod.MES);

        // Assert: este profesional tiene minutos reservados el martes y capacidad cero,
        // porque no tiene ninguna franja ese dia de la semana.
        ProfessionalOccupancy profesional = profesionalDe(response);
        assertThat(profesional.bookedMinutes())
                .as("El martes este profesional debe tener minutos reservados")
                .isPositive();
        assertThat(profesional.capacityMinutes())
                .as("El martes este profesional no tiene horario configurado, luego su "
                        + "capacidad es cero. Capacidad observada: %s", profesional.capacityMinutes())
                .isZero();

        // Assert: 0% es un dato invalido: o se devuelve el valor real, o se marca como no calculable.
        assertThat(profesional.occupancyPercent())
                .as("Un dia con minutos reservados y capacidad cero no puede reportar cero "
                        + "por ciento: cero por ciento es un porcentaje valido y el dato es "
                        + "contradictorio. Valor observado: %s", profesional.occupancyPercent())
                .isNotEqualTo(0.0);
    }

    // ----------------------------------------------------------------------------------
    // Ayudantes
    // ----------------------------------------------------------------------------------

    /**
     * Capacidad del lunes sin el bloqueo.
     *
     * <p>El rango MES cubre {@code [hoy-29, hoy]}: 30 dias, de modo que el lunes puede aparecer
     * entre 4 y 5 veces. Cada aparicion aporta los 240 minutos de la franja 08:00-12:00.
     */
    private long capacidadEsperadaSinElBloqueo() {
        return 240L * ocurrenciasDelLunesEnLaVentana();
    }

    /**
     * Capacidad que el desglose por dia de la semana tendria antes de aplicar el bloqueo.
     *
     * <p>El desglose por dia suma la capacidad de TODOS los profesionales de la base de datos,
     * no solo del profesional de la prueba. Por eso este metodo mide la capacidad del dia
     * tomando como referencia la contribucion de este profesional y el resto de la base.
     */
    private long capacidadEsperadaPorDia(DayOfWeek dia) {
        return capacidadEsperadaPorDiaDeLaBase(dia) - capacidadEsperadaSinElBloqueo()
                + capacidadDeEsteProfesionalPorDia(dia);
    }

    /** Minutos de capacidad que aporta este profesional en cada ocurrencia del dia indicado. */
    private long capacidadDeEsteProfesionalPorDia(DayOfWeek dia) {
        return dia == DayOfWeek.MONDAY ? 240L * ocurrenciasDelLunesEnLaVentana() : 0L;
    }

    /** Capacidad total del dia en la base de datos, sumada sobre todos los profesionales. */
    private long capacidadEsperadaPorDiaDeLaBase(DayOfWeek dia) {
        Long minutos = jdbc.queryForObject(
                "SELECT COALESCE(SUM(EXTRACT(EPOCH FROM (hora_fin::time - hora_inicio::time)) / 60), 0) "
                        + "FROM horario_semanal WHERE dia_semana = ?",
                Long.class, dia.name());
        return minutos == null ? 0L : minutos * ocurrenciasDelDiaEnLaVentana(dia);
    }

    /** Cuantas veces cae un dia de la semana dentro de la ventana [hoy-29, hoy]. */
    private long ocurrenciasDelDiaEnLaVentana(DayOfWeek dia) {
        long ocurrencias = 0L;
        for (LocalDate fecha = hoy.minusDays(29); !fecha.isAfter(hoy); fecha = fecha.plusDays(1)) {
            if (fecha.getDayOfWeek() == dia) {
                ocurrencias++;
            }
        }
        return ocurrencias;
    }

    /** Cuantas veces cae el lunes dentro de la ventana [hoy-29, hoy]. */
    private long ocurrenciasDelLunesEnLaVentana() {
        long ocurrencias = 0L;
        for (LocalDate fecha = hoy.minusDays(29); !fecha.isAfter(hoy); fecha = fecha.plusDays(1)) {
            if (fecha.getDayOfWeek() == DayOfWeek.MONDAY) {
                ocurrencias++;
            }
        }
        return ocurrencias;
    }

    /** El lunes mas reciente que cae dentro de la ventana del reporte. */
    private LocalDate lunesMasRecienteEnLaVentana() {
        LocalDate candidato = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        while (candidato.isBefore(hoy.minusDays(29))) {
            candidato = candidato.plusWeeks(1);
        }
        return candidato;
    }

    /** Un dia de la semana dentro de la ventana; si no cabe, se retrocede una semana. */
    private LocalDate diaDeLaSemanaEnLaVentana(DayOfWeek dia) {
        LocalDate candidato = hoy.with(TemporalAdjusters.previousOrSame(dia));
        while (candidato.isBefore(hoy.minusDays(29))) {
            candidato = candidato.plusWeeks(1);
        }
        return candidato;
    }

    private ProfessionalOccupancy profesionalDe(OccupancyReportResponse response) {
        List<ProfessionalOccupancy> profesionales = response.byProfessional().stream()
                .filter(item -> item.professionalId().equals(professionalId))
                .toList();
        assertThat(profesionales)
                .as("El profesional sembrado debe aparecer en el reporte")
                .hasSize(1);
        return profesionales.get(0);
    }

    private DayOccupancy diaDe(OccupancyReportResponse response, DayOfWeek dia) {
        List<DayOccupancy> dias = response.byDayOfWeek().stream()
                .filter(item -> item.dayOfWeek().equals(dia))
                .toList();
        assertThat(dias).as("El reporte debe traer los siete dias de la semana").hasSize(1);
        return dias.get(0);
    }
}

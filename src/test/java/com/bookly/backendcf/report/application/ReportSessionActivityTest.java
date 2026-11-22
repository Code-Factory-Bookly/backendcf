package com.bookly.backendcf.report.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/**
 * Expiración por inactividad de la sesión del reporte general (HU-16), escenario "acceso con MFA
 * y expiración por inactividad".
 *
 * <p>Para no esperar 15 minutos, los casos de expiración construyen el componente con un límite
 * de 0 minutos: cualquier segunda consulta posterior a la primera ya supera la inactividad
 * permitida. El valor por defecto de 15 minutos se comprueba con el componente que
 * construye Spring en {@code GeneralReportAuditIntegrationTest}.
 */
@Tag("unit")
class ReportSessionActivityTest {

    private static final String MENSAJE = "La sesión del reporte expiró por inactividad";

    private final UUID admin = UUID.randomUUID();

    /** Deja pasar un instante para que "ahora" sea estrictamente posterior a la última consulta. */
    private static void dejarPasarElTiempo() throws InterruptedException {
        Thread.sleep(20);
    }

    @Test
    void laPrimeraConsultaSeAceptaYLasSiguientesDentroDelLimiteTambien() {
        ReportSessionActivity actividad = new ReportSessionActivity(15);

        assertThatCode(() -> {
            actividad.touch(admin, "sesion-1");
            actividad.touch(admin, "sesion-1");
            actividad.touch(admin, "sesion-1");
        }).doesNotThrowAnyException();
    }

    @Test
    void unaConsultaDespuesDelLimiteDeInactividadSeRechaza() throws InterruptedException {
        ReportSessionActivity actividad = new ReportSessionActivity(0);
        actividad.touch(admin, "sesion-1");
        dejarPasarElTiempo();

        assertThatThrownBy(() -> actividad.touch(admin, "sesion-1"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage(MENSAJE);
    }

    @Test
    void unaSesionExpiradaSigueRechazadaAunqueVuelvaAConsultar() throws InterruptedException {
        ReportSessionActivity actividad = new ReportSessionActivity(0);
        actividad.touch(admin, "sesion-1");
        dejarPasarElTiempo();
        assertThatThrownBy(() -> actividad.touch(admin, "sesion-1"))
                .isInstanceOf(AccessDeniedException.class);

        // La expiración queda registrada: reintentar con la misma credencial no la revive.
        assertThatThrownBy(() -> actividad.touch(admin, "sesion-1"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage(MENSAJE);
    }

    @Test
    void laExpiracionDeUnaSesionNoAfectaANuevasSesionesDelMismoUsuario() throws InterruptedException {
        ReportSessionActivity actividad = new ReportSessionActivity(0);
        actividad.touch(admin, "sesion-1");
        dejarPasarElTiempo();
        assertThatThrownBy(() -> actividad.touch(admin, "sesion-1"))
                .isInstanceOf(AccessDeniedException.class);

        assertThatCode(() -> actividad.touch(admin, "sesion-2")).doesNotThrowAnyException();
    }

    @Test
    void lasSesionesDeDistintosAdministradoresSonIndependientes() throws InterruptedException {
        ReportSessionActivity actividad = new ReportSessionActivity(0);
        UUID otroAdmin = UUID.randomUUID();
        actividad.touch(admin, "sesion-a");
        dejarPasarElTiempo();
        assertThatThrownBy(() -> actividad.touch(admin, "sesion-a"))
                .isInstanceOf(AccessDeniedException.class);

        assertThatCode(() -> actividad.touch(otroAdmin, "sesion-b")).doesNotThrowAnyException();
    }

    @Test
    void sinClaveDeSesionSeUsaElIdentificadorDelAdministrador() throws InterruptedException {
        ReportSessionActivity actividad = new ReportSessionActivity(0);
        actividad.touch(admin, null);
        dejarPasarElTiempo();

        // Una clave nula y una en blanco se tratan igual: ambas caen en el id del actor.
        assertThatThrownBy(() -> actividad.touch(admin, "   "))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage(MENSAJE);
    }
}

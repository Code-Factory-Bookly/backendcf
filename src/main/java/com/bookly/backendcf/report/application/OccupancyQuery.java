package com.bookly.backendcf.report.application;

import com.bookly.backendcf.report.domain.model.BookedPeriod;
import com.bookly.backendcf.report.domain.model.WeeklyWindow;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Puerto de lectura del reporte de ocupación (HU-15). Solo consulta la base de datos de la plataforma. */
public interface OccupancyQuery {

    /** Ids de todos los profesionales registrados. */
    List<UUID> findProfessionalIds();

    /** Franjas semanales de todos los profesionales. */
    List<WeeklyWindow> findWeeklyWindows();

    /** Reservas CONFIRMADA o COMPLETED cuyo inicio cae en [from, to). */
    List<BookedPeriod> findOccupyingBookings(LocalDateTime from, LocalDateTime to);

    /** Nombre completo de cada profesional por id. */
    Map<UUID, String> professionalNames(Collection<UUID> professionalIds);
}
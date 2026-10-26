package com.bookly.backendcf.booking.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Resuelve los nombres que muestran las vistas de reservas, sin acoplar booking a auth ni catalog. */
public interface BookingNameDirectory {

    /** Nombre completo de cuentas (clientes o profesionales) por id de cuenta. */
    Map<UUID, String> accountNames(Collection<UUID> accountIds);

    /** Nombre de servicios por id de servicio. */
    Map<UUID, String> serviceNames(Collection<UUID> serviceIds);
}

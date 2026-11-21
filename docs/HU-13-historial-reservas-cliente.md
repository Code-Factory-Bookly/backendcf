# HU-13 – Historial de reservas del cliente

## Decisión de diseño

Se conserva la ruta existente `/api/v1/reservas/cliente/{customerId}` para compatibilidad con consumidores actuales. Se agrega `/api/v1/reservas/cliente/historial` para la vista del cliente autenticado.

La nueva ruta obtiene el identificador del cliente desde el token JWT. Por tanto, el cliente no puede solicitar el historial de otra cuenta. Solo se permite el rol `CUSTOMER`.

## Consulta

La ruta permite consultar reservas pasadas y futuras con los siguientes parámetros opcionales:

- `from` y `to`: rango inclusivo sobre `startTime`.
- `status`: `CONFIRMADA`, `CANCELADA`, `RESCHEDULED` o `COMPLETED`.
- `page` y `size`: paginación; el tamaño máximo es 100.
- `order`: `asc` o `desc`; por defecto ordena cronológicamente de forma ascendente.

La respuesta incluye fecha, servicio, profesional y estado, además de los metadatos de paginación. Cuando no existen reservas, `content` es una lista vacía y `message` informa al cliente.

## Persistencia

La consulta se ejecuta en PostgreSQL mediante una consulta paginada. Se reutiliza el índice existente sobre `bookings.customer_id`; no se agrega una migración nueva hasta contar con evidencia de `EXPLAIN ANALYZE` que justifique un índice adicional.

## Validación para Calidad

Calidad debe validar, como mínimo:

1. Un cliente autenticado solo obtiene sus propias reservas.
2. Un cliente no puede consultar la ruta de historial con otro identificador porque la ruta no recibe `customerId`.
3. El endpoint rechaza usuarios sin rol `CUSTOMER`.
4. El historial incluye reservas pasadas, futuras y canceladas.
5. Los filtros por fecha y estado funcionan correctamente.
6. El orden ascendente y descendente es estable.
7. La paginación respeta `page`, `size` y el máximo de 100.
8. Una cuenta sin reservas recibe `content: []` y un mensaje informativo.

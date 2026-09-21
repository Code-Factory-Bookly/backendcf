# HU-06 - Configuración de duración estándar del servicio

## Resumen

Permite al administrador consultar y actualizar la duración (en minutos) de un servicio.
La duración es obligatoria, entera y debe estar entre 1 y 480 minutos.

## Endpoint

```
PUT /api/v1/servicios/{id}
Authorization: Bearer <token_admin>
Content-Type: application/json

{
  "name": "Limpieza dental",
  "description": "Limpieza profesional",
  "category": "Odontología",
  "durationMinutes": 45,
  "price": 80000
}
```

### Respuestas

| Código | Escenario                          |
|--------|------------------------------------|
| 200    | Actualización exitosa              |
| 400    | Duración inválida (0, negativa, >480, nula) |
| 401    | No autenticado                     |
| 403    | No es ADMIN                        |
| 404    | Servicio inexistente               |
| 409    | Nombre duplicado                   |

## Validaciones

- **Aplicación (DTO):** `@Min(1)`, `@Max(480)`, `@NotNull`
- **Base de datos (CHECK):** `duracion_minutos > 0 AND duracion_minutos <= 480`

## Autorización

Solo usuarios con rol `ADMIN` pueden modificar servicios.
Configurado en `SecurityConfiguration`:

```
.requestMatchers(HttpMethod.GET, "/api/v1/servicios/**").permitAll()
.requestMatchers("/api/v1/servicios/**").hasRole("ADMIN")
```

## Integración con cálculo de disponibilidad (agenda)

El campo `duracion_minutos` será consumido por el módulo de agendas (HU-07 y relacionadas)
para dividir los horarios disponibles de los profesionales en bloques reservables.

### Flujo previsto

1. El profesional define su horario (ej: 08:00 – 17:00)
2. El sistema consulta `duracion_minutos` del servicio solicitado
3. Genera bloques de tiempo: `inicio`, `inicio + duracion_minutos`, ...
4. Los bloques ya ocupados por otras reservas se excluyen
5. El cliente ve solo los bloques libres

### Ejemplo

- Servicio: Limpieza dental (45 min)
- Horario profesional: 08:00 – 12:00
- Bloques generados: 08:00, 08:45, 09:30, 10:15, 11:00, 11:45

> **Nota:** La implementación concreta depende de la HU-07 (gestión de agendas)
> y la HU de reservas. Esta documentación define el contrato de uso del dato.

## Integridad de datos

La duración queda protegida por las validaciones del DTO y la restricción
`CHECK` definida en la migración del catálogo de servicios.

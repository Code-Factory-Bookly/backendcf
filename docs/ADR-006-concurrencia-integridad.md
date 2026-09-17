# ADR-006: Concurrencia e integridad en operaciones únicas

- **Estado:** Aceptada
- **Fecha:** 2026-09-16
- **Responsables:** Arquitectura y Base de Datos
- **Ámbito:** HU-01 y HU-20

## Contexto

La validación previa de existencia (`existsByEmail` o `count`) no es suficiente cuando dos
solicitudes llegan simultáneamente. Ambas pueden observar que el dato no existe y tratar de
insertarlo.

## Decisión

La base de datos conserva la autoridad final mediante restricciones únicas:

- `uk_app_user_email` para impedir dos cuentas con el mismo correo.
- `uk_platform_singleton` sobre `platform ((true))` para impedir más de una plataforma.

Los casos de uso ejecutan `saveAndFlush()` para detectar la violación dentro de la operación y la
traducen a un error de dominio estable:

- `EMAIL_ALREADY_REGISTERED` (`409`) en HU-01.
- `PLATFORM_ALREADY_CONFIGURED` (`409`) en HU-20.

La transacción se revierte ante la excepción, evitando registros parciales.

## Consecuencias

- La aplicación no depende únicamente de una comprobación previa en memoria.
- Las respuestas HTTP mantienen el contrato funcional aun bajo concurrencia.
- PostgreSQL sigue siendo la fuente de verdad para la unicidad.
- Las pruebas de concurrencia y carga deberán ser definidas por el rol de Calidad cuando corresponda.

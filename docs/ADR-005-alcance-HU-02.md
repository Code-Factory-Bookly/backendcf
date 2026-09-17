# ADR-005: Alcance reducido de HU-02

- **Estado:** Aceptada
- **Fecha:** 2026-09-16
- **Responsables:** Arquitectura y Base de Datos
- **Ámbito:** Sprint 1, Caso 14

## Contexto

La versión inicial de HU-02 mezclaba la configuración general de la plataforma, datos de contacto,
especialidades y el catálogo de servicios. La configuración inicial de la plataforma ya pertenece a
HU-20 y la implementación de reservas aún no requiere una entidad independiente para especialidades.

Mantener todos esos conceptos dentro de HU-02 produciría duplicidad con HU-20 y agregaría tablas y
endpoints sin reglas de negocio necesarias para este sprint.

## Decisión

HU-02 queda formalmente delimitada al catálogo de servicios. Incluye:

- Registrar servicios como administrador (`ADMIN`).
- Consultar públicamente el catálogo.
- Consultar un servicio por identificador.
- Filtrar servicios por categoría.
- Actualizar, activar/desactivar y eliminar servicios.
- Validar nombre, categoría, duración y precio.
- Mantener restricciones de integridad en PostgreSQL.

La especialidad se representa en esta versión mediante el campo `categoria` de `servicios`; no se
crea una tabla ni un CRUD independiente de especialidades.

Quedan fuera de HU-02:

- Nombre comercial y datos de contacto generales de la plataforma; pertenecen a HU-20.
- Perfil de organización, NIT y configuraciones multi-tenant.
- Entidad independiente de especialidades.
- Asociación de profesionales, agendas y reservas.

## Consecuencias

- El modelo físico de HU-02 se mantiene en una tabla `servicios`.
- Se evita duplicar la configuración de `platform` definida en HU-20.
- Los clientes pueden buscar por categoría sin una relación adicional.
- Si las especialidades requieren ciclo de vida propio —por ejemplo, renombrar, fusionar o asignar
  permisos— deberá crearse una nueva HU y un ADR que justifique la migración a una entidad separada.

## Trazabilidad

- Requisitos funcionales: `Historias_de_usuario_azure.md`, sección HU-02.
- Implementación: `src/main/java/com/bookly/backendcf/catalog`.
- Persistencia: `servicios`, definida por la migración correspondiente.
- Evidencia manual: `docs/evidencia/HU-02.http`.

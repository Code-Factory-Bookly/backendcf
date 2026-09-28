# ADR-008: Estrategia de pruebas de concurrencia/carga y deuda de contraseña inicial de profesionales

- **Estado:** Aceptada
- **Fecha:** 2026-09-22
- **Responsables:** Calidad
- **Ámbito:** Sprint 1 (decisión de alcance), deuda registrada para Sprint 2/3

## Contexto

Dos pendientes quedaron abiertos en ADR de Arquitectura y Base de Datos, sin que Calidad los hubiera
tomado formalmente hasta ahora:

1. [ADR-006](ADR-006-concurrencia-integridad.md) delega explícitamente a Calidad: *"Las pruebas de  concurrencia y carga deberán ser definidas por el rol de Calidad cuando corresponda."* Sin sprint ni   fecha asignada, quedó como compromiso abierto sin dueño ni trazabilidad.
2. [ADR-007](ADR-007-registro-profesionales.md) documenta que el administrador conoce la contraseña  inicial del profesional que registra, sin flujo de invitación ni canal seguro de entrega, marcándolo   como *"deuda de seguridad registrada para una HU posterior"* — pero esa deuda nunca se incorporó a
   `README_QA.md` ni a `Plan_Calidad_Software.md`.

Ambos correspondían al rol de Calidad por naturaleza (uno delegado explícitamente, el otro por ser un riesgo de seguridad) y necesitaban una decisión formal en vez de quedar mencionados de paso en un ADR
de otro rol.

## Decisión

**1. Pruebas de concurrencia y carga: se difieren explícitamente a Sprint 3.**

Razones:

- `Lineamientos.md` 3.7 ubica "Automatización de criterios de aceptación y ejecución E2E" en Sprint 3, no en Sprint 1 ni 2 — probar concurrencia real contra la base de datos es ese mismo nivel de prueba.
- Clase 6 del curso de caldiad donde el docente entrega isntrucciones al respecto, es explícita: "una prueba de carga real requiere herramientas como JMeter, capaces
  de simular miles de peticiones concurrentes; un puñado de peticiones manuales o de hilos en JUnit no  representa estrés real para el servidor y daría una falsa sensación de cobertura."
- Los índices únicos que sostienen la integridad bajo concurrencia (`uk_app_user_email`,
  `uk_platform_singleton`, `uk_servicios_nombre`, `uk_app_user_id_role`) ya están revisados por código y
  se ejecutaron manualmente contra PostgreSQL real (validación end-to-end del 21/09, `docker compose` +
  Flyway real) — no hay evidencia de que estén rotos, solo de que no están cubiertos por test
  automatizado todavía.
- A un día del cierre de Sprint 1 no es prudente introducir infraestructura de test nueva
  (Testcontainers, JMeter) sin tiempo para validarla.

En su lugar, se prioriza para lo que resta de Sprint 1/entrada de Sprint 2: corregir el patrón
check-then-act pendiente en `RegisterCustomerService`, `PlatformSetupService` y `ServiceOfferingService`
(usando `RegisterProfessionalService` como referencia, ver ADR-006) — mitiga el riesgo real de
concurrencia sin requerir infraestructura de carga.

**2. Deuda de contraseña inicial: se registra formalmente como riesgo de seguridad, severidad Media.**

Severidad Media (escala de Clase 4: funciona, con problema notable) y no Crítica, porque no es una vulnerabilidad explotable remotamente — depende de que el canal de comunicación entre administrador y  profesional ya esté comprometido. Se resuelve junto con HU-17 (roles y permisos) o antes, mediante un
flujo de invitación con token de un solo uso, cuando exista un proveedor de correo real
(`WelcomeNotificationPort` hoy solo escribe en el log). Registrado en `Plan_Calidad_Software.md`,
sección 13.

## Alternativas consideradas

| Alternativa | Motivo de descarte |
|---|---|
| Tests de concurrencia con hilos manuales en JUnit, sin JMeter | No representa carga real (Clase 6); daría falsa sensación de cobertura sin medir tiempos de respuesta ni límites reales |
| Testcontainers + PostgreSQL real para probar los índices únicos bajo concurrencia, ya en Sprint 1 | Técnicamente válido, pero es el nivel de integración que `Lineamientos.md` 3.7 reserva para Sprint 3 — adelantarlo a 1 día del cierre no es prudente |
| Resolver la deuda de contraseña inicial ahora (contraseña aleatoria + cambio obligatorio) | Requiere un flujo de cambio de contraseña que no existe todavía (ninguna HU lo cubre); mejor documentarla y resolverla con alcance completo en Sprint 2/3 |
| Dejar ambos pendientes sin decisión formal, como estaban | Es la opción que motivó este ADR — un compromiso delegado sin dueño ni fecha tiende a perderse, como de hecho pasó hasta hoy |

## Consecuencias

- El compromiso abierto de ADR-006 queda cerrado con una decisión explícita y trazable, no como un
  pendiente flotante sin dueño.
- La deuda de seguridad de ADR-007 pasa a estar visible en el documento de riesgos de Calidad, no solo
  enterrada en un ADR de Arquitectura.
- Sprint 3 hereda dos tareas concretas y con dueño: (1) pruebas de concurrencia/carga con JMeter sobre
  los índices únicos reales, (2) flujo de invitación/cambio de contraseña para profesionales.
- No se agrega infraestructura de test nueva en Sprint 1 — cero riesgo de romper el cierre del sprint.

## Trazabilidad

- Depende de: [ADR-006](ADR-006-concurrencia-integridad.md), [ADR-007](ADR-007-registro-profesionales.md).


### edita y revisa.
Redactado con deepseek revisado y editado por Adrian Espinosas -22/09-22:20
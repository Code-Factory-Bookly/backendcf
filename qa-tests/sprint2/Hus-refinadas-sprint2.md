# Historias de usuario refinadas — Sprint 2 (BOOKLY)

> **Versión:** Refinamiento post-implementación  
> **Fecha:** 2026-10-16  
> **Origen:** `qa-tests/sprint2/historias-usuario-azure.md` (versión original cerrada el 22/09)  
> **Mantenido por:** Equipo de QA + Frontend

---

## Histórico de refinamiento

### Contexto

Las 22 historias de usuario (HU-01 a HU-22) se cerraron el **22/09/2026** como parte de la entrega del Sprint 2. Después del cierre, durante la fase de integración con el frontend (SvelteKit + Svelte 5) y la ejecución de la suite E2E con Playwright (77% de tests pasando), el equipo encontró **dos defectos** que afectaban la experiencia de usuario y la comunicación backend ↔ frontend.

### Proceso de descubrimiento

| Etapa | Acción | Resultado |
|---|---|---|
| 1 | Implementación del frontend SvelteKit (HU-01 a HU-22) | 36/47 tests E2E pasan |
| 2 | Inspección manual de reservas en `/customer/agenda` y `/professional/turns` | Se detecta que se muestran placeholders "Cliente" y "Servicio" en lugar de los nombres reales |
| 3 | Reproducción con `curl` del endpoint `/api/v1/reservas/cliente/{id}` | Confirmado: la respuesta NO incluye los nombres del cliente ni del servicio |
| 4 | Reproducción con `curl` del endpoint `/api/v1/auth/login` (5 intentos fallidos) | Confirmado: el backend siempre devuelve 401, nunca 423 `ACCOUNT_LOCKED` |
| 5 | Decisión de refinamiento (no crear sub-HU nuevas) | Se modifican **HU-03** y **HU-08** en este documento |

### Bugs encontrados y refinados

| # | HU afectada | Tipo de defecto | CA añadido |
|---|---|---|---|
| 1 | **HU-03** Inicio de sesión seguro | CA no cumplido: el bloqueo se aplica en DB pero el backend no lo comunica al frontend (siempre 401) | CA-4 (5º intento → 423), CA-5 (6º+ intento → 423 con minutos restantes), CA-6 (login exitoso resetea contador) |
| 2 | **HU-08** Creación y agendamiento de reserva | Alcance incompleto: el DTO `BookingResponse` no incluye `customerName` ni `serviceName`; el frontend los necesita para mostrar el nombre del cliente y del servicio en las listas | CA-3 (BookingResponse enriquecido), CA-4 (query N+1 safe con LEFT JOIN), CA-5 (compatibilidad con FK huérfanas) |

### Por qué modificar las HU originales y no crear sub-HU nuevas

- **HU-03** ya mencionaba en su descripción original *"bloqueo temporal y autenticación Bearer"* y sus criterios incluían *"El bloqueo temporal al 5º intento fallido"*. El refinamiento completa un CA parcialmente implementado, no crea alcance nuevo.
- **HU-08** su título dice *"Creación **y agendamiento** de reserva"*. El listado de reservas propias con sus datos asociados es parte natural del agendamiento, no un requisito separado.
- Mantener las HU intactas refleja mejor el trabajo del equipo y evita fragmentar el backlog con sub-tickets (`HU-03b`, `HU-08b`) que duplican contexto.
- Las 20 HU restantes (HU-01, 02, 04–07, 09–22) **no requieren refinamiento** según los hallazgos de la batería de tests.

### Cómo usar este documento

1. Las 22 HU aparecen completas aquí (importadas del original con sus IDs, descripciones, criterios Gherkin y estados de Azure DevOps).
2. **HU-03** y **HU-08** aparecen marcadas con ⚠️ **REFINADA** y contienen:
   - Una nota al inicio explicando qué se refinó y por qué
   - Los criterios de aceptación actualizados (con los CA nuevos marcados como `[NUEVO]`)
3. Para crear los tickets en Azure DevOps: copiar-pegar las secciones de cada HU. Para HU-03 y HU-08, mantener el campo `Estado = Active` y agregar la nota del refinamiento en la descripción.

---

## Leyenda de refinamiento

- ⚠️ **REFINADA** — la HU fue modificada en este documento.
- `[NUEVO]` — criterio de aceptación añadido durante el refinamiento.
- `[EXISTENTE]` — criterio que ya estaba en la HU original.

---

# Historias de usuario — CodeFactory EAV10

Total: 22 historias de usuario (2 refinadas tras descubrimiento de bugs).

## HU-01 - Registro de cliente

- **ID:** 12
- **Estado:** Closed
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/12

### Descripción

Como cliente, quiero registrarme en el portal de la plataforma con mi correo y contraseña, para poder buscar profesionales y reservar citas.

ISO/IEC 25010: Seguridad (Protección de identidad)

### Criterios de aceptación

```gherkin
Feature: Registro de cliente

  Scenario: Registro exitoso con datos válidos
    Given soy un visitante sin cuenta en la plataforma
    And ingreso un correo que no está registrado en la plataforma
    When completo el formulario de registro con correo, contraseña y nombre
    Then el sistema crea mi cuenta con el rol "CUSTOMER"
    And puedo iniciar sesión inmediatamente después en el portal

  Scenario: Intento de registro con correo ya usado
    Given ya existe una cuenta de cliente registrada con mi correo en la plataforma
    When intento registrarme de nuevo con ese mismo correo
    Then el sistema rechaza el registro
    And me informa que el correo ya está en uso en la plataforma

  Scenario: Intento de registro con contraseña débil
    Given soy un visitante sin cuenta en la plataforma
    When completo el formulario de registro con una contraseña que no cumple la política de seguridad mínima
    Then el sistema rechaza el registro en el backend
    And me indica los requisitos de longitud y complejidad requeridos
```

---

## HU-02 - Configuración de catálogo de servicios

- **ID:** 13
- **Estado:** Closed
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/13

### Descripción

Como Administrador de la plataforma, quiero configurar el catálogo general y registrar los servicios disponibles, para que los clientes puedan visualizarlos correctamente al buscar citas.

ISO/IEC 25010: Adecuación funcional (Completo)

Nota (22/09): el alcance de especialidades se retiró de esta HU por ADR-005 (alcance reducido de HU-02) — la especialidad vive como campo de HU-22 (Professional), no como entidad propia de esta historia. El título y esta descripción se corrigieron para no prometer algo fuera de alcance.

### Criterios de aceptación

```gherkin
Feature: Registro de servicios y catálogo general

  Scenario: Configuración correcta del catálogo y servicios
    Given estoy autenticado como "ADMIN" en la plataforma
    When configuro el nombre comercial, los datos de contacto generales y cargo los servicios ofrecidos
    Then el sistema guarda el catálogo en la configuración general de la plataforma
    And los servicios quedan indexados para las consultas de los clientes

  Scenario: Intento de configuración con datos obligatorios faltantes
    Given estoy autenticado como "ADMIN" en la plataforma
    When intento guardar la información dejando en blanco el nombre comercial o sin servicios mínimos
    Then el sistema rechaza los cambios en el backend
    And me indica los campos obligatorios pendientes por completar
```

---

## HU-03 - Inicio de sesión seguro ⚠️ REFINADA

- **ID:** 14
- **Estado original:** Closed → **Estado refinado:** Active (reabierta)
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/14
- **Fecha de refinamiento:** 2026-10-16
- **Razón del refinamiento:** durante la integración con el frontend y la suite E2E, se detectó que el `LoginService` siempre devuelve `401 INVALID_CREDENTIALS` incluso cuando aplica el bloqueo por 5 intentos fallidos. El bloqueo se persiste en la base de datos (`app_user.locked_until`) pero el frontend nunca se entera. El componente de countdown del frontend existe pero nunca se activa. Los CA-4, CA-5 y CA-6 son **nuevos** y cierran el gap.

### Descripción

Como usuario registrado (cliente, profesional o administrador), quiero iniciar sesión con mis credenciales en el portal de la plataforma, para acceder a mi cuenta, mis reservas o mis herramientas de gestión.

ISO/IEC 25010: Seguridad (Autenticación y robustez)

### Criterios de aceptación

```gherkin
Feature: Inicio de sesión

  Scenario: Inicio de sesión exitoso
    Given tengo una cuenta previamente registrada en la plataforma
    When ingreso mi correo y contraseña correctos en el portal
    Then el sistema me otorga acceso a mi panel de usuario correspondiente
    And mantiene de forma segura mi rol durante toda la sesión (ver ADR-001)

  Scenario: Credenciales incorrectas en el login
    Given tengo una cuenta previamente registrada en la plataforma
    When ingreso una contraseña incorrecta en el portal
    Then el sistema rechaza el acceso de forma segura
    And no revela en su mensaje de error si el correo ingresado existe o no en el sistema

  Scenario: Bloqueo de cuenta tras intentos fallidos repetidos
    Given tengo una cuenta previamente registrada en la plataforma
    When ingreso una contraseña incorrecta un número de veces que supera el límite de "5" intentos permitidos
    Then el sistema bloquea temporalmente el acceso a mi cuenta
    And me informa el tiempo de espera obligatorio antes de reintentar

  Scenario: [NUEVO] El 5º intento fallido devuelve HTTP 423 al frontend
    Given un usuario con "failed_login_attempts=4" y "locked_until=NULL"
    When intenta hacer login con contraseña incorrecta
    Then el backend debe responder HTTP 423 Locked con:
      {
        "errorCode": "ACCOUNT_LOCKED",
        "message": "Cuenta bloqueada. Intenta en 15 minutos.",
        "details": { "lockMinutes": "15" }
      }
    And persiste "failed_login_attempts=5" y "locked_until=now+15min" en la base de datos

  Scenario: [NUEVO] El 6º intento (y siguientes) con cuenta bloqueada devuelve HTTP 423
    Given un usuario con "failed_login_attempts=5" y "locked_until" en el futuro
    When intenta hacer login con cualquier contraseña
    Then el backend debe responder HTTP 423 con "errorCode=ACCOUNT_LOCKED"
    And el campo "details.lockMinutes" debe reflejar los minutos restantes
      (redondeado hacia arriba a entero, mínimo 1)

  Scenario: [NUEVO] Login con credenciales correctas resetea el contador
    Given un usuario con "failed_login_attempts >= 1"
    When hace login con la contraseña correcta
    Then el sistema persiste "failed_login_attempts=0" y "locked_until=NULL"

  Scenario: [NUEVO] Login con MFA pendiente resetea el contador
    Given un ADMIN con credenciales correctas pero sin MFA enrolado
    When hace login
    Then el backend responde 200 con "mfaRequired=true, mfaSetupRequired=true"
    And el sistema persiste "failed_login_attempts=0"
```

### Notas de implementación (post-refinamiento)

- Crear nueva excepción `AccountLockedException extends RuntimeException` con campo `long lockMinutes`.
- Mapear la excepción a `HttpStatus.LOCKED` (423) en `GlobalExceptionHandler`.
- `LoginService.login()` debe distinguir 3 casos: cuenta ya bloqueada, credenciales malas dentro del límite, credenciales malas que alcanzan el límite.
- El frontend (`routes/(public)/login/+page.svelte`) ya tiene el componente de countdown implementado, se activará automáticamente al recibir el 423.

---

## HU-04 - Definición de horarios de atención semanales

- **ID:** 15
- **Estado:** Closed
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/15

### Descripción

Como profesional, quiero definir mi horario de atención semanal (días y franjas), para que los clientes solo vean espacios en los que realmente puedo atender de forma presencial o virtual.

ISO/IEC 25010: Adecuación funcional (Exactitud)

### Criterios de aceptación

```gherkin
Feature: Definición de horario de atención

  Scenario: Profesional configura su horario semanal
    Given estoy autenticado como "PROFESSIONAL" en la plataforma
    When defino mis días hábiles y las franjas de atención para la semana
    Then el sistema guarda mi disponibilidad asociada a mi ID de profesional
    And los clientes solo visualizan estas franjas libres al agendar conmigo

  Scenario: Intento de definir franjas superpuestas
    Given estoy autenticado como "PROFESSIONAL" en la plataforma
    When intento guardar un horario con franjas superpuestas o con hora de fin anterior a la de inicio
    Then el sistema rechaza los cambios
    And me indica detalladamente el conflicto de horario encontrado
```

---

## HU-05 - Bloqueo temporal de agenda

- **ID:** 16
- **Estado:** New
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/16

### Descripción

Como profesional, quiero bloquear una franja específica de mi agenda (vacaciones, imprevistos), para evitar que se generen reservas en momentos en los que estaré ausente.

ISO/IEC 25010: Confiabilidad (Tolerancia a fallos y gestión de conflictos)

### Criterios de aceptación

```gherkin
Feature: Bloqueo puntual de agenda

  Scenario: Bloquear una franja sin reservas previas
    Given estoy autenticado como "PROFESSIONAL" en la plataforma
    And no existen reservas confirmadas en el rango que deseo bloquear
    When bloqueo ese rango específico de fechas y horas
    Then el sistema deja de ofrecer esas franjas a los clientes de forma inmediata

  Scenario: Intento de bloquear rango con reservas confirmadas activas
    Given estoy autenticado como "PROFESSIONAL" en la plataforma
    And existen "3" reservas de clientes ya confirmadas en el rango de fechas a bloquear
    When intento aplicar el bloqueo temporal a mi agenda
    Then el sistema rechaza el bloqueo automático
    And me presenta una advertencia con las citas afectadas para gestionar su cancelación o reprogramación previa
```

---

## HU-06 - Configuración de duración estándar de servicios

- **ID:** 17
- **Estado:** Closed
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/17

### Descripción

Como Administrador de la plataforma, quiero parametrizar la duración en minutos de cada servicio ofrecido, para que el sistema pueda usar ese dato al calcular espacios y bloques disponibles en las agendas de los profesionales cuando esa funcionalidad se implemente (HU-07/HU-08, Sprint 2).

ISO/IEC 25010: Adecuación funcional

### Criterios de aceptación

```gherkin
Feature: Duración de servicios

  Scenario: Definir la duración de un servicio
    Given estoy autenticado como "ADMIN" en la plataforma
    And existe un servicio creado en el catálogo general
    When le asigno una duración estándar de "45" minutos
    Then el sistema guarda la parametrización asociada al servicio

  Scenario: Intento de asignar una duración igual o menor a cero
    Given estoy autenticado como "ADMIN" en la plataforma
    When intento asignar al servicio una duración de "0" o menor de minutos
    Then el sistema rechaza la solicitud de actualización
    And me indica que la duración debe ser un número entero estrictamente positivo

  Scenario: Intento de registrar una duración que supera el límite máximo permitido
    Given estoy autenticado como "ADMIN" en la plataforma
    When intento asignar al servicio una duración de "600" minutos (excediendo el límite de 480 minutos / 8 horas)
    Then el sistema rechaza la solicitud de registro en el backend
    And devuelve un mensaje de error indicando que la duración del servicio no puede exceder las 8 horas laborales (480 minutos)

Nota Técnica de Dependencia (Clase 6): el cálculo automático de bloques y horarios disponibles en las agendas depende de la entidad Agenda (HU-04/HU-07/HU-08, Sprint 2), que todavía no existe. HU-06 resuelve y prueba únicamente la parametrización y validación de la duración del servicio; el cálculo de disponibilidad se cubrirá al implementar HU-07 (Consulta de disponibilidad de agenda), que consumirá esta duración ya validada. Ningún test de HU-06 afirma ni depende de ese cálculo.
```

---

## HU-07 - Consulta de disponibilidad de agenda

- **ID:** 18
- **Estado:** Closed
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/18

### Descripción

Como cliente, quiero consultar los horarios disponibles de un profesional para un servicio específico dentro de la plataforma, para elegir el bloque de tiempo que más me convenga.

ISO/IEC 25010: Usabilidad

### Criterios de aceptación

```gherkin
Feature: Consulta de disponibilidad

  Scenario: Ver franjas libres de un profesional
    Given estoy registrado como cliente en la plataforma
    And un profesional tiene horarios y servicios configurados
    When consulto la disponibilidad del profesional para un servicio específico
    Then el sistema me muestra la lista de franjas horarias realmente libres de ese profesional
    And no me muestra bloques ya ocupados ni bloqueados

  Scenario: Consultar disponibilidad en un profesional sin cupos libres
    Given el profesional seleccionado no tiene franjas disponibles en el mes solicitado
    When realizo la consulta de disponibilidad
    Then el sistema me informa claramente que no hay horarios libres en el rango seleccionado
    And me sugiere ampliar el filtro de fecha o elegir otro profesional
```

---

## HU-08 - Creación y agendamiento de reserva ⚠️ REFINADA

- **ID:** 19
- **Estado original:** Closed → **Estado refinado:** Active (reabierta)
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/19
- **Fecha de refinamiento:** 2026-10-16
- **Razón del refinamiento:** durante la integración con el frontend, se detectó que el `BookingResponse` del backend (DTO devuelto por `GET /reservas/cliente/{id}` y `GET /reservas/profesional/{id}`) NO incluye los nombres del cliente ni del servicio, solo los IDs. Esto provoca que en las pantallas `/customer/agenda`, `/professional/turns` y otras, se muestren placeholders "Cliente" y "Servicio" en vez de los nombres reales. El listado de reservas propias del cliente/profesional es parte natural del "agendamiento" descrito en el título de la HU. Los CA-3, CA-4 y CA-5 son **nuevos** y completan el flujo.

### Descripción

Como cliente, quiero reservar un servicio en una franja disponible en la plataforma, para asegurar mi cita de forma confiable.

ISO/IEC 25010: Confiabilidad y robustez transaccional

### Criterios de aceptación

```gherkin
Feature: Creación de reserva

  Scenario: Reserva exitosa sobre una franja disponible
    Given estoy autenticado como cliente en la plataforma
    And estoy viendo una franja horaria libre de la agenda de un profesional
    When confirmo la reserva de dicha franja
    Then el sistema registra la reserva a mi nombre
    And la franja horaria pasa de forma atómica a estado "OCUPADA" para otros clientes

  Scenario: Dos clientes intentan reservar la misma franja de forma simultánea (Condición de Carrera)
    Given la franja de las 09:00 AM del lunes para el profesional está marcada como libre
    And el "Cliente_X" y el "Cliente_Y" confirman simultáneamente la reserva para esa misma franja
    When el backend procesa de forma concurrente ambas solicitudes de reserva en el mismo instante (ver ADR-002)
    Then el sistema confirma exitosamente la reserva únicamente a una de las solicitudes
    And rechaza de forma segura la petición del otro cliente para evitar la doble reservación
    And le muestra un mensaje claro indicando que el horario ya no está disponible

  Scenario: [NUEVO] El listado de reservas propias incluye el nombre del cliente y del servicio
    Given estoy autenticado como cliente y tengo reservas en mi cuenta
    When consulto GET "/api/v1/reservas/cliente/{miId}"
    Then cada elemento de la respuesta debe incluir "customerName" (mi nombre completo)
    And "serviceName" (el nombre del servicio reservado)
    And NO debe mostrar placeholders genéricos como "Cliente" o "Servicio"

  Scenario: [NUEVO] El listado de reservas del profesional incluye el nombre del cliente
    Given estoy autenticado como profesional y tengo reservas asignadas
    When consulto GET "/api/v1/reservas/profesional/{miId}"
    Then cada elemento debe incluir "customerName" (nombre completo del cliente)
    And "serviceName" (nombre del servicio)
    Para que pueda atender a la persona correcta en la hora correcta

  Scenario: [NUEVO] La query de listado es N+1 safe
    Given un listado de "N" reservas
    When se ejecuta el endpoint
    Then el log de Hibernate debe mostrar UNA sola query SQL con LEFT JOIN a "app_user" y "servicios"
    Y no N+1 queries (una por cada reserva)
```

### Notas de implementación (post-refinamiento)

- Añadir campos `customerName` y `serviceName` (ambos `@Nullable`) al record `BookingResponse`.
- Crear métodos en `BookingRepository` que usen `@Query` JPQL con constructor expression + LEFT JOIN, por ejemplo:
  ```java
  @Query("""
      SELECT new com.bookly.backendcf.booking.presentation.dto.BookingResponse(
          b.id, b.customerId, u.fullName, b.professionalId, b.serviceId, s.nombre,
          b.startTime, b.endTime, b.status, b.createdAt, b.updatedAt, b.cancelledAt, b.cancellationReason)
      FROM Booking b
        LEFT JOIN app_user u ON u.id = b.customerId
        LEFT JOIN servicios s ON s.id = b.serviceId
      WHERE b.customerId = :customerId
      ORDER BY b.startTime DESC
  """)
  List<BookingResponse> findByCustomerIdWithNames(UUID customerId);
  ```
- Usar **LEFT JOIN** (no INNER) para que las reservas con FK huérfana sigan aparezcan con `customerName=null` y `serviceName=null` en lugar de desaparecer del listado.
- El frontend ya consume los nuevos campos con `b.serviceName ?? 'Servicio'` (fallback por retro-compatibilidad), no requiere cambios.
- Los tests E2E del frontend (`09-customer.spec.ts`, `08-professional.spec.ts`) que buscan `getByText('María')` y similares pasan con esta corrección.

---

## HU-09 - Cancelación autónoma de reservas

- **ID:** 20
- **Estado:** Closed
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/20

### Descripción

Como cliente, quiero cancelar mi reserva con anticipación en el portal de la plataforma, para liberar mi bloque horario y permitir que otro cliente tome la cita.

ISO/IEC 25010: Adecuación funcional

### Criterios de aceptación

```gherkin
Feature: Cancelación de reserva

  Scenario: Cancelación exitosa dentro del tiempo permitido
    Given tengo una reserva confirmada para dentro de "3" horas (cumple el mínimo de 2 horas de anticipación)
    When cancelo la reserva en mi panel de usuario
    Then el sistema libera inmediatamente la franja horaria para otros clientes
    And cambia el estado de mi reserva a "cancelada" en mi historial

  Scenario: Intento de cancelación fuera del plazo de anticipación permitido
    Given tengo una reserva confirmada programada para dentro de "45" minutos (incumple el mínimo de 2 horas)
    When intento cancelar la reserva en mi panel
    Then el sistema rechaza la cancelación automática en el backend
    And me informa que las políticas de la plataforma exigen un mínimo de 2 horas de anticipación para cancelaciones sin penalización
```

---

## HU-10 - Confirmación de reserva por correo electrónico

- **ID:** 21
- **Estado:** Closed
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/21

### Descripción

Como cliente, quiero recibir correos automáticos al crear, reprogramar o cancelar una reserva en el sistema, para contar con un soporte y recordatorio digital de mis citas.

ISO/IEC 25010: Adecuación funcional

### Criterios de aceptación

```gherkin
Feature: Notificación de reserva

  Scenario: Confirmación al crear exitosamente una cita
    Given acabo de agendar una reserva exitosa en la plataforma
    When el sistema procesa y confirma la transacción en el backend
    Then el sistema me envía un correo electrónico de confirmación a mi dirección registrada (ver ADR-004)
    And el correo contiene el membrete de la plataforma, fecha, hora, servicio y profesional asignado

  Scenario: Notificación de cancelación de cita
    Given acabo de cancelar una reserva de forma válida
    When la cancelación queda registrada en la base de datos
    Then recibo un correo de confirmación de cancelación especificando los detalles del cupo liberado
```

---

## HU-11 - Registro de recursos limitados

- **ID:** 22
- **Estado:** Closed
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/22

### Descripción

Como Administrador de la plataforma, quiero registrar los recursos físicos limitados (ej. equipos de capacidad limitada) vinculándolos a servicios específicos, para que el sistema impida agendar más citas de las que físicamente se pueden atender.

ISO/IEC 25010: Adecuación funcional (Soporte transaccional)

### Criterios de aceptación

```gherkin
Feature: Registro de recursos

  Scenario: Asociar un recurso limitado a un servicio
    Given estoy autenticado como "ADMIN" en la plataforma
    And existe un servicio creado en el catálogo general
    When le asigno un recurso físico (ej. equipo portátil) con una cantidad total disponible de "1" unidad
    Then el sistema guarda el inventario de recursos en la configuración general
    And queda parametrizado para validar dicho recurso al agendar reservas

  Scenario: Intento de registrar cantidad inválida de recurso
    Given estoy autenticado como "ADMIN" en la plataforma
    When intento registrar un recurso con una cantidad disponible de "0" o menor
    Then el sistema rechaza la asignación
    And me indica que el inventario del recurso debe ser un número entero mayor a cero
```

---

## HU-12 - Prevención de sobreocupación de recursos físicos

- **ID:** 23
- **Estado:** New
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/23

### Descripción

Como Administrador de la plataforma, quiero que el sistema valide transaccionalmente la disponibilidad de los recursos asignados a un servicio antes de confirmar una reserva, para garantizar que no se agenden citas simultáneas que requieran el mismo recurso físico cuando este no tiene stock disponible en la franja seleccionada.

ISO/IEC 25010: Confiabilidad (Integridad de recursos)

### Criterios de aceptación

```gherkin
Feature: Control de disponibilidad de recursos

  Scenario: Reserva permitida dentro del límite del recurso físico
    Given el recurso "Equipo A" tiene "1" unidad disponible en la franja del lunes a las 09:00 AM
    And no hay reservas confirmadas usando ese recurso en dicha franja
    When un cliente solicita agendar un servicio que requiere el equipo en esa hora
    Then el sistema autoriza y confirma la reserva

  Scenario: Intento de reserva que excede el inventario físico disponible (QA/Exception)
    Given el recurso "Equipo A" tiene "1" unidad de inventario
    And ya existe una reserva confirmada que ocupa esa unidad el lunes a las 09:00 AM
    When otro cliente intenta agendar un servicio que requiere ese recurso para ese mismo horario
    Then el sistema rechaza la reserva en el backend para evitar la sobreocupación del recurso físico (ver ADR-003)
    And devuelve una respuesta de error que le sugiere al cliente franjas libres alternativas
```

---

## HU-13 - Historial de reservas del cliente

- **ID:** 24
- **Estado:** New
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/24

### Descripción

Como cliente, quiero ver el historial de todas mis reservas pasadas y futuras, para mantener una bitácora y llevar control de los servicios que he tomado.

ISO/IEC 25010: Usabilidad

### Criterios de aceptación

```gherkin
Feature: Historial de reservas del cliente

  Scenario: Consultar mi historial como cliente
    Given estoy autenticado como "CUSTOMER" en la plataforma
    And tengo reservas confirmadas, canceladas e históricas registradas en mi cuenta
    When accedo a mi panel de "Mis Reservas"
    Then el sistema me presenta un listado con fecha, servicio, profesional y estado de mis citas
    And no incluye bajo ninguna circunstancia información de otros clientes

  Scenario: Consultar historial sin citas previas registradas
    Given estoy autenticado como "CUSTOMER" en la plataforma
    And no he agendado ninguna cita en mi historial
    When accedo a mi panel de "Mis Reservas"
    Then el sistema me presenta un mensaje informativo indicando que aún no cuento con reservas registradas
```

> **Nota técnica (post-implementación):** los criterios de aceptación de esta HU **ya se cumplen implícitamente** con la implementación de HU-08 + los refinamientos de HU-08b (campos `customerName` y `serviceName`). El frontend `/customer/agenda` implementa la vista "próximas" + "historial" (canceladas y pasadas). HU-13 puede marcarse como **Closed** una vez que HU-08b se implemente en backend, sin código nuevo.

---

## HU-14 - Historial de reservas del profesional

- **ID:** 25
- **Estado:** New
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/25

### Descripción

Como profesional, quiero ver el historial y la programación de las reservas que tengo asignadas en mi agenda, para planificar mis jornadas de atención a clientes de forma óptima.

ISO/IEC 25010: Usabilidad

### Criterios de aceptación

```gherkin
Feature: Historial de reservas del profesional

  Scenario: Consultar mi agenda de atención diaria
    Given estoy autenticado como "PROFESSIONAL" en la plataforma
    And tengo asignadas citas de clientes para el día de hoy
    When consulto mi panel de "Mi Agenda"
    Then el sistema me muestra la lista ordenada cronológicamente de mis clientes con su hora, nombre y tipo de servicio
    And tiene prohibido mostrarme datos de otros profesionales

  Scenario: Consultar agenda de atención en un rango de fechas ampliado
    Given estoy autenticado como "PROFESSIONAL" en la plataforma
    And tengo asignadas citas de clientes programadas para la próxima semana
    When consulto mi panel de "Mi Agenda" especificando un rango de fechas opcional que cubre desde el próximo lunes hasta el próximo viernes
    Then el sistema me presenta el listado cronológico consolidado de todas mis reservas asignadas en ese intervalo
    And mantiene la restricción estricta de no retornar datos de otros profesionales

  Scenario: Consultar mi agenda sin clientes programados
    Given estoy autenticado como "PROFESSIONAL" en la plataforma
    And no cuento con citas agendadas para el día de hoy
    When consulto mi panel de "Mi Agenda"
    Then el sistema me presenta un mensaje informativo indicando que mi agenda se encuentra libre para la jornada de hoy
```

> **Nota técnica (post-implementación):** los criterios de esta HU también se cumplen con la implementación de HU-08 + HU-08b. El frontend `/professional/turns` y `/professional/agenda` implementan la vista. HU-14 puede marcarse como **Closed** junto con HU-08b.

---

## HU-15 - Reporte de ocupación por periodo

- **ID:** 26
- **Estado:** New
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/26

### Descripción

Como Administrador de la plataforma, quiero visualizar reportes consolidados del porcentaje de ocupación de las agendas de mis profesionales por periodos (semana, mes), para identificar horas pico, evaluar la demanda de servicios y optimizar la planeación de turnos.

ISO/IEC 25010: Eficiencia de desempeño

### Criterios de aceptación

```gherkin
Feature: Reporte de ocupación

  Scenario: Generar reporte de ocupación mensual
    Given estoy autenticado como "ADMIN" en la plataforma
    And existen reservas agendadas y atendidas durante el último mes
    When solicito la generación del reporte de ocupación mensual
    Then el sistema me presenta gráficos y porcentajes de ocupación por día de la semana y por profesional
    And extrae los datos únicamente de la base de datos de la plataforma de forma segura

  Scenario: Generar reporte de ocupación mensual sin datos transaccionales en el periodo (QA/Exception)
    Given estoy autenticado como "ADMIN" en la plataforma
    And no se han registrado reservas de clientes durante el último mes
    When solicito la generación del reporte de ocupación mensual
    Then el sistema genera el reporte exitosamente con valores y porcentajes en cero
    And el backend no arroja ninguna excepción interna o de puntero nulo (NullPointerException)
```

---

## HU-16 - Reporte general de uso de la plataforma

- **ID:** 27
- **Estado:** New
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/27

### Descripción

Como Administrador de la plataforma, quiero generar reportes y estadísticas de uso consolidadas (reservas totales, cancelaciones promedio, volumen de uso de recursos e ingresos), para medir el nivel de adopción general del software y evaluar el valor comercial generado en el único contexto de la plataforma.

ISO/IEC 25010: Eficiencia de desempeño; Seguridad (control de acceso a datos agregados)

### Criterios de aceptación

```gherkin
Feature: Reporte de uso general de la plataforma

  Scenario: Consultar estadísticas agregadas del sistema
    Given estoy autenticado como "ADMIN" de la plataforma
    And existen reservas registradas durante el periodo a consultar
    When solicito el reporte analítico general de adopción
    Then el sistema me presenta un panel consolidado que detalla el número de reservas, cancelaciones promedio, volumen de uso de recursos e ingresos generados en el único contexto de la plataforma

  Scenario: Autenticación obligatoria con doble factor robusto para acceder al reporte general
    Given estoy registrado como "ADMIN" con privilegios para visualizar reportes agregados
    When inicio sesión en el portal de la plataforma y accedo al módulo de reportes
    Then el sistema me exige obligatoriamente un segundo factor de autenticación de alta seguridad (MFA por aplicación autenticadora TOTP) antes de permitirme visualizar los datos
    And expira mi sesión activa automáticamente tras "15" minutos de inactividad para proteger las consultas de datos

  Scenario: Trazabilidad ineludible y log de auditoría para consultas agregadas
    Given estoy autenticado como "ADMIN"
    When ejecuto una consulta de estadísticas de uso que agrega datos de la plataforma
    Then el sistema registra de manera inmutable, en un log de auditoría exclusivo, el identificador del administrador, la IP de origen, la marca de tiempo exacta y el tipo de datos consultados
```

---

## HU-17 - Asignación de roles y permisos (RBAC)

- **ID:** 28
- **Estado:** Closed
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/28

### Descripción

Como Administrador de la plataforma, quiero asignar y modificar el rol de un usuario, para controlar qué acciones y módulos puede operar de forma segura.

ISO/IEC 25010: Seguridad (Autorización)

### Criterios de aceptación

```gherkin
Feature: Gestión de roles y permisos (RBAC)

  Scenario: Asignar un rol a un usuario
    Given estoy autenticado como "ADMIN" de la plataforma
    And existe un usuario registrado con rol "CUSTOMER"
    And el usuario no tiene reservas activas pendientes en el sistema
    When le asigno el rol de "PROFESSIONAL"
    Then el sistema actualiza el rol del usuario en la base de datos
    And sus permisos de acceso reflejan de inmediato el cambio para los endpoints de la plataforma

  Scenario: Intento fallido de cambiar el rol de un usuario con compromisos activos
    Given estoy autenticado como "ADMIN" de la plataforma
    And existe un usuario registrado con el rol "CUSTOMER" que tiene reservas activas programadas
    When intento cambiar su rol a "PROFESSIONAL"
    Then el sistema rechaza la actualización del rol en el backend
    And devuelve una excepción indicando que el usuario posee citas activas que impiden la conversión de rol

  Scenario: Usuario intenta acceder a una función fuera de sus permisos
    Given estoy autenticado como un usuario con rol "CUSTOMER" en la plataforma
    When intento enviar una solicitud a un endpoint de configuración reservado para "ADMIN"
    Then el sistema backend rechaza la solicitud de forma segura devolviendo un código HTTP 403 Forbidden
```

---

## HU-18 - Autenticación multifactor (MFA) para accesos administrativos

- **ID:** 29
- **Estado:** Closed
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/29

### Descripción

Como Administrador de la plataforma, quiero autenticarme con un segundo factor (MFA) al acceder a la consola administrativa, para blindar las operaciones de configuración y datos ante robos de contraseñas.

ISO/IEC 25010: Seguridad (Autenticación multifactor)

### Criterios de aceptación

```gherkin
Feature: Autenticación multifactor para accesos sensibles

  Scenario: Acceso administrativo con código MFA correcto
    Given inicié sesión con mis credenciales de "ADMIN" válidas
    When ingreso el código temporal de segundo factor (MFA) generado en mi aplicación autenticadora
    Then el sistema me concede acceso al panel administrativo de la plataforma

  Scenario: Código de segundo factor incorrecto o expirado
    Given inicié sesión con mis credenciales de "ADMIN" válidas
    When ingreso un código temporal incorrecto o cuya ventana de tiempo ya expiró
    Then el sistema rechaza el acceso administrativo
    And me permite solicitar un reintento
```

---

## HU-19 - Registro de auditoría de acciones críticas

- **ID:** 30
- **Estado:** Closed
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/30

### Descripción

Como Administrador de la plataforma, quiero consultar la bitácora inmutable de auditoría de todas las acciones críticas ejecutadas, para mantener una trazabilidad transparente ante incidentes, modificaciones de accesos o reclamos.

ISO/IEC 25010: Confiabilidad (Trazabilidad e inmutabilidad)

### Criterios de aceptación

```gherkin
Feature: Registro de auditoría

  Scenario: Registrar una acción crítica en el log de la plataforma
    Given un usuario realiza una acción crítica (crear reserva, cancelar reserva o cambiar un rol) en la plataforma
    When la acción se completa exitosamente en el backend
    Then el sistema registra de forma inmutable quién la realizó, qué acción fue y cuándo ocurrió

  Scenario: Consultar el registro de auditoría de la plataforma
    Given estoy autenticado como "ADMIN" de la plataforma
    When solicito el registro de auditoría para un rango de fechas específico
    Then el sistema me muestra únicamente las acciones críticas registradas en la plataforma
```

---

## HU-20 - Configuración inicial de la plataforma

- **ID:** 31
- **Estado:** Closed
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/31

### Descripción

Como responsable de la plataforma, quiero registrar la plataforma y obtener la cuenta administradora inicial, para empezar a configurar servicios y agendas.
La plataforma es única. POST /api/v1/platform/setup es público y de un solo uso: crea la plataforma y su administrador (rol ADMIN) en una transacción. La contraseña se guarda con BCrypt. Si la plataforma ya fue configurada, el endpoint rechaza con 409.
ISO/IEC 25010: Seguridad (Confidencialidad) — las credenciales del administrador nunca se almacenan, registran ni devuelven en claro.
Fuera de alcance: configuraciones multi-tenant, SMTP real, recuperación de contraseña, agendas y catálogos.

### Criterios de aceptación

```gherkin
Escenario: Aprovisionamiento exitoso

  Dado que la plataforma no ha sido configurada previamente

  Cuando envío POST a "/api/v1/platform/setup" con el nombre de la plataforma y los datos del administrador

  Entonces recibo 201 con el identificador de la plataforma

  Y queda creado un usuario administrador con rol "ADMIN"



Escenario: Rechazo de un segundo aprovisionamiento

  Dado que la plataforma ya fue configurada previamente

  Cuando envío POST a "/api/v1/platform/setup" con datos distintos

  Entonces recibo 409 con errorCode "PLATFORM_ALREADY_CONFIGURED"



Escenario: Rechazo por datos incompletos

  Cuando envío POST a "/api/v1/platform/setup" sin el campo "name"

  Entonces recibo 400 con los campos "errorCode", "message", "details" y "traceId"



Escenario: La contraseña nunca se almacena en texto plano

  Cuando aprovisiono la plataforma con la contraseña "Secreta123*"

  Entonces el valor almacenado no es "Secreta123*"

  Y la respuesta no contiene ningún campo con la contraseña
```

---

## HU-21 - Control de acceso a recursos ajenos

- **ID:** 32
- **Estado:** Active
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/32

### Descripción

Como responsable de la plataforma, quiero que ningún usuario acceda a información que no le pertenece, para cumplir con la confidencialidad de los datos de los clientes.
Cubre autorización por rol y por propiedad del recurso: respuesta 403 uniforme ante acceso ajeno, distinguida del 401 por falta de autenticación, y log de seguridad en JSON de cada intento.
ISO/IEC 25010: Seguridad (Confidencialidad e integridad de datos)
Fuera de alcance: RLS, filtros cross-tenant, claim de organización en el JWT.

### Criterios de aceptación

```gherkin
Escenario: Un usuario no puede consultar el recurso de otro

  Dado que estoy autenticado con rol "CUSTOMER"

  Y existe un recurso que pertenece a otro usuario

  Cuando envío GET para ver su detalle

  Entonces recibo 403 con errorCode "ACCESS_DENIED" y el traceId de la petición

  Y se registra un log de seguridad en formato JSON con el intento



Escenario: El administrador accede a cualquier recurso

  Dado que estoy autenticado con rol "ADMIN"

  Cuando consulto el detalle de cualquier recurso

  Entonces recibo 200



Escenario: Falta de autenticación se distingue de falta de permiso

  Dado que no envío token

  Cuando intento consultar un recurso

  Entonces recibo 401 y no 403
```

---

## HU-22 - Creación de especialistas y profesionales

- **ID:** 33
- **Estado:** Closed
- **Azure DevOps:** https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/33

### Descripción

Como Administrador de la plataforma, quiero registrar a los profesionales o especialistas que prestan servicios, para poder configurar sus agendas y asignarlos a servicios específicos.

ISO/IEC 25010: Adecuación funcional (Modularidad)

### Criterios de aceptación

```gherkin
Feature: Registro de profesionales

  Scenario: Registro exitoso de un profesional
    Given estoy autenticado como "ADMIN" en la plataforma
    When completo el registro del profesional "Sofía" con su correo y especialidad
    Then el sistema crea su perfil en la plataforma
    And le asigna el rol de "PROFESSIONAL" con permisos restringidos a su propia agenda

  Scenario: Intento de registro con correo ya registrado
    Given estoy autenticado como "ADMIN" en la plataforma
    And ya existe una cuenta registrada con ese correo
    When intento registrar un profesional con ese mismo correo
    Then el sistema rechaza la solicitud
    And me informa que el correo ya está en uso en la plataforma

  Scenario: Intento de registro con contraseña débil
    Given estoy autenticado como "ADMIN" en la plataforma
    When completo el registro del profesional con una contraseña que no cumple la política de seguridad mínima
    Then el sistema rechaza el registro en el backend
    And me indica los requisitos de longitud y complejidad requeridos

  Scenario: Carrera de registro simultáneo con el mismo correo
    Given estoy autenticado como "ADMIN" en la plataforma
    And el correo todavía no existe al momento de la validación previa
    When dos solicitudes de registro con el mismo correo llegan simultáneamente
    Then la base de datos detecta el duplicado al guardar (índice único)
    And el sistema traduce esa condición a la misma respuesta de correo ya registrado, sin exponer el error técnico de integridad de datos
```

---

## Resumen del refinamiento

| HU | Estado original | Estado refinado | CA nuevos | Estimación |
|---|---|---|---|---|
| HU-03 | Closed | **Active** (reabierta) | 3 (5º intento → 423, 6º+ → 423 con minutos, login correcto resetea) | 1-2h backend |
| HU-08 | Closed | **Active** (reabierta) | 3 (BookingResponse con nombres, query N+1 safe, LEFT JOIN) | 1-2h backend |
| HU-13, HU-14 | New | New (sin cambios — implementación ya cubre con HU-08 refinada) | 0 | 0 (solo documentación) |
| Otras 18 HU | Closed/New | Sin cambios | 0 | 0 |

**Costo total estimado del refinamiento**: 2-4 horas de backend.  
**Costo en frontend**: 0 horas (los tests E2E existentes y el código actual ya consumen los nuevos campos).  
**Migración de datos**: no requerida.  
**Breaking changes**: no (campos aditivos en `BookingResponse`).

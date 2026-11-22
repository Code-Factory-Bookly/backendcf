# Historias de Usuario - Sprint 3

Proyecto: CodeFactory EAV10
Iteracion: Sprint 3
Periodo: 28 de octubre al 25 de noviembre de 2026

---

## HU-05 - Bloqueo temporal de agenda

- **Azure Work Item:** [#16](https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/16)
- **Tipo:** User Story
- **Estado:** Active
- **Asignado a:** DAVID ARANGO PINEDA
- **Ruta de iteracion:** CodeFactory EAV10\Sprint 3

### Descripcion

Como profesional, quiero bloquear una franja específica de mi agenda (vacaciones, imprevistos), para evitar que se generen reservas en momentos en los que estaré ausente.

**ISO/IEC 25010:** Confiabilidad (Tolerancia a fallos y gestión de conflictos)

### Criterios de aceptacion

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

## HU-11 - Registro de recursos limitados

- **Azure Work Item:** [#22](https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/22)
- **Tipo:** User Story
- **Estado:** Active
- **Asignado a:** MIGUEL ANGEL MARIN OTALVARO
- **Ruta de iteracion:** CodeFactory EAV10\Sprint 3

### Descripcion

Como Administrador de la plataforma, quiero registrar los recursos físicos limitados (ej. equipos de capacidad limitada) vinculándolos a servicios específicos, para que el sistema impida agendar más citas de las que físicamente se pueden atender.

**ISO/IEC 25010:** Adecuación funcional (Soporte transaccional)

### Criterios de aceptacion

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

- **Azure Work Item:** [#23](https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/23)
- **Tipo:** User Story
- **Estado:** Active
- **Asignado a:** MIGUEL ANGEL MARIN OTALVARO
- **Ruta de iteracion:** CodeFactory EAV10\Sprint 3

### Descripcion

Como Administrador de la plataforma, quiero que el sistema valide transaccionalmente la disponibilidad de los recursos asignados a un servicio antes de confirmar una reserva, para garantizar que no se agenden citas simultáneas que requieran el mismo recurso físico cuando este no tiene stock disponible en la franja seleccionada.

**ISO/IEC 25010:** Confiabilidad (Integridad de recursos)

### Criterios de aceptacion

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

- **Azure Work Item:** [#24](https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/24)
- **Tipo:** User Story
- **Estado:** Active
- **Asignado a:** ELENA VARGAS GRISALES
- **Ruta de iteracion:** CodeFactory EAV10\Sprint 3

### Descripcion

Como cliente, quiero ver el historial de todas mis reservas pasadas y futuras, para mantener una bitácora y llevar control de los servicios que he tomado.

**ISO/IEC 25010:** Usabilidad

### Criterios de aceptacion

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

---

## HU-14 - Historial de reservas del profesional

- **Azure Work Item:** [#25](https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/25)
- **Tipo:** User Story
- **Estado:** Active
- **Asignado a:** SOFIA ESPINAL VASQUEZ
- **Ruta de iteracion:** CodeFactory EAV10\Sprint 3

### Descripcion

Como profesional, quiero ver el historial y la programación de las reservas que tengo asignadas en mi agenda, para planificar mis jornadas de atención a clientes de forma óptima.

**ISO/IEC 25010:** Usabilidad

### Criterios de aceptacion

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

---

## HU-15 - Reporte de ocupación por periodo

- **Azure Work Item:** [#26](https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/26)
- **Tipo:** User Story
- **Estado:** Active
- **Asignado a:** SOFIA ESPINAL VASQUEZ
- **Ruta de iteracion:** CodeFactory EAV10\Sprint 3

### Descripcion

Como Administrador de la plataforma, quiero visualizar reportes consolidados del porcentaje de ocupación de las agendas de mis profesionales por periodos (semana, mes), para identificar horas pico, evaluar la demanda de servicios y optimizar la planeación de turnos.

**ISO/IEC 25010:** Eficiencia de desempeño

### Criterios de aceptacion

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

- **Azure Work Item:** [#27](https://dev.azure.com/CodeFactoryEAV10/_workitems/edit/27)
- **Tipo:** User Story
- **Estado:** Active
- **Asignado a:** ELENA VARGAS GRISALES
- **Ruta de iteracion:** CodeFactory EAV10\Sprint 3

### Descripcion

Como Administrador de la plataforma, quiero generar reportes y estadísticas de uso consolidadas (reservas totales, cancelaciones promedio, volumen de uso de recursos e ingresos), para medir el nivel de adopción general del software y evaluar el valor comercial generado en el único contexto de la plataforma.

**ISO/IEC 25010:** Eficiencia de desempeño; Seguridad (control de acceso a datos agregados)

### Criterios de aceptacion

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

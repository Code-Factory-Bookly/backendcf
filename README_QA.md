# Calidad de software - Sprint 1 y Sprint 2

## 1. Propósito y alcance

Este documento describe el plan de calidad y pruebas de Bookly Backend para el Sprint 1 (7 HU según el tablero real de Azure DevOps: HU-01, HU-02, HU-03, HU-06, HU-20, HU-21, HU-22), el estado real de la suite de pruebas, y lo que falta para cumplir el Quality Gate exigido por los `Lineamientos.md` antes del cierre del sprint (23/09/2026).

**Actualizado el 2026-09-21:** el rol `PATIENT` se renombró a `CUSTOMER` en todo el código (PR #12, migración Flyway `V4`) — este documento ya usa el nombre real. **Segunda actualización, mismo día:** se agregó `JwtTokenServiceTest` (PR #18, Quality Gate `OK`) y se corrigió en el tablero de Azure DevOps la anomalía de HU-21 (ver secciones 6, 8 y 10).

Los criterios de aceptación en Gherkin de cada HU viven en `HU_Reservas_Empresa_Unica.md`; este documento  los usa como base de trazabilidad hacia las pruebas automatizadas.

## 2. Requisitos de calidad exigidos para el equipo avanzado.

Según `Lineamientos.md`, secciones 3.5, 7.2 y 9.1:

| Dimensión | Criterio mínimo | Obligatoriedad |
|---|---|---|
| Análisis estático | SonarCloud con Quality Gate activo | Debe (bloquea DoD, sección 9.1) |
| Cobertura unitaria | ≥ 65% | Debe (resuelve contradicciones de umbral, sección 1.2.4) |
| Deuda técnica | Remediación máx. 2 días | Debe |
| Complejidad ciclomática | < 50 (evaluar también cognitiva) | Debe |
| Vulnerabilidades | Cero críticas | Debe |
| Historias de usuario | Criterios de aceptación en Gherkin | Debe |
| Pipeline (etapa Calidad) | SonarCloud, cobertura ≥65% | Debe (sección 7.2) |

## 3. Plan de calidad por sprint (Lineamientos 3.7)

| Sprint | Entregable exigido | Estado en `backendcf` |
|---|---|---|
| 1 | Plan de calidad y pruebas; Gherkin para HU prioritarias | Gherkin completo en `HU_Reservas_Empresa_Unica.md` (22 HU, sincronizado contra el tablero real). Este documento cubre el plan de pruebas. |
| 2 | Registro de defectos; SonarCloud activo; cobertura medida; pruebas con patrón AAA | Pipeline y JaCoCo agregados (sección 7); **bloqueo de SonarCloud ya resuelto** (ver sección 7.3) — el análisis corre real en cada PR/push. Quality Gate del proyecto (`branch=main`) en **`OK`**, 92.6% de cobertura de código nuevo (vs 80% que exige el gate por defecto de SonarCloud, más estricto que el 65% de `Lineamientos.md` sobre el proyecto completo) |
| 3 | Automatización de criterios de aceptación; ejecución E2E | No iniciado — depende de que Sprint 1 y 2 cierren cobertura unitaria primero |

## 4. Trazabilidad HU → Gherkin → prueba automatizada

### HU-01 — Registro de cliente

| Escenario Gherkin | Prueba automatizada | Estado |
|---|---|---|
| Registro exitoso con datos válidos | `RegisterCustomerServiceTest.registroExitosoCreaLaCuentaConRolClienteYPasswordHasheado` | Cubierto |
| Intento de registro con correo ya usado | `RegisterCustomerServiceTest.registroConCorreoYaRegistradoRechazaLaSolicitud` | Cubierto |
| Intento de registro con contraseña débil | `RegisterCustomerServiceTest.registroConContrasenaDebilEsRechazadoPorLaValidacionDelContrato` (valida el contrato `@Pattern` de `RegisterCustomerRequest` directamente) | Cubierto |

### HU-03 — Inicio de sesión seguro

| Escenario Gherkin | Prueba automatizada | Estado |
|---|---|---|
| Inicio de sesión exitoso | `LoginServiceTest.loginExitosoEntregaTokenYReiniciaContador` | Cubierto |
| Credenciales incorrectas en el login | `LoginServiceTest.credencialesIncorrectasDevuelvenErrorYRegistranIntento` | Cubierto |
| Bloqueo de cuenta tras intentos fallidos repetidos | `LoginServiceTest.quintoIntentoFallidoBloqueaLaCuenta` | Cubierto |

### HU-21 — Control de acceso a recursos ajenos

| Escenario Gherkin | Prueba automatizada | Estado |
|---|---|---|
| Dueño accede a su propio recurso | `OwnershipGuardTest.elDuenoDelRecursoPuedeAccederASuPropioRecurso` | Cubierto |
| Administrador accede a cualquier recurso | `OwnershipGuardTest.unAdministradorPuedeAccederACualquierRecurso` | Cubierto |
| Tercero sin permisos es rechazado (403 + log JSON) | `OwnershipGuardTest.unUsuarioNoPuedeAccederAlRecursoDeOtro` | Cubierto |

Mecanismo genérico (`shared/security/OwnershipGuard`), independiente de HU-08 (no existe todavía la entidad `Reserva`) — mergeado en PR #13, Quality Gate `OK`.

### HU-22 — Creación de especialistas y profesionales

| Escenario Gherkin | Prueba automatizada | Estado |
|---|---|---|
| Registro exitoso de profesional | `RegisterProfessionalServiceTest.registroExitosoCreaLaCuentaConRolProfesionalYElPerfilDeEspecialidad` | Cubierto |
| Correo ya registrado | `RegisterProfessionalServiceTest.registroConCorreoYaRegistradoRechazaLaSolicitud` | Cubierto |
| Contraseña débil | `RegisterProfessionalServiceTest.registroConContrasenaDebilEsRechazadoPorLaValidacionDelContrato` | Cubierto |

Implementada y mergeada en el PR #15. La cobertura incluye registro exitoso, correo duplicado, contraseña débil y carrera de registro simultáneo.

### HU-02 — Configuración del catálogo de servicios

| Escenario Gherkin | Prueba automatizada | Estado |
|---|---|---|
| Registro correcto de un servicio en el catálogo | `ServiceOfferingServiceTest.registroCorrectoGuardaElServicioEnElCatalogoConEstadoActivo`, `ServiceOfferingControllerTest.registroCorrectoDevuelve201ConElServicioCreado` | Cubierto |
| Intento de registrar un servicio con datos obligatorios faltantes | `ServiceOfferingRequestValidationTest.shouldRejectBlankName/shouldRejectNullName/shouldRejectBlankCategory/shouldRejectNullCategory/shouldRejectNullPrice/shouldRejectZeroPrice`, `ServiceOfferingControllerTest.registroConCamposObligatoriosFaltantesDevuelve400ConLosCamposPendientes` | Cubierto |
| Consulta pública del catálogo | `ServiceOfferingServiceTest.consultaSinFiltroDeCategoriaDevuelveTodoElCatalogo/consultaConCategoriaFiltraPorEsaCategoria/consultaConCategoriaEnBlancoSeComportaComoSinFiltro`, `ServiceOfferingControllerTest.consultaDelCatalogoDevuelve200ConLosServiciosDisponibles/consultaDelCatalogoConFiltroDeCategoriaDevuelve200ConLosServiciosDeEsaCategoria` | Cubierto |

`ServiceOfferingServiceTest` (Mockito) también cubre el rechazo por nombre duplicado (`registroConNombreYaExistenteEnElCatalogoEsRechazado`), lógica de negocio detrás de la unicidad del catálogo que no está descrita literalmente en el Gherkin. `ServiceOfferingControllerTest` (`@WebMvcTest`) suma el caso de contrato HTTP 409 `SERVICE_ALREADY_EXISTS` (`registroConNombreDuplicadoDevuelve409ConErrorCodeServiceAlreadyExists`).

**Pendiente conocido, no cubierto por diseño (ver sección 6):** `ServiceOfferingService.create` sigue con el patrón check-then-act (`existsByNameIgnoreCase` → `save()` sin atomicidad) sin corregir; no se agregó test de condición de carrera para HU-02 todavía.

### HU-06 — Configuración de duración estándar de servicios

| HU | Prueba automatizada | Estado |
|---|---|---|
| HU-02 | Ninguna sobre `ServiceOfferingService` (la lógica de duplicado por nombre) | Sin cubrir |
| HU-06 | `ServiceOfferingRequestValidationTest`, `ServiceOfferingTest` (PR #14, Miguel) | Cubierto (dominio + validación del DTO), implementado y mergeado |

### HU-20 — Configuración inicial de la plataforma

| Escenario Gherkin | Prueba automatizada | Estado |
|---|---|---|
| Aprovisionamiento exitoso | `PlatformSetupServiceTest.aprovisionamientoExitosoCreaPlataformaYAdminConRolAdmin`, `PlatformSetupControllerTest.aprovisionamientoExitosoDevuelve201ConElIdentificadorDeLaPlataforma` | Cubierto |
| Rechazo de un segundo aprovisionamiento | `PlatformSetupServiceTest.segundoAprovisionamientoEsRechazadoCuandoYaExistePlataforma`, `PlatformSetupControllerTest.segundoAprovisionamientoDevuelve409ConErrorCodePlatformAlreadyConfigured` | Cubierto |
| Rechazo por datos incompletos | `PlatformSetupRequestValidationTest.requestSinNombreDePlataformaEsRechazadoPorElContrato`, `PlatformSetupControllerTest.requestSinNombreDevuelve400ConElContratoDeErrorCompleto` | Cubierto |
| La contraseña nunca se almacena en texto plano | `PlatformSetupServiceTest.elPasswordNuncaSeAlmacenaEnTextoPlanoYNoSeExponeEnLaRespuesta` | Cubierto |

HU-20 tiene sus 4 escenarios Gherkin cubiertos entre `PlatformSetupServiceTest` (capa de servicio, Mockito), `PlatformSetupRequestValidationTest` (contrato del DTO, Bean Validation) y `PlatformSetupControllerTest` (contrato HTTP vía `@WebMvcTest`, códigos 201/409/400). Adicionalmente, `PlatformSetupServiceTest.segundoAprovisionamientoConcurrenteEsRechazadoPorElIndiceUnicoDePlataforma` cubre la condición de carrera del índice único `uk_platform_singleton` señalada en la sección 6, y `PlatformSetupRequestValidationTest` suma dos casos de control (`name` en blanco y request válido) que no están descritos literalmente en el Gherkin.

### HU-07 — Consulta de disponibilidad de agenda

| Escenario Gherkin | Prueba automatizada | Estado |
|---|---|---|
| Ver franjas libres de un profesional | `AvailabilityServiceTest.devuelveLosBloquesLibresSegunLaDuracionDelServicio/noMuestraLosBloquesYaOcupados`, `AvailabilityCalculatorTest.generaBloquesConsecutivosDentroDeLaFranja/excluyeLosBloquesQueSeSolapanConUnaReserva/unaReservaContiguaNoBloqueaElBloqueSiguiente/ignoraLosBloquesQueYaPasaron`, `AvailabilityIntegrationTest.consultaLasFranjasRealesYExcluyeLasReservasConfirmadas/unaReservaCanceladaNoOcupaElBloque` (PostgreSQL real), `AvailabilityControllerSecurityTest.clienteConsultaLaDisponibilidad` | Cubierto. Los bloqueos manuales quedan fuera de alcance (ver `docs/HU-07-disponibilidad.md`) |
| Consultar disponibilidad en un profesional sin cupos libres | `AvailabilityServiceTest.sinFranjasLibresInformaYSugiereAmpliarElFiltro`, `AvailabilityIntegrationTest.sinHorariosEnElRangoInformaQueNoHayDisponibilidad/profesionalTotalmenteReservadoInformaQueNoHayDisponibilidad` | Cubierto |
| Control adicional: validación del rango y 404 | `AvailabilityServiceTest.rechazaParametrosFaltantes/rechazaUnRangoInvertido/rechazaUnRangoMayorAlMaximo/rechazaUnRangoCompletamenteEnElPasado/profesionalInexistenteDevuelveNotFound/servicioInexistenteOInactivoDevuelveNotFound`, `GlobalExceptionHandlerTest.transformaUnaConsultaDeDisponibilidadInvalidaEnErrorBadRequest` | Cubierto |
| Control adicional: autorización (solo `CUSTOMER`) | `AvailabilityControllerSecurityTest.sinTokenDevuelveUnauthorized/profesionalRecibeForbiddenAlConsultarLaDisponibilidad/administradorRecibeForbiddenAlConsultarLaDisponibilidad` | Cubierto |

## 5. Estado real de la suite de pruebas

```text
src/test/java/com/bookly/backendcf/
├── auth/application/LoginServiceTest.java                          # 3,  HU-03
├── auth/application/RegisterCustomerServiceTest.java               # 3,  HU-01 (Mockito)
├── auth/domain/model/UserAccountTest.java                          # 6,  reglas de dominio
├── auth/security/JwtTokenServiceTest.java                          # 10, riesgo JWT (firma/expiración/estructura)
├── availability/application/AvailabilityServiceTest.java           # 10, HU-07 (servicio, Mockito)
├── availability/application/AvailabilityIntegrationTest.java       # 4,  HU-07 (PostgreSQL real)
├── availability/domain/model/AvailabilityCalculatorTest.java       # 8,  HU-07 (regla de dominio)
├── availability/infrastructure/persistence/AvailabilityQueryAdapterTest.java # 5, HU-07 (adaptador, Mockito)
├── availability/presentation/AvailabilityControllerSecurityTest.java # 5, HU-07 (contrato HTTP/seguridad)
├── catalog/application/ServiceOfferingServiceTest.java             # 5,  HU-02 (servicio, Mockito)
├── catalog/domain/model/ServiceOfferingTest.java                   # 10, HU-06 (dominio/duración)
├── catalog/presentation/ServiceOfferingControllerTest.java         # 5,  HU-02 (contrato HTTP, @WebMvcTest)
├── catalog/presentation/dto/ServiceOfferingRequestValidationTest.java # 14, HU-06 (validación del DTO)
├── platform/application/PlatformSetupServiceTest.java              # 4,  HU-20 (servicio, Mockito)
├── platform/presentation/PlatformSetupControllerTest.java          # 3,  HU-20 (contrato HTTP, @WebMvcTest)
├── platform/presentation/dto/PlatformSetupRequestValidationTest.java # 3, HU-20 (validación del DTO)
├── professional/application/RegisterProfessionalServiceTest.java   # 4,  HU-22 (servicio, Mockito)
├── professional/presentation/ProfessionalControllerSecurityTest.java # 3, HU-22 (contrato HTTP/seguridad)
├── shared/security/OwnershipGuardTest.java                         # 3,  HU-21 (Mockito)
└── BackendcfApplicationTests.java                                   # 1,  smoke test de contexto Spring
```

**77 tests en `main`, todos en verde** (`./mvnw test`, verificado el 2026-09-22). Las 7 HU de Sprint 1 están mergeadas — ya no hay PR pendientes que sumar aparte.

Sin prueba directa todavía: `JwtAuthenticationFilter`, `AuthController`, `GlobalExceptionHandler` — son los únicos tres componentes de Sprint 1 sin cobertura (`PlatformSetupService`/HU-20 ya se cubrió, ver arriba).

`LoginServiceTest` simula el repositorio con un `Proxy` de reflexión hecho a mano en vez de Mockito. El resto de los tests nuevos (`RegisterCustomerServiceTest`, `OwnershipGuardTest`, `RegisterProfessionalServiceTest` del PR #15, y `JwtTokenServiceTest` del PR #18) sí usan Mockito o son de unidad plana sin mocks — conviene migrar `LoginServiceTest` al mismo patrón cuando se retome.

**HU-07:** 34 pruebas automatizadas, todas en verde (`./mvnw test`, verificado el 2026-10-10). Incluye 4 pruebas de integración contra PostgreSQL real (`AvailabilityIntegrationTest`, requieren la base `bookly_test` en el puerto 55432, igual que `BookingConcurrencyTest`).

## 6. Defectos y riesgos de calidad detectados

| Riesgo | Ubicación | Impacto |
|---|---|---|
| Carrera de duplicados (check-then-act sin atomicidad) | `RegisterCustomerService.register`, `PlatformSetupService`, `ServiceOfferingService` (todos con `existsByEmail`/`count()` → `save()` sin atomicidad) | Dos solicitudes concurrentes pueden generar una excepción de integridad no mapeada al contrato de error esperado, en vez de un 409 controlado |
| JWT construido a mano | `JwtTokenService` | Arma/parsea JSON con concatenación de strings e `indexOf`/`substring`; el `escape()` solo cubre `\` y `"`. **Ya tiene test** (`JwtTokenServiceTest`, PR #18, 10 escenarios: firma/payload alterado, expiración, secreto distinto, estructura inválida) — el riesgo pasó de "no verificado" a "verificado y pineado por test", pero el diseño artesanal sigue siendo el mismo; migrar a una librería vetada (`io.jsonwebtoken`) sigue recomendado para Sprint 2 |
| Secreto JWT no persistente | `JwtTokenService` | El constructor ya rechaza secreto vacío o menor a 32 bytes (falla al arrancar si `JWT_SECRET` no está seteado, en vez de generar uno aleatorio) — cubierto por dos de los diez casos de `JwtTokenServiceTest` (PR #18) |

**A favor — ya corregido en un caso:** `RegisterProfessionalService` (HU-22, PR #15) **sí** resuelve bien el mismo patrón de carrera: usa `saveAndFlush` dentro de un `try/catch` de `DataIntegrityViolationException`, traducido a `EmailAlreadyRegisteredException`. Vale la pena usar ese código como referencia al corregir los otros tres servicios de la fila de arriba, en vez de reinventar el patrón cada vez.

## 7. Pipeline de calidad (CI/SonarCloud)

### 7.1 Etapas del pipeline mínimo exigido

```mermaid
flowchart LR
    V[Validación<br/>lint + compilación]
    Q[Calidad<br/>SonarCloud + cobertura ≥ 65%]
    S[Seguridad<br/>SAST/SCA/secretos]
    D[Despliegue<br/>health check]
    V --> Q --> S --> D
```

`ci.yml` implementa hoy Validación (`mvnw compile`) y la mitad de Calidad (tests + JaCoCo + análisis real de SonarCloud, ya funcionando). Seguridad (SAST/SCA/detección de secretos) y Despliegue todavía no existen en el workflow. **Nuevo hallazgo:** `backendcf` nunca se desplegó en Render ni en ningún otro lado — dato relevante para cuando se arme la etapa de Despliegue.

### 7.2 JaCoCo

Agregado en `pom.xml` (`jacoco-maven-plugin` 0.8.12, ejecuciones `prepare-agent` y `report` en fase `test`). Genera `target/site/jacoco/jacoco.xml`, que es el reporte que `sonar.coverage.jacoco.xmlReportPaths` le entrega a SonarCloud.

### 7.3 Bloqueo de SonarCloud — RESUELTO (2026-09-17)

El bloqueo original (`Not authorized or project not found`, causado por el proyecto tener activo "Automatic Analysis" en SonarCloud en vez de "Use CI") **ya se resolvió**: alguien con rol Admin en SonarCloud cambió el modo del proyecto. Confirmado con una corrida real en `main`:

```
[INFO] ANALYSIS SUCCESSFUL, you can find the results at:
https://sonarcloud.io/dashboard?id=Code-Factory-Bookly_backendcf&branch=main
```

`sonar.organization` (`code-factory-bookly`) y `sonar.projectKey` (`Code-Factory-Bookly_backendcf`) en `pom.xml` están confirmados correctos.

**Nota sobre `continue-on-error` en el paso de Sonar:** el PR #6 (Copilot) lo agregó como parche temporal mientras el bloqueo estaba activo. Se intentó sacar (PR #11) pero se cerró sin mergear a pedido del equipo — **sigue en `ci.yml` a propósito**. Esto significa que hoy un PR puede mostrarse "verde" en GitHub aunque el Quality Gate real de SonarCloud esté en rojo — **siempre verificar el gate real** (sección siguiente), no solo el check de GitHub. Nota que solo aplica en `pull_request`; en push a `main` el paso de Sonar sí puede tumbar el job entero (pasó exactamente eso el 22/09, ver el incidente de abajo).

**Incidente del 22/09 — `SONAR_TOKEN` rechazado, ya resuelto:** entre el merge del PR #15 (00:48) y el PR #22 (05:12), las 8 corridas de CI en `main` fallaron con `HTTP 403 Forbidden. Please check ... SONAR_TOKEN` — el token había quedado inválido. `Compilar` y `Tests y cobertura` seguían pasando bien; solo el análisis de Sonar se caía. Se regeneró el token en SonarCloud y se actualizó el secreto en GitHub (17:43); se reintentó la corrida (`gh run rerun`, sin necesitar un commit nuevo) y terminó en verde.

**Estado real del Quality Gate hoy** (verificado vía API pública de SonarCloud, no solo el checkmark):

```
GET https://sonarcloud.io/api/qualitygates/project_status?projectKey=Code-Factory-Bookly_backendcf&branch=main
```

`branch=main`: **`status: OK`** — `new_coverage: 92.6%` vs `80%` exigido (el gate por defecto de SonarCloud mide cobertura de *código nuevo*, más estricto que el 65% de `Lineamientos.md` sobre el proyecto completo). Reliability, security, maintainability, duplicación y hotspots también en verde. Análisis corrido el 2026-09-22 sobre el commit `1be78fd` (el más reciente, con las 7 HU ya mergeadas).

## 8. Pruebas pendientes para cerrar Sprint 1

Actualizado con las 7 HU reales de Sprint 1:

1. ~~`RegisterCustomerServiceTest`~~ — hecho (HU-01, 3 escenarios).
2. ~~`UserAccountTest`~~ — hecho (6 pruebas de reglas de dominio).
3. ~~`OwnershipGuardTest`~~ — hecho (HU-21, 3 escenarios, mecanismo genérico independiente de HU-08).
4. ~~`RegisterProfessionalServiceTest`~~ — hecho e integrado (HU-22, 4 escenarios incluyendo la carrera de registro simultáneo).
5. ~~`ServiceOfferingTest` / `ServiceOfferingRequestValidationTest`~~ — hecho e integrado (HU-06).
6. ~~`JwtTokenServiceTest`~~ — hecho e integrado (10 escenarios: firma/payload alterado, expiración, secreto distinto, estructura inválida y validación del constructor).
7. ~~`ServiceOfferingService` (HU-02)~~ — hecho e integrado; `registroConNombreYaExistenteEnElCatalogoEsRechazado` cubre el duplicado por nombre (sección 4).
8. ~~`PlatformSetupServiceTest` (HU-20)~~ — hecho e integrado (10 tests entre servicio, controlador y validación del DTO, sección 4).
9. **`JwtAuthenticationFilterTest`**, **`AuthControllerTest`** y **`GlobalExceptionHandlerTest`** — únicos componentes de Sprint 1 sin test todavía, sin cambios.

Las 7 HU de Sprint 1 tienen cobertura automatizada completa. Lo único pendiente (punto 9) es cobertura adicional de componentes transversales que no bloquean la implementación funcional de ninguna HU.

## 9. Ejecución local

```bash
./mvnw test
```

Reporte de cobertura generado en:

```
target/site/jacoco/jacoco.xml   # formato que consume SonarCloud
target/site/jacoco/index.html   # reporte navegable
```

Análisis local contra SonarCloud (una vez resuelto el bloqueo de la sección 7.3):

```bash
export SONAR_TOKEN=$(cat ~/.sonar_token)
./mvnw -B verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar
```
Las pruebas de integración (`BookingConcurrencyTest`, `AvailabilityIntegrationTest`) requieren un PostgreSQL local:

```bash
docker run -d --name bookly-test-db -e POSTGRES_DB=bookly_test -e POSTGRES_USER=bookly_test \
  -e POSTGRES_PASSWORD=bookly_test_local_2026 -p 55432:5432 postgres:16-alpine
```

## 10. Pendientes de evolución

### HU-19 — Registro de auditoría de acciones críticas

La integración funcional disponible quedó cubierta así:

| Acción | Adaptador | Evidencia automatizada |
|---|---|---|
| `BOOKING_CREATED` | `BookingCreatedAuditListener` | `BookingCreatedAuditListenerTest` |
| `ROLE_CHANGED` | `RoleChangedAuditListener` | `RoleChangedAuditListenerTest`, `RoleAssignmentServiceTest` |
| `BOOKING_CANCELLED` | Pendiente de HU-09 | No existe todavía el evento consumidor en `main` |

La auditoría se publica de forma síncrona dentro de la transacción de negocio. Si la persistencia de
la bitácora falla, la operación de negocio no debe confirmarse. La persistencia e inmutabilidad de
PostgreSQL se validan adicionalmente con `AuditLogPersistenceTest` y
`docs/evidencia/HU-19-integridad.sql`.

Para continuar el cierre de Sprint 1: mantener la cobertura y corregir el patrón de carrera check-then-act en `RegisterCustomerService`/`PlatformSetupService`/`ServiceOfferingService` (usando como referencia la corrección ya aplicada en `RegisterProfessionalService`). HU-06, HU-22 y la cobertura de `JwtTokenService` ya están integradas.

**Resuelto el 2026-09-21:** la anomalía de HU-21 en el tablero de Azure DevOps (work item 32) — estaba sin Sprint asignado pese a estar mergeada desde el PR #13. Ya se movió a `Sprint 1` vía API. El `State` se dejó en `Active` a propósito (no se cerró unilateralmente el work item de David; falta que él lo pase a `Closed`).

Para Sprint 2 (Lineamientos 3.7): llevar la cobertura real por encima del 80% de código nuevo que exige el gate por defecto de SonarCloud, reescribir `LoginServiceTest` con Mockito en vez del `Proxy` manual, agregar la etapa de Seguridad al pipeline (SAST/SCA/detección de secretos), armar la etapa de Despliegue (hoy `backendcf` no está desplegado en ningún lado), y registrar defectos de forma trazable en Azure DevOps (el tipo de work item `Bug` ya está disponible, todavía no se usó ninguno).

Para Sprint 3: automatizar los criterios de aceptación Gherkin restantes y sumar ejecución E2E. La migración del JWT artesanal a una librería vetada (`io.jsonwebtoken`, ya usada en `Fab2016`) sigue recomendada, pero ya no es bloqueante para tener cobertura — `JwtTokenServiceTest` (PR #18) fija el comportamiento actual, así que la migración puede evaluarse con red de seguridad en vez de a ciegas.

**Documentos formales que faltan** (según Clases 6 y 7 del curso, distintos entre sí): un **SQAP** consolidado (IEEE 730, 7 secciones) y un **Plan de Pruebas** formal (IEEE 829/ISO 29119: alcance In/Out-of-Scope, criterios de entrada/salida/suspensión/reanudación, matriz de riesgos) — ninguno de los dos existe todavía para `backendcf`.

### revisado por Adrian Espinosa
### co-redactado con codex.

## 11. HU-10 — Notificaciones de reserva

### Alcance probado

La implementación actual de HU-10 consume los eventos disponibles en `main`:

- `BookingCreatedEvent` para la confirmación de reserva.
- `BookingCancelledEvent` para la notificación de cancelación.
- `BookingRescheduledEvent` queda fuera de esta ejecución porque todavía no existe en el contrato
  de `booking`.

El adaptador `LoggingEmailNotificationAdapter` se mantiene como valor predeterminado para que las
pruebas no dependan de un servidor externo. `SmtpEmailNotificationAdapter` permite activar un
proveedor real mediante variables de entorno, sin credenciales en el repositorio.

### Trazabilidad Gherkin → pruebas

| Escenario | Prueba automatizada | Estado |
|---|---|---|
| Se procesa una confirmación y se registra como enviada | `NotificationServiceTest.procesaCreacionYMarcaElCorreoComoEnviado` | Cubierto |
| Un fallo del proveedor se registra como `FAILED` | `NotificationServiceTest.registraFalloDelProveedorSinPropagarLaExcepcion` | Cubierto |
| El mismo evento no genera un segundo correo | `NotificationServiceTest.eventoYaProcesadoNoVuelveAEnviarCorreo` | Cubierto |
| El evento de cancelación contiene los datos de la reserva | `CancelBookingServiceTest` — captura de `BookingCancelledEvent` | Cubierto |
| El evento tiene identificador único para idempotencia | `BookingDtoTests.bookingCreatedEventConstructor`, `CancelBookingServiceTest` | Cubierto |
| El listener procesa después del commit | `BookingNotificationListenerIntegrationTest.procesaElEventoSoloDespuesDelCommit` | Cubierto |
| El listener no procesa un rollback | `BookingNotificationListenerIntegrationTest.noProcesaElEventoCuandoLaTransaccionHaceRollback` | Cubierto |

### Criterios negativos y de seguridad

- El correo no se envía dos veces para el mismo `eventId`.
- Un error del adaptador no propaga la excepción hacia la operación de reserva.
- El error técnico se conserva en `notification_delivery` sin credenciales ni tokens.
- La información de cliente, servicio y profesional se resuelve mediante puertos de lectura.
- La notificación se procesa con `AFTER_COMMIT`; no debe revertir una reserva confirmada.

### Evidencia ejecutada

Pruebas focalizadas ejecutadas correctamente:

```bash
./mvnw -q -Dtest=NotificationServiceTest,BookingDtoTests,CancelBookingServiceTest test
```

Resultado: **29 pruebas ejecutadas, 0 fallos y 0 errores**.

Compilación verificada:

```bash
./mvnw -q -DskipTests compile
```

La migración `V43__create_notification_delivery.sql` también fue aplicada contra PostgreSQL 16.15
en Docker. Se verificaron la tabla, la FK hacia `bookings`, la unicidad de `event_id`, las
restricciones `CHECK` y los índices operativos. La migración `V44__cascade_notification_delivery_booking.sql`
ajusta la FK para que la limpieza de una reserva no quede bloqueada por su registro técnico.

Suite completa ejecutada con una base PostgreSQL de pruebas en `localhost:55432`:

```bash
./mvnw test
```

Resultado de la suite base: **264 pruebas ejecutadas, 0 fallos y 0 errores**.

Con las pruebas de integración `AFTER_COMMIT`, SMTP y contrato de reprogramación incorporadas, la
suite completa actual queda en **268 pruebas ejecutadas, 0 fallos y 0 errores**.

### Pendientes de QA para cerrar HU-10

1. Validar el contenido final de las plantillas con datos representativos del negocio.
2. Configurar y probar las credenciales SMTP del entorno de despliegue, sin incorporarlas al código.
3. Agregar casos de reprogramación cuando `booking` publique `BookingRescheduledEvent`.

## 12. HU-04 — Horario de atención semanal del profesional

| Escenario Gherkin | Prueba automatizada | Estado |
|---|---|---|
| Profesional configura su horario semanal | `WeeklyScheduleServiceTest.guardaHorarioValidoYReemplazaElAnterior`, `WeeklyScheduleServiceTest.aceptaFranjasContiguas` | Cubierto |
| Intento de definir franjas superpuestas | `WeeklyScheduleServiceTest.rechazaFranjasSuperpuestasConElDetalleDelConflicto`, `WeeklyScheduleServiceTest.rechazaHoraFinAnteriorALaDeInicio` | Cubierto |
| Control adicional: profesional inexistente | `WeeklyScheduleServiceTest.profesionalInexistenteDevuelveNotFound` | Cubierto |
| Control adicional: autorización (dueño o ADMIN) | `ScheduleControllerSecurityTest.sinTokenDevuelveUnauthorized/clienteDevuelveForbidden/profesionalNoPuedeEditarHorarioAjeno/profesionalPuedeEditarSuPropioHorario` | Cubierto |

El solapamiento se valida en el servicio, no con una restricción de exclusión en la base (`uk_horario_semanal_inicio` solo impide dos franjas con la misma hora de inicio exacta). Queda como mejora si el equipo la quiere (ver ADR-006).

## 13. HU-17 — Asignación de roles y permisos (RBAC)

| Escenario Gherkin | Prueba automatizada | Estado |
|---|---|---|
| Asignar un rol a un usuario | `RoleAssignmentServiceTest.clienteSeConvierteEnProfesionalCreandoSuPerfil`, `UserRoleControllerSecurityTest.administradorPuedeAsignarRoles` | Cubierto |
| Intento fallido de cambiar el rol de un usuario con compromisos activos | `RoleAssignmentServiceTest.profesionalConPerfilNoPuedeVolverseCliente` (bloquea por perfil profesional existente, no por citas activas — ver nota) | Cubierto parcialmente |
| Usuario intenta acceder a una función fuera de sus permisos | `UserRoleControllerSecurityTest.sinTokenDevuelveUnauthorized/clienteNoPuedeAsignarRoles/profesionalNoPuedeAsignarRoles` | Cubierto |
| Control adicional: especialidad obligatoria al promover a PROFESSIONAL | `RoleAssignmentServiceTest.clienteAProfesionalSinEspecialidadEsRechazado` | Cubierto |
| Control adicional: cambio al mismo rol es una operación nula | `RoleAssignmentServiceTest.mismoRolNoModificaNada` | Cubierto |
| Control adicional: búsqueda de usuarios para asignar el rol (`GET /usuarios/buscar`) | `RoleAssignmentServiceTest.busquedaSinTextoDevuelveSugerenciasExistentes/busquedaConTextoFiltraPorCorreoONombreYMarcaPerfilProfesional/busquedaConUnSoloCaracterSeTrataComoSinTexto`, `UserRoleControllerSecurityTest.busquedaSinTokenDevuelveUnauthorized/busquedaClienteDevuelveForbidden/busquedaAdministradorDevuelveCoincidencias/busquedaSinParametroQUsaCadenaVacia` | Cubierto |

**Pendiente conocido, no cubierto por diseño:** el Gherkin de HU-17 pide rechazar el cambio de rol cuando el usuario tiene "citas activas programadas". Lo que el código hoy rechaza es distinto: un `PROFESSIONAL` con **perfil creado** no puede cambiar a otro rol (`409 ROLE_CHANGE_CONFLICT`), sin consultar reservas. El escenario literal depende de que `booking` exponga una consulta de reservas activas por usuario; no existe todavía esa integración entre los módulos `auth` y `booking`.

**Pendiente conocido, no cubierto:** el rol viaja dentro del JWT ya emitido. Un cambio de rol no revoca ni invalida los tokens activos de ese usuario — siguen autorizando con el rol anterior hasta que expiren (hasta 1 hora). El criterio de aceptación pide que el cambio se refleje "de inmediato"; eso requeriría resolver el rol contra la base en cada petición o un mecanismo de revocación de tokens, ninguno de los dos implementado.

## 14. HU-08 — Creación y agendamiento de reserva

| Escenario Gherkin | Prueba automatizada | Estado |
|---|---|---|
| Reserva exitosa sobre una franja disponible | `CreateBookingServiceTest.shouldCreateBookingSuccessfully`, `BookingControllerTest.shouldCreateBookingSuccessfully/shouldUseAuthenticatedCustomerId` | Cubierto |
| Dos clientes intentan reservar la misma franja de forma simultánea (condición de carrera) | `BookingConcurrencyTest.twoSimultaneousCustomersOnlyOneBookingIsConfirmed` (concurrencia real contra PostgreSQL), `CreateBookingServiceTest.shouldRejectIfSlotOccupied`, `BookingControllerTest.shouldReturnConflictWhenSlotIsOccupied` | Cubierto |
| Control adicional: validación del rango horario | `CreateBookingServiceTest.shouldRejectIfStartTimeAfterEndTime/shouldRejectIfStartTimeInPast`, `BookingControllerTest.shouldReturnBadRequestForInvalidTimeRange/shouldReturnBadRequestForInvalidCustomerId` | Cubierto |
| Control adicional: lectura de reservas por dueño (`GET /reservas/cliente/{id}`, `GET /reservas/profesional/{id}`) | `BookingQuerySecurityTest.sinTokenDevuelve401/clientePuedeVerSusPropiasReservas/clienteNoPuedeVerLasReservasDeOtroClienteDevuelve403/profesionalPuedeVerSusPropiasReservas/administradorPuedeVerLasReservasDeCualquierCliente` | Cubierto |

La unicidad de la franja la garantiza la base con la restricción `unique_booking_per_professional_time` (`V30__create_bookings.sql`); el servicio traduce la violación a `409 SLOT_OCUPADO` (ADR-006).

**Nota de limpieza de QA:** `BookingComprehensiveTest.java` (clase `BookingComprehensiveTestFinal`) no sigue la convención de nombres de Surefire (termina en "Final", no en "Test"/"Tests") y el CI nunca la ejecuta. Si se corre a mano, 7 de sus 26 casos fallan porque mockean `bookingRepository.save()` cuando `CreateBookingService` usa `saveAndFlush()` — queda como código de prueba muerto y desactualizado, duplicado del que sí corre en `CreateBookingServiceTest`. Pendiente que el equipo de `booking` la borre o la corrija.

## 15. HU-09 — Cancelación autónoma de reservas

| Escenario Gherkin | Prueba automatizada | Estado |
|---|---|---|
| Cancelación exitosa dentro del tiempo permitido | `CancelBookingServiceTest.shouldCancelBookingWithThreeHoursNotice/shouldAllowCancellationAt121Minutes/shouldAllowCancellationAtExactly120Minutes`, `CancelBookingControllerTest.shouldCancelBookingSuccessfully/shouldAllowRequestWithoutReason` | Cubierto |
| Intento de cancelación fuera del plazo de anticipación permitido | `CancelBookingServiceTest.shouldRejectCancellationWithFortyFiveMinutesNotice/shouldRejectCancellationAt119Minutes` | Cubierto |
| Control adicional: solo el cliente dueño cancela | `CancelBookingServiceTest.shouldRejectCancellationByAnotherCustomer`, `CancelBookingControllerTest.shouldPropagateAccessDeniedForAnotherCustomer`, `CancelBookingSecurityTest.administradorNoPuedeCancelarDevuelve403/profesionalNoPuedeCancelarDevuelve403` | Cubierto |
| Control adicional: estados inválidos | `CancelBookingServiceTest.shouldRejectAlreadyCancelledBooking/shouldRejectMissingBooking`, `CancelBookingControllerTest` (contrato HTTP) | Cubierto |

El plazo mínimo (`BOOKING_CANCELLATION_MIN_ADVANCE_MINUTES`, 120 por defecto) es configurable, no está fijado en el código — ver `CancelBookingServiceTest.shouldRejectCancellationAt119Minutes` / `shouldAllowCancellationAtExactly120Minutes` para el límite exacto.

## 16. HU-18 — Autenticación multifactor (MFA) para accesos administrativos

| Escenario Gherkin | Prueba automatizada | Estado |
|---|---|---|
| Acceso administrativo con código MFA correcto | `MfaServiceTest.enrolamientoConCodigoValidoActivaTotpYEntregaDiezCodigosDeRecuperacion` | Cubierto |
| Código de segundo factor incorrecto o expirado | `MfaServiceTest.codigoIncorrectoEnVerificacionRegistraIntentoFallido` | Cubierto |
| Control adicional: no se puede reenrolar solo con la contraseña | `MfaServiceTest.noSePuedeReenrolarConSoloLaContrasena` | Cubierto |
| Control adicional: código de recuperación de un solo uso | `MfaServiceTest.codigoDeRecuperacionDeUsoUnicoFunciona` | Cubierto |
| Control adicional: regenerar códigos de recuperación | `MfaServiceTest.regenerarCodigosConCodigoValidoReemplazaLosAnteriores/regenerarCodigosSinMfaActivoFalla/regenerarCodigosConCodigoIncorrectoRegistraIntentoFallido`, `MfaSettingsControllerSecurityTest.regenerarDevuelveDiezCodigosNuevos` | Cubierto |
| Control adicional: desactivar el segundo factor | `MfaServiceTest.desactivarConContrasenaYCodigoValidosLimpiaElSegundoFactor/desactivarConContrasenaIncorrectaFalla`, `MfaSettingsControllerSecurityTest.desactivarConCredencialesValidasDevuelveNoContent/desactivarSinTokenDevuelveUnauthorized` | Cubierto |
| Control adicional: omitir el enrolamiento ("preguntar de nuevo la próxima vez", ADR-010) | `MfaServiceTest.omitirLaConfiguracionEntregaSesionSinActivarMfa/omitirLaConfiguracionConMfaYaActivoFalla` | Cubierto |
| Control adicional: autorización de las rutas de administración del propio MFA | `MfaSettingsControllerSecurityTest.estadoSinTokenDevuelveUnauthorized/estadoClienteDevuelveForbidden/estadoAdministradorDevuelveCodigosRestantes` | Cubierto |

El secreto TOTP se cifra con `MFA_ENCRYPTION_KEY` (AES-256) antes de persistirse; si esa clave cambia, los secretos ya guardados quedan indescifrables (ver README.md, sección de variables de entorno). El alcance es exclusivo de `ADMIN`: `CUSTOMER` y `PROFESSIONAL` nunca pasan por un reto de MFA al iniciar sesión.

## 17. Evidencia ejecutada (Sprint 2, HU-04/08/09/17/18)

```bash
./mvnw -q -Dtest='!AvailabilityIntegrationTest,!BookingCancellationConcurrencyTest,!BookingCancellationIntegrationTest,!BookingConcurrencyTest,!BookingComprehensiveTestFinal' test
```

Ejecutado localmente contra H2 (sin la base PostgreSQL de integración que usa el CI para las pruebas de concurrencia e integración real): **303 pruebas, 0 fallos, 0 errores**, incluidas `WeeklyScheduleServiceTest`, `ScheduleControllerSecurityTest`, `RoleAssignmentServiceTest`, `UserRoleControllerSecurityTest`, `CreateBookingServiceTest`, `BookingControllerTest`, `CancelBookingServiceTest`, `CancelBookingControllerTest`, `CancelBookingSecurityTest`, `BookingQuerySecurityTest`, `MfaServiceTest` y `MfaSettingsControllerSecurityTest`. Las cuatro clases que exigen PostgreSQL real en `localhost:55432` (`AvailabilityIntegrationTest` y las tres de `booking` de concurrencia/integración) y el reporte de cobertura consolidado quedan a cargo del pipeline de CI, que sí tiene ese Postgres disponible.

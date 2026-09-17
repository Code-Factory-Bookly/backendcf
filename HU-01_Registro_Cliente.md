# HU-01 - Registro de cliente / paciente

**Descripción:** Como cliente (paciente), quiero registrarme con mi correo y contraseña, para poder buscar profesionales y reservar citas.

**ISO/IEC 25010:** Seguridad (Protección de identidad)

---

## Decisiones de Arquitectura y Base de Datos
*Analizando los escenarios de esta historia de usuario y asumiendo la responsabilidad de tomar las decisiones clave sobre la estructura y diseño del sistema desde los roles de Arquitectura y Base de Datos, se establece el siguiente lineamiento técnico:*

Inicialmente **no implementaremos una arquitectura multitenant**. El nivel de complejidad técnica que esto añade a nivel de infraestructura y persistencia de datos es demasiado alto para esta fase. Además, revisando detalladamente el documento de Lineamientos Integrados de CodeFactory, implementar un modelo multitenant no es un requisito exigido ni en los lineamientos de Arquitectura ni en los de Base de Datos. 

Dado que el equipo cuenta con recursos limitados, asumir este reto ahora representa un riesgo innecesario para los tiempos de entrega. Por lo tanto, el alcance multitenant se documentará y se dejará exclusivamente como un 'plus' o mejora futura. 

**Resolución:** Los escenarios de aceptación (Gherkin) se han ajustado para que funcionen sobre un entorno estándar de un solo tenant, de acuerdo con esta directiva.

---

## Acceptance Criteria (Gherkin Ajustado)

### Scenario: Registro exitoso de un paciente nuevo
**Given** soy un visitante sin cuenta
**When** completo el formulario de registro con correo, contraseña y nombre
**Then** el sistema crea mi cuenta con el rol "CUSTOMER"
**And** puedo iniciar sesión inmediatamente después en el portal

### Scenario: Intento de registro con correo ya usado
**Given** ya existe una cuenta de paciente registrada con mi correo
**When** intento registrarme de nuevo con ese mismo correo
**Then** el sistema rechaza el registro
**And** me informa que el correo ya está en uso en esta organización

### Scenario: Intento de registro con contraseña débil
**Given** soy un visitante sin cuenta
**When** completo el formulario de registro con una contraseña que no cumple la política de seguridad mínima
**Then** el sistema rechaza el registro en el backend
**And** me indica los requisitos de longitud y complejidad requeridos

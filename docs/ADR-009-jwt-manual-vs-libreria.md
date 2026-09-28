# ADR-009: JWT hecho a mano vs. migración a librería vetada

- **Estado:** Aceptada
- **Fecha:** 2026-09-22
- **Responsables:** Arquitectura y Calidad
- **Ámbito:** `auth/security/JwtTokenService`, Sprint 1 (decisión), Sprint 2 (ejecución)

## Contexto

`JwtTokenService` (autoría original: Elena, PR #2) implementa la emisión y verificación de JWT a mano:
arma el header/payload con concatenación de strings, codifica en Base64Url, firma con HMAC-SHA256 vía
`javax.crypto.Mac`, y "parsea" el payload con búsqueda de substrings (`indexOf`/`substring`) en vez de
un parser JSON real.

La recomendación de migrar a una librería vetada (`io.jsonwebtoken`, ya usada en `Fab2016`) venía
apareciendo como fila de riesgo en `README_QA.md` y `Plan_Calidad_Software.md` desde que se escribió
`JwtTokenServiceTest` (PR #18, 10 escenarios) — pero nunca se tomó como decisión formal con alternativas
y consecuencias, solo quedó anotada como sugerencia sin dueño ni fecha, igual que los dos pendientes que
resolvió [ADR-008](ADR-008-pruebas-carga-y-contrasena-inicial.md).

## Decisión

**Se mantiene `JwtTokenService` hecho a mano en Sprint 1. No se migra ahora.** La migración a
`io.jsonwebtoken` queda diferida a Sprint 2 — recomendada, no descartada.

Justificación, verificada directamente sobre el código (no solo sobre la sugerencia heredada):

1. **La verificación de firma usa comparación de tiempo constante.** `parse()` compara la firma con
   `MessageDigest.isEqual(...)`, no con `.equals()` de `String` — mitiga ataques de timing contra la
   firma HMAC.
2. **No confía en el campo `alg` del token para decidir cómo verificar.** Siempre usa HMAC-SHA256 fijo,
   sin leer ni respetar el `alg` que declara el propio token — mitiga el ataque clásico "alg:none" /
   confusión de algoritmo, uno de los vectores más comunes contra implementaciones JWT ingenuas.
3. **El constructor ya rechaza secretos vacíos o menores a 32 bytes**, fallando al arrancar en vez de
   generar un secreto aleatorio efímero — evita que un despliegue sin `JWT_SECRET` quede silenciosamente
   inseguro.
4. **`JwtTokenServiceTest` (10 escenarios, PR #18) ya fija el contrato como red de seguridad:** token
   válido, firma alterada, payload alterado, expiración, secreto distinto, estructura inválida,
   validación del constructor. Si se migra más adelante, correr esta misma suite sin tocarla es la forma
   de confirmar que la migración no cambió el comportamiento observable.
5. No hay una vulnerabilidad activa ni un incidente que fuerce el cambio ahora. Migrar la pieza central
   de autenticación a un día del cierre de Sprint 1, sin necesidad real, es más riesgo (romper
   login/registro/todo lo que depende de un token válido) que beneficio inmediato.

El riesgo real que sigue existiendo — parser JSON artesanal, frágil ante casos no contemplados aunque
hoy no sea explotable — no desaparece con esta decisión. Se acepta conscientemente para Sprint 1, con
plan de resolución en Sprint 2.

## Alternativas consideradas

| Alternativa | Motivo de descarte |
|---|---|
| Migrar a `io.jsonwebtoken` ahora, en Sprint 1 | Sin urgencia real (no hay vulnerabilidad activa) y con riesgo alto de romper autenticación a un día del cierre |
| Dejar la recomendación como estaba, sin decisión formal | Es la opción que motivó este ADR — llevaba desde el PR #18 sin dueño ni fecha, igual que los pendientes de ADR-008 |
| Mantener el diseño manual permanentemente, sin plan de migración | Descartado: el parser artesanal sigue siendo una fuente de fragilidad a largo plazo (no reproduce el spec de JWT/RFC 7519 completo); conviene migrar cuando el calendario lo permita, no cancelar la migración |
| Migrar reescribiendo también los tests | Innecesario: `JwtTokenServiceTest` prueba el contrato observable (`createToken`/`parse`), no la implementación interna — debería seguir pasando sin cambios tras una migración bien hecha |

## Consecuencias

- El riesgo de JWT pasa de "recomendación flotante sin dueño" a "decisión con justificación técnica y
  fecha objetivo (Sprint 2)".
- `JwtAuthenticationFilter` y `AuthController` siguen sin test directo (gap ya registrado en
  `README_QA.md`/`Plan_Calidad_Software.md`) — no se resuelve con este ADR, sigue pendiente aparte.
- Cuando se ejecute la migración en Sprint 2, el criterio de aceptación de esa tarea es: `mvn test`
  sobre `JwtTokenServiceTest` pasa sin modificar el archivo de test.
- No se introduce ninguna dependencia nueva en Sprint 1 — cero riesgo de romper el cierre del sprint.

## Trazabilidad

- Código: `src/main/java/com/bookly/backendcf/auth/security/JwtTokenService.java`.
- Test: `src/test/java/com/bookly/backendcf/auth/security/JwtTokenServiceTest.java` (PR #18).
- Riesgo registrado
- Relacionado: [ADR-008 en: `Plan_Calidad_Software.md`, sección 13.](ADR-008-pruebas-carga-y-contrasena-inicial.md) (mismo patrón: recomendación de  Calidad sin dueño, formalizada como ADR).

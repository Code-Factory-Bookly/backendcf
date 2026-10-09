# ADR-010: MFA con TOTP para cuentas ADMIN

- **Estado:** Aceptada
- **Fecha:** 2026-10-06
- **Responsables:** David (implementación), Arquitectura
- **Ámbito:** HU-18. `auth/application/LoginService`, `auth/application/MfaService`, `auth/security/JwtTokenService`, `V41__mfa_totp.sql`

## Contexto

HU-18 exige un segundo factor al acceder al panel administrativo. Hoy el login solo pide correo y contraseña y devuelve un JWT directamente. Un JWT ya emitido vale para todo el API, así que un token intermedio mal distinguido permitiría saltarse el segundo factor.

## Decisión

1. **Obligatorio solo para `ADMIN`.** Clientes y profesionales conservan el login actual.
2. **Flujo en dos pasos.** Tras validar la contraseña, un ADMIN recibe un `mfaToken` de vida corta (5 minutos) con `typ=mfa`, no un access token. Con ese token llama a `/api/v1/auth/mfa/verify` (si ya tiene TOTP) o a `/api/v1/auth/mfa/enroll` + `/enroll/confirm` (si es su primer acceso).
3. **El `mfaToken` no sirve como acceso.** `JwtTokenService.parse()` rechaza cualquier token con `typ=mfa`, y `parseMfaPending()` rechaza cualquier token sin ese claim. Así el filtro JWT nunca lo acepta como sesión.
4. **Secreto TOTP cifrado en reposo** con AES-256-GCM. La clave viene de `MFA_ENCRYPTION_KEY` (32 bytes en base64). Si falta o es corta, la aplicación no arranca, igual que `JWT_SECRET`.
5. **10 códigos de recuperación** de un solo uso, generados al confirmar el enrolamiento y mostrados una sola vez. Se guardan con hash BCrypt.
6. **Límite de intentos compartido con el login.** Los fallos de código TOTP cuentan en `failed_login_attempts` y bloquean la cuenta con la misma política (`security.login.max-attempts`). Sin esto, un código de 6 dígitos podría probarse sin límite durante los 5 minutos del token.
7. **Librería:** `dev.samstevens.totp:totp:1.7.1` para generar el secreto y verificar códigos (RFC 6238). No se implementa el algoritmo a mano.

## Alternativas consideradas

| Alternativa | Motivo de descarte |
|---|---|
| MFA opcional por usuario | HU-18 habla de acceso administrativo obligatorio; dejarlo opcional deja cuentas ADMIN sin segundo factor |
| Secreto TOTP en texto plano | Un respaldo de la base expondría todos los secretos y permitiría generar códigos válidos |
| Código por correo | Depende de SMTP (HU-10 aún no existe), y el correo puede ser el mismo vector que se quiere proteger |
| Reutilizar el JWT normal como token intermedio | Permitiría saltarse el segundo factor usando el token parcial |

## Consecuencias

- `LoginResponse` gana campos `mfaRequired`, `mfaSetupRequired` y `mfaToken`. El frontend necesita un segundo paso para ADMIN.
- Render necesita la variable `MFA_ENCRYPTION_KEY` antes del siguiente deploy, o el backend no arrancará.
- Los ADMIN existentes deberán enrolar TOTP en su próximo inicio de sesión.
- Riesgo aceptado: dentro de la misma ventana de 30 segundos, un código TOTP válido podría reutilizarse. Lo mitiga el límite de intentos, no la deduplicación de pasos.
- Hay que revisar el orden de merge con HU-17 (PR #28), que también toca `UserAccount` y `SecurityConfiguration`.

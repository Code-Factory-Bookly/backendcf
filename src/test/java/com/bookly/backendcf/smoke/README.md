# Smoke Tests

Tests de humo del backend Bookly. Validan los aspectos mas basicos
del sistema en segundos (sin sembrar datos completos).

## Como ejecutarlos

### Solo smoke tests (sin el resto de la suite)

```bash
./mvnw -B test -Psmoke
```

### Toda la suite (incluyendo smoke)

```bash
./mvnw -B test
```

## Que cubren

| Clase | Proposito |
|---|---|
| `SmokeTest` | El ApplicationContext de Spring Boot arranca correctamente |
| `PublicEndpointsSmokeTest` | Endpoints publicos (`GET /servicios`) responden y los protegidos piden auth |
| `AuthFlowSmokeTest` | El endpoint `/auth/register` y `/auth/login` estan disponibles |

## Convenciones

- Cada clase de smoke usa `@Tag("smoke")` para que Surefire las
  pueda aislar con `-Psmoke`.
- Cada metodo usa `@DisplayName` con frases en espanol legibles,
  como exige el criterio de calidad "gherkin y casos positivos".
- No usan BD sembrada: validan solo disponibilidad del endpoint,
  no flujos completos. Para flujos completos, ver `src/test/java/.../*Test.java`.

## Cuando ejecutarlos en CI

Se recomienda ejecutar primero `-Psmoke` (rapido) y luego la suite
completa. Si los smoke fallan, no es necesario correr el resto.
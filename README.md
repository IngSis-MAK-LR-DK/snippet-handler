# Snippet Handler Service

Servicio de **Snippet Searcher** (IngSis 2026) que guarda, valida, lintea, formatea y testea
snippets. La metadata va a PostgreSQL, el contenido al bucket (asset service de la cátedra), los
permisos los decide el servicio de Permisos y cada lenguaje se usa a través de su adaptador.

```
UI (React) ──► Snippet Handler ──► Permisos            (HTTP, reenvía el token de Auth0)
                    │        ├───► Adaptador PrintScript (HTTP, ver docs/language-adapter-contract.md)
                    │        └───► Asset service ──► bucket
                    └──► PostgreSQL
```

## Módulos

| Módulo | Qué tiene |
|---|---|
| `language-api` | Interfaz `LanguageService` + `LanguageRegistry`. Java puro: es el contrato con cualquier lenguaje |
| `language-http` | `LanguageService` que llama a un adaptador por HTTP |
| `asset-client` | `AssetStore` y su cliente del asset service (`/v1/asset/{container}/{key}`) |
| `permission-client` | `PermissionService` y su cliente HTTP ([contrato](docs/permission-service-contract.md)) |
| `app` | Spring Boot: API REST, JPA + Flyway, seguridad con Auth0 |

## API

Todo requiere un access token de Auth0 (`Authorization: Bearer ...`) salvo `/actuator/health`.
Los errores siguen RFC 7807; un snippet inválido devuelve `422` con
`errors: [{rule, message, line, column}]`.

| Endpoint | User story |
|---|---|
| `POST /snippets` (multipart `file` + `name`, `description`, `language`, `version`) | 1 |
| `POST /snippets` (JSON con `content`) | 3 |
| `PUT /snippets/{id}` (multipart o JSON, todos los campos opcionales) | 2, 4 |
| `GET /snippets?relation=OWNER\|SHARED\|ALL&name=&language=&compliance=&sort=name,asc&page=&size=` | 5 |
| `GET /snippets/{id}` | 6 |
| `DELETE /snippets/{id}` | |
| `POST /snippets/{id}/share` `{"userId": "..."}` | 7 |
| `GET\|POST /snippets/{id}/tests`, `PUT\|DELETE /snippets/{id}/tests/{testId}` | 8 |
| `POST /snippets/{id}/tests/{testId}/run` | 6, 9 |
| `GET\|PUT /rules/format`, `GET\|PUT /rules/lint` (`?language=` opcional) | 11, 14 |
| `POST /snippets/{id}/format` | 11 |
| `GET /snippets/{id}/download?formatted=true\|false` | 13 |
| `GET /languages` | |
| `POST /snippets/format` `{language, version, content}` (formatea sin guardar) | 3, 4 |
| `POST /snippets/{id}/tests/preview` (corre un test sin guardarlo) | 8, 9 |
| `GET /users?query=&page=&size=` (busca en Auth0, excluye al caller) | 7 |
| `POST /languages/{lang}/versions/{version}/revalidate` (202, en background) | 5 |
| `GET /languages/revalidations/{id}` (progreso de la revalidación) | 5 |
| `GET /rules/reprocessing`, `GET /rules/reprocessing/{id}` (progreso del reproceso al cambiar reglas) | 12, 15 |

Al actualizar un snippet sus tests se corren en segundo plano (US 16). US 9 (streaming) y 10
quedan para cuando se vean en la cursada.

### Procesamiento en segundo plano (US 12, 15 y revalidación)

Hay **un solo sistema** para todo lo que se hace sobre snippets ya guardados, con Redis Streams.
Un job aplica una operación a cada snippet:

| Tipo | Se dispara con | Qué hace con cada snippet |
|---|---|---|
| `LINT` (US 15) | `PUT /rules/lint` | Lo lintea con las reglas vigentes de su dueño |
| `FORMAT` (US 12) | `PUT /rules/format` | Lo formatea con las reglas vigentes de su dueño |
| `REVALIDATE` | `POST /languages/{lang}/versions/{version}/revalidate` | Si es válido en la versión nueva, lo mueve a ella y lo lintea; si no, queda `FAILED` y conserva su versión |

Los endpoints responden enseguida y el trabajo sigue en segundo plano:

1. Quien dispara publica un `ReprocessRequest` en el stream `reprocess-requests` (las reglas se
   guardan antes; una revalidación guarda su job y devuelve el id).
2. `ReprocessingFanOut` lo consume, crea el `ReprocessJob` y una `ReprocessTask` (`PENDING`) por
   snippet (los del usuario en reglas; los de todos en una revalidación) y encola cada tarea en el
   stream `snippet-reprocess`. En `LINT` deja además los snippets en `PENDING` hasta evaluarlos.
3. `SnippetReprocessor` toma una tarea, hace la operación del tipo del job y la marca `DONE`.

Tolerancia a fallos (ambos streams usan el consumer group `snippet-handler`):

| Caso | Qué pasa |
|---|---|
| El adaptador falla o responde error | El mensaje no se confirma (ack) y vuelve a entregarse pasado `claim-idle`. |
| Un snippet falla `max-attempts` veces | Se **saltea**: tarea `FAILED` y el resto sigue. En `LINT` el snippet queda con lint `FAILED`. |
| El servicio se cae a mitad de camino | Al volver (o en otra réplica) se retoman los mensajes sin confirmar. Los intentos se cuentan antes de procesar, así un snippet que tumba el proceso tampoco se reintenta para siempre. |
| Mensaje entregado dos veces | Inocuo: una tarea terminada no se vuelve a procesar. |
| Redis caído al guardar reglas | Las reglas se guardan igual; el reproceso de ese cambio no se dispara y se loguea el error. |
| Redis caído al pedir una revalidación | El endpoint falla y no queda ningún job colgado. |

Progreso: `GET /rules/reprocessing` (últimos 20 del usuario) y `/{id}`, y
`GET /languages/revalidations/{id}`. Devuelven `total`, `done`, `failed`, `pending` y `status`
(`RUNNING` / `COMPLETED`; la revalidación muestra `processed` = `done` + `failed`). Configuración en
`snippet.reprocessing.*` (`enabled`, `max-attempts`, `claim-idle`, `poll-interval`) y
`REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD`.

## Desarrollo

Las reglas (Spotless, Checkstyle, JUnit, JaCoCo ≥ 80% por módulo) y el CI vienen de
[ci-kit](https://github.com/IngSis-MAK-LR-DK/ci-kit).

1. **Credenciales para bajar el plugin de ci-kit** (una vez por PC): crear un PAT classic con
   `read:packages` y agregarlo a `~/.gradle/gradle.properties`:
   ```properties
   gpr.user=tu-usuario-de-github
   gpr.key=el-token
   ```
2. **Pre-commit**: `pip install pre-commit && pre-commit install`. Cada commit corre `./gradlew check`.
3. **Docker** tiene que estar corriendo: los tests de integración levantan Postgres con Testcontainers.

```bash
./gradlew spotlessApply   # formatea
./gradlew check           # todo lo que corre el CI
```

### Levantar local

```bash
cp .env.example .env      # completar Auth0, GPR_USER y GPR_KEY
docker compose up --build
curl localhost:8080/actuator/health
```

| Servicio | Puerto en el host |
|---|---|
| Snippet Handler | 8080 |
| Permisos | 8081 (su Postgres en 5434) |
| Asset service (bucket) | 8082 |
| Adaptador PrintScript | 8083 |
| Postgres del handler | 5433 |

Auth0: `AUTH0_DOMAIN` (dominio del tenant, sin `https://`) y `AUTH0_AUDIENCE` (Identifier de la API), igual que en el [template de la cátedra](https://github.com/austral-ingsis/auth0-integration). El servicio valida firma, `exp`, `iss` y `aud` desde `application.yml`. Con `./gradlew bootRun` también lee el `.env` de la raíz.

El handler usa dos applications M2M del tenant, cada una con un solo permiso:

| Application | Variables | Autorizada para | Para qué |
|---|---|---|---|
| `snippet-handler-service` | `AUTH0_SERVICE_CLIENT_ID` / `_SECRET` | API `snippet-searcher` | Token propio para el trabajo en segundo plano, que no tiene usuario (tests que corren al editar un snippet, revalidación). Sin ellas, esas llamadas al adaptador salen sin token y responden 401 |
| `snippet-handler-management` | `AUTH0_MANAGEMENT_CLIENT_ID` / `_SECRET` | Auth0 Management API, solo `read:users` | Búsqueda de usuarios para compartir. Sin ellas, `GET /users` responde 502 |

En las requests de un usuario, el handler reenvía el token de ese usuario a Permisos y a los adaptadores.

Una versión nueva de PrintScript se agrega en `snippet.languages.printscript.versions` (y el adaptador tiene que soportarla); después se revalida con el endpoint de arriba.

Permisos y el adaptador corren desde sus propios repos. En Docker el handler los alcanza por `host.docker.internal` (fijo en `docker-compose.yml`); las URLs del `.env` son las de tu máquina y solo se usan con `./gradlew bootRun`.

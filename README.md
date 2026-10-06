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
| `GET /snippets?relation=OWNED\|SHARED\|ALL&name=&language=&compliance=&sort=name,asc&page=&size=` | 5 |
| `GET /snippets/{id}` | 6 |
| `DELETE /snippets/{id}` | |
| `POST /snippets/{id}/share` `{"userId": "..."}` | 7 |
| `GET\|POST /snippets/{id}/tests`, `PUT\|DELETE /snippets/{id}/tests/{testId}` | 8 |
| `POST /snippets/{id}/tests/{testId}/run` | 6, 9 |
| `GET\|PUT /rules/format`, `GET\|PUT /rules/lint` (`?language=` opcional) | 11, 14 |
| `POST /snippets/{id}/format` | 11 |
| `GET /snippets/{id}/download?formatted=true\|false` | 13 |
| `GET /languages` | |

Al actualizar un snippet sus tests se corren en segundo plano (US 16). US 9 (streaming),
10, 12 y 15 quedan para cuando se vean en la cursada; `SnippetEventPublisher` es el punto de
extensión para pasarlas a una cola.

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
cp .env.example .env      # completar Auth0
GITHUB_ACTOR=<usuario> GITHUB_TOKEN=<pat> docker compose up --build
curl localhost:8080/actuator/health
```

Permisos y el adaptador de PrintScript se esperan en `localhost:8082` y `localhost:8083`
(configurable en `.env`).

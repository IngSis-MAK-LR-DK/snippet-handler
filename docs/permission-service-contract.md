# Contrato con el servicio de Permisos

Acordado con el equipo de `permission-service`. Si cambia, en el handler solo se toca
`HttpPermissionService` (módulo `permission-client`).

## Reglas generales
- HTTP + JSON. Permisos corre en el puerto **8081** (su base en 5434). El handler corre en 8080
  y su base en 5433.
- El handler reenvía el JWT de Auth0 del usuario en `Authorization: Bearer <token>`. Permisos
  saca el usuario del `sub`: no se manda el userId en los bodies (salvo a quién se comparte).
- `snippetId` es un UUID (string) que genera el handler al crear el snippet.
- Roles: `OWNER` (edita, comparte, borra) y `READER` (lee el snippet y sus tests, y los corre).

## Endpoints

| # | Método y path | Body | Respuesta |
|---|---|---|---|
| 1 | `POST /permissions` | `{"snippetId": "..."}` | `201`; `409` si ya existe |
| 2 | `POST /permissions/{snippetId}/share` | `{"userId": "auth0\|..."}` | `204`; `403` si no es OWNER; `409` si ya estaba compartido |
| 3 | `GET /permissions/snippets?relation=owner\|shared\|all` (default `all`) | — | `200 [{"snippetId": "...", "role": "OWNER"}]` |
| 4 | `GET /permissions/{snippetId}` | — | `200 {"role": "OWNER" \| "READER" \| "NONE"}` |
| 5 | `DELETE /permissions/{snippetId}` | — | `204`; `403` si no es OWNER |

## Cómo lo usa el handler

| Operación del handler | Llamada | Rol mínimo |
|---|---|---|
| Crear snippet | (1) después de validar y guardar; si falla se revierte todo | — |
| Ver, descargar, listar tests, correr un test | (4) | `READER` |
| Editar, formatear, crear/editar/borrar tests | (4) | `OWNER` |
| Compartir | (4) y después (2) | `OWNER` |
| Borrar | (4) y después (5) | `OWNER` |
| Listado (US 5) | (3) y con esos ids filtra, ordena y pagina en su base | — |

Cómo se interpretan las respuestas:
- (4) con `404` o `403` se toma como `NONE`. Con `NONE`, o con un rol menor al necesario, el
  handler responde `403`.
- (2) con `409` (ya compartido) se toma como éxito.
- Un `403` de (2) o (5) llega al cliente como `403`; cualquier otro error, como `502`.

## Pendiente
- Búsqueda de usuarios para compartir (US 7): Permisos consulta la Auth0 Management API.
- Red de Docker compartida entre los compose de cada servicio.

# Contrato con el servicio de Permisos

> **Borrador**: es lo que el Snippet Handler asume hoy. Hay que acordarlo con el equipo de
> Permisos; si cambia, solo se toca `HttpPermissionService` (módulo `permission-client`).

Cada request lleva el `Authorization: Bearer <token de Auth0>` del usuario que llamó al
handler: Permisos saca el usuario del `sub` del token.

| Método y path | Body | Respuesta | Uso |
|---|---|---|---|
| `POST /permissions/snippets/{id}/owner` | — | `201` | El usuario pasa a ser owner de un snippet nuevo |
| `GET /permissions/snippets/{id}/check?permission=READ\|WRITE\|OWNER` | — | `200 {"allowed": true}` (`404`/`403` = sin permiso) | Antes de leer, editar, borrar o compartir |
| `POST /permissions/snippets/{id}/shares` | `{"userId": "auth0\|bob"}` | `201` | US 7: lectura sobre el snippet y sus tests |
| `GET /permissions/snippets?relation=OWNED\|SHARED\|ALL` | — | `200 {"snippetIds": ["..."]}` | US 5: listado |
| `DELETE /permissions/snippets/{id}` | — | `204` | Snippet borrado |

`OWNER` implica `WRITE` y `WRITE` implica `READ`. La búsqueda de usuarios para compartir
(US 7, "a medida que se escribe se filtran") la resuelve Permisos/Auth0, no el handler.

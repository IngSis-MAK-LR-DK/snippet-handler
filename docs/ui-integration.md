# Integración con la UI (`SnippetOperations`)

La UI de la cátedra (`austral-ingsis/printscript-ui`, `src/utils/snippetOperations.ts`) define
`SnippetOperations`, la interfaz que cada grupo implementa para hablar con su backend. Esta tabla
dice qué endpoint del handler usa cada método. Todos los requests llevan
`Authorization: Bearer <token de Auth0>`.

| Método de `SnippetOperations` | Endpoint del handler | Notas |
|---|---|---|
| `listSnippetDescriptors(page, pageSize, name)` | `GET /snippets?page=&size=&name=` | Filtros extra: `relation`, `language`, `compliance`, `sort` |
| `createSnippet({name, content, language, extension})` | `POST /snippets` (JSON) | `version` es opcional: si no llega se usa la última del lenguaje |
| `getSnippetById(id)` | `GET /snippets/{id}` | |
| `updateSnippetById(id, {content})` | `PUT /snippets/{id}` (JSON) | |
| `shareSnippet(snippetId, userId)` | `POST /snippets/{id}/share` | Responde `204`; la UI vuelve a pedir el snippet si lo necesita |
| `deleteSnippet(id)` | `DELETE /snippets/{id}` | |
| `formatSnippet(content)` | `POST /snippets/format` `{content, language?, version?}` | No guarda nada; usa las reglas de formato del usuario |
| `getTestCases()` | `GET /snippets/{id}/tests` | Necesita el id del snippet (ver temas a decidir) |
| `postTestCase(testCase)` | `POST /snippets/{id}/tests` o `PUT /snippets/{id}/tests/{testId}` | Con `id` es una edición |
| `removeTestCase(id)` | `DELETE /snippets/{id}/tests/{testId}` | |
| `testSnippet(testCase)` | `POST /snippets/{id}/tests/run` `{inputs, expectedOutputs}` | No guarda nada. `PASSED` = `"success"`, el resto = `"fail"` |
| `getFormatRules()` / `modifyFormatRule(rules)` | `GET` / `PUT /rules/format` | |
| `getLintingRules()` / `modifyLintingRule(rules)` | `GET` / `PUT /rules/lint` | |
| `getFileTypes()` | `GET /languages` | |
| `getUserFriends(name, page, pageSize)` | — | Es de Permisos, no del handler |

## Temas a decidir con el grupo

1. **Nombre del autor.** La tabla de snippets muestra `snippet.author`, pero el handler solo
   guarda el id de Auth0 del dueño (`auth0|...`). Opciones:
   - Agregar el nombre como claim personalizado en el access token (Action de Auth0) y que el
     handler lo guarde al crear el snippet.
   - Que la UI resuelva el nombre con el endpoint de usuarios de Permisos.
2. **Tests sin id de snippet.** `getTestCases()`, `postTestCase()` y `removeTestCase()` no reciben
   el id del snippet, y en el handler los tests cuelgan de un snippet. Opciones:
   - Agregar el id a esas firmas en el fork de la UI (el modal de tests está dentro de la pantalla
     del snippet, así que lo tiene a mano).
   - Que el handler exponga rutas planas `/tests/{testId}`.
3. **Formas de los datos.** La UI y el handler nombran distinto varias cosas. Hay que decidir si
   traduce la implementación de `SnippetOperations` o si el handler adopta los nombres de la UI:

   | La UI espera | El handler devuelve |
   |---|---|
   | Listado con `snippets`, `page`, `page_size`, `count` | `content` y `page: {size, number, totalElements, totalPages}` |
   | Parámetro `page_size` | Parámetro `size` |
   | Compliance `pending`, `compliant`, `not-compliant`, `failed` | `PENDING`, `COMPLIANT`, `NOT_COMPLIANT`, `FAILED` |
   | Snippet con `author` y `extension` | `ownerId`, sin extensión (sale de `GET /languages`) |
   | Reglas `{id, name, isActive, value}` | `{name, description, enabled, value}` |
   | Tests `{id, name, input, output}` | `{id, name, inputs, expectedOutputs, lastStatus}` |
   | Tipos de archivo `{language, extension}` | `{name, versions, fileExtension}` |
   | `shareSnippet` devuelve el snippet | `204` sin cuerpo |
4. **Selector de versión en la UI.** La consigna pide elegir la versión al crear un snippet. La
   UI de la cátedra no la manda; la última versión por defecto del handler es solo un respaldo.
5. **Búsqueda de usuarios (`getUserFriends`).** Se acordó que la resuelve Permisos con la Auth0
   Management API; falta que Permisos confirme el endpoint para la UI.

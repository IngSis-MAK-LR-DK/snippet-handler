# Contrato del adaptador de lenguaje

El Snippet Handler habla con cada lenguaje a través de la interfaz `LanguageService`
(módulo `language-api`). La implementación por defecto (`language-http`) espera que cada
adaptador exponga estos endpoints HTTP con JSON. Agregar un lenguaje nuevo = levantar su
adaptador y sumarlo en `snippet.languages` del `application.yml`:

```yaml
snippet:
  languages:
    printscript:
      url: http://printscript:8080
      versions: ["1.0", "1.1"]
      file-extension: ps
```

Las posiciones (`line`, `column`) empiezan en 1.

## `POST /validate`
Parsea el snippet. Siempre `200`; un snippet inválido no es un error HTTP.

```json
// request
{ "content": "let x: number = 1", "version": "1.1" }
// response
{ "valid": false,
  "errors": [{ "rule": "missing-semicolon", "message": "Expected ';'", "line": 1, "column": 18 }] }
```

## `POST /lint`
```json
// request
{ "content": "...", "version": "1.1",
  "rules": [{ "name": "camel-case", "enabled": true, "value": null }] }
// response
{ "violations": [{ "rule": "camel-case", "message": "Use camelCase", "line": 1, "column": 5 }] }
```

## `POST /format`
```json
// request
{ "content": "...", "version": "1.1", "rules": [{ "name": "indentation", "enabled": true, "value": "4" }] }
// response
{ "content": "let x: number = 1;\n" }
```

## `POST /execute`
Los `inputs` se consumen en orden cada vez que el snippet pide un input; `outputs` son las
líneas impresas con `println`, en orden.

```json
// request
{ "content": "...", "version": "1.1", "inputs": ["Alice"] }
// response
{ "outputs": ["Hello Alice"], "errors": [] }
```

## `WS /execute/stream` (US 9 y 10)

La misma ejecucion que `POST /execute`, pero en streaming por WebSocket: cada `println` llega
apenas se ejecuta y, si el snippet pide un input que no vino, se le pide al cliente y el programa
espera. El handshake lleva el mismo `Authorization: Bearer` que el resto.

Un mensaje JSON por frame de texto. El cliente empieza con:

```json
{ "type": "start", "content": "...", "version": "1.1", "inputs": ["Alice"], "interactive": true }
```

- `inputs` se usan primero, en orden. Cuando se acaban: con `interactive: false` la ejecucion
  falla (como `POST /execute`); con `true` el adaptador pide el siguiente.

| Del adaptador | Significa |
|---|---|
| `{"type":"output","line":"Hello Alice"}` | el snippet imprimio una linea |
| `{"type":"input","prompt":"Name:"}` | el snippet espera un input |
| `{"type":"done","errors":[]}` | termino (vacio si anduvo); despues el adaptador cierra |

| Del cliente | Significa |
|---|---|
| `{"type":"input","value":"Alice"}` | respuesta al ultimo `input` |

Si el input no llega en 5 minutos, o se corta la conexion, la ejecucion termina. Un mensaje
invalido cierra con 1008.

## `GET /rules?type=LINT|FORMAT`
Reglas predeterminadas del lenguaje.

```json
[{ "name": "indentation", "description": "Spaces per indentation level",
   "enabledByDefault": true, "defaultValue": "4" }]
```

Cualquier respuesta 4xx/5xx o un body vacío se reporta al cliente del handler como `502`.

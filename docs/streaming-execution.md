# Ejecucion en streaming para la UI (US 9 y 10)

`WS /snippets/{snippetId}/run` (detras del proxy: `wss://<dominio>/api/snippets/{id}/run`).

## Autenticacion

El browser no puede poner headers en un WebSocket, y un token en la URL quedaria en los logs del
proxy. El token va como subprotocolo:

```js
new WebSocket(url, ["bearer", accessToken])
```

El servidor contesta el subprotocolo `bearer`. Al abrir se comprueba que el usuario pueda leer el
snippet (rol READER o mas, la misma regla que correr un test por HTTP): si no, el handshake falla
con 401, 403 o 404.

## Mensajes

Una conexion = una ejecucion. La UI manda uno de estos para empezar:

| Mensaje | Que corre |
|---|---|
| `{"type":"run"}` | ejecucion interactiva (US 10): los inputs se piden a medida que hacen falta |
| `{"type":"test","testId":"<uuid>"}` | un test guardado (US 9); su resultado queda registrado |
| `{"type":"test","inputs":[...],"expectedOutputs":[...]}` | un test sin guardar; no se registra |

y despues, en la ejecucion interactiva, `{"type":"input","value":"..."}` por cada input pedido.

| Del servidor | Significa |
|---|---|
| `{"type":"output","line":"..."}` | el snippet imprimio una linea |
| `{"type":"input","prompt":"..."}` | el snippet espera un input (solo interactiva) |
| `{"type":"done","errors":[...]}` | termino la ejecucion interactiva |
| `{"type":"result","testId":...,"status":"PASSED","outputs":[...],"expectedOutputs":[...],"errors":[...]}` | termino el test |
| `{"type":"error","message":"..."}` | el pedido no se pudo hacer (test inexistente, ya habia una ejecucion) |

Despues de `done`, `result` o `error` el servidor cierra la conexion. Si la UI cierra antes, la
ejecucion se corta.

## Recorrido

```
UI --WS (token como subprotocolo)--> handler --WS (Authorization: Bearer)--> adaptador
```

El handler reenvia el token del usuario al adaptador y traduce los mensajes; el contrato con el
adaptador esta en [language-adapter-contract.md](language-adapter-contract.md).

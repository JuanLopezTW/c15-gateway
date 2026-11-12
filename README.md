# c15-gateway

**API Gateway** del EscapeRoom C15 (ARSW, equipo C15-PortalSuba), hecho con Spring Cloud Gateway. Es la única
puerta de entrada del front y además guarda el compose para levantar **todo el sistema** en local.

## Qué hace

| Ruta | Servicio |
|---|---|
| `/ws/**` (WebSocket STOMP) | `c15-broker` |
| `/api/salas/**`, `/api/equipos/**` | `c15-salas` |
| `/api/partidas/**`, `/dev/partidas/**` | `c15-gameplay` |
| `/api/perfil/**`, `/api/usuarios/**`, `/api/chat/**`, `/api/estadisticas/**` | `c15-social` |
| `/api/version` | el gateway (su commit) |

- **Token:** valida el JWT de Auth0 en la entrada. Lo que no trae token válido no llega a ningún servicio (401). Los servicios lo vuelven a validar.
  - Excepciones: `/ws` (el broker valida el token en el `CONNECT` de STOMP) y `/api/version`.
- **CORS:** un solo lugar (`CORS_ORIGENES`). Expone `X-Trace-Id` para que el front muestre el código de error.
- **Traza:** el `traceId` pasa a los servicios (`traceparent`) y desde ahí sigue por Kafka.
- **Tiempos:** si un servicio no responde, corta a los 3 s de conexión o 15 s de respuesta.

## Todo el sistema en local

Clona los repos uno al lado del otro: `c15-gateway`, `c15-broker`, `c15-salas`, `c15-gameplay` y `c15-social`.

```bash
cp .env.example .env           # y cambia las claves
docker compose up -d --build   # la primera vez compila los 5 proyectos (tarda)
```

- **Front:** `http://localhost:8080` es lo mismo que usa el front con Vite (`npm run dev`), así que no cambia nada.
- **Grafana** (trazas y logs): `http://localhost:3000`.
- **Consola de RabbitMQ:** `http://localhost:15672`.

Levanta:
- los 4 servicios y el gateway;
- Postgres (una base por servicio: `c15_salas`, `c15_gameplay`, `c15_social`);
- Kafka;
- RabbitMQ (relay STOMP);
- Redis;
- Grafana (Tempo + Loki).

## Pruebas

```bash
mvn verify   # rutas, token, CSRF, CORS y version contra servicios falsos; cobertura minima 80%
```

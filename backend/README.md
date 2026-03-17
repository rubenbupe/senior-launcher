# Senior Launcher Backend

WebSocket + HTTP backend to sync Senior Launcher devices and execute remote actions from the web panel.

## What it does

- Keeps a real-time connection with each device (`role=device`).
- Exposes a web panel (`/`) to send config and remote actions.
- Persists state and command queues in `device-sync-store.json`.
- Replays pending commands when a device reconnects.
- Blocks config updates if no persisted device state exists yet.

## Structure

- `server.ts`: entry point.
- `src/serverApp.ts`: HTTP/WS server.
- `src/store.ts`: persistence + in-memory runtime state.
- `src/parsers.ts`: payload validation/parsing.
- `src/types.ts`: shared types.
- `public/index.html`: web panel.
- `public/panel.js`: panel logic.

## Requirements

- Bun 1.1 or higher.

## Run locally

```bash
cd backend
bun install
bun run start
```

Optional env vars:

- `HOST` (default `0.0.0.0`)
- `PORT` (default `8080`)
- `BACKEND_API_TOKEN` (if set, protects HTTP and WS)

Example:

```bash
cd backend
BACKEND_API_TOKEN="my_token" bun run start
```

## Web panel

1. Open `http://localhost:8080/`.
2. Enter `Device ID` (same one configured in Android app).
3. If token is enabled, fill `Token`.
4. Click `Conectar WebSocket`.

From the panel you can:

- View current device state.
- Manage allowed apps without manually typing package names.
- Update config (`setConfig`).
- Execute actions (`runAction`, e.g. call or create contact).
- Request SMS data reported by the device.

## WebSocket protocol

Endpoints:

- `ws://<host>:<port>/ws?role=device&deviceId=<id>&token=<optional>`
- `ws://<host>:<port>/ws?role=web&deviceId=<id>&token=<optional>`

Messages from `device` to backend:

- `deviceState`
  - `currentConfig`
  - `availableApps`
  - `deviceInfo`
- `deviceData`
  - `sms` payload on demand
- `commandResult`

Messages from backend to `device`:

- `setConfig`
- `runAction`
- `requestData`

Messages from backend to `web`:

- `hello`
- `devicePresence`
- `deviceState`
- `deviceData`
- `commandResult`
- `accepted`
- `error`

## HTTP API

- `GET /health`
- `GET /api/state`
- `POST /api/config/update`
- `POST /api/action/run`

If `BACKEND_API_TOKEN` is enabled, use either:

- Header: `Authorization: Bearer <token>`
  or
- Query param: `?token=<token>`

## Docker

### Docker Compose (example)

```bash
cd backend
docker compose up --build
```

Included file: `docker-compose.yml`.

## Quick validation

1. Start backend.
2. Open web panel.
3. Connect with a valid `deviceId`.
4. Confirm `deviceState` arrives.
5. Send `setConfig` and verify `commandResult`.

# AGENTS.md – WordFlow project instructions

## Overview

WordFlow is a self‑hosted vocabulary trainer with a Fastify API, a React/Vite web UI, and an Android app (Capacitor). It stores data in SQLite, supports AI providers for answer checking, and can run offline.

## Dev environment

- **Node**: >=22.12 (as required by `package.json`)
- **npm**: use the bundled version
- Run `npm ci` at the repository root to install all workspaces.
- Copy `.env.example` to `.env` (or set env vars) before running the server.
- Native modules (`better-sqlite3`, `@node-rs/argon2`) may need Python, make, a C++ compiler (see Dockerfile comment lines 5‑7).

## Build & test

| Task                                                   | Exact command                               |
| ------------------------------------------------------ | ------------------------------------------- |
| Interactive setup (deps, .env, build, systemd service) | `./einrichten.sh` (`./einrichten.sh hilfe`) |
| Install dependencies                                   | `npm ci`                                    |
| Development (watch server + Vite)                      | `npm run dev`                               |
| Production build (web & server)                        | `npm run build`                             |
| Start production server                                | `npm start`                                 |
| Run all tests (server, web, shared)                    | `npm test`                                  |
| Type‑check all TypeScript packages                     | `npm run typecheck`                         |
| Code formatting (Prettier)                             | `npm run format`                            |
| Generate a new DB migration (server)                   | `npm run db:generate -w @wordflow/server`   |
| Build web only                                         | `npm run build -w @wordflow/web`            |
| Build server only                                      | `npm run build -w @wordflow/server`         |
| Run web tests only                                     | `npm run test -w @wordflow/web`             |
| Run server tests only                                  | `npm run test -w @wordflow/server`          |

All workspace scripts are invoked via the top‑level wrappers (`--workspaces --if-present`).

## Docker & deployment

- Build image: `docker build -t wordflow .`
- Run container (port 3000): `docker compose up -d`
- Optional profiles:
  - `ollama` – adds a local Ollama AI service.
  - `https` – adds Caddy for automatic TLS (edit `deploy/Caddyfile`).
- Volumes: `wordflow-data` holds `data/` (SQLite DB, uploads, `secrets.json`).

## Project structure

```
apps/server      Fastify backend, DB, AI integration
apps/web         React UI built with Vite (also the Capacitor Android project)
packages/shared  Shared TypeScript types, constants, Zod schemas
Dockerfile       Multi‑stage build for production image
docker-compose.yml  Compose file with optional Ollama & Caddy services
README.md        User‑facing documentation
```

## Conventions

- **TypeScript** with `tsconfig.base.json` (strict, noEmit). All source files are under `src/` (or `test/`).
- **Fastify** routes are mounted under `/api`; unknown non‑API GET routes serve `index.html` when `webDist` is set.
- **Session cookie** name: `wordflow_session` (httpOnly, sameSite=lax, secure on HTTPS).
- Environment variables are read via `loadConfig` in `apps/server/src/config.ts`. Missing values are generated and stored in `data/secrets.json` (mode 600) – do **not** edit this file manually.
- **Superuser** `admin@wordflow.com` / `admin` (`role = 'admin'`) is created on every start if missing (`ensureSuperuser`). Self-registration is disabled (`POST /api/auth/register` → 403); the superuser manages accounts, AI settings, backups and updates under `/api/admin/*` (`requireAdmin`).
- **Own packages**: `packages.user_id` is the owner; every account manages (and uploads photos for) only its own packages, other accounts get 404. Learning progress lives in `user_vocab` (a row = activated). Migration `0009` copied the former shared packages for each learning account.
- **AI setup** is global: `AiService` always uses the superuser's `provider_configs`/`task_models`.
- **Migrations run with foreign keys off** (`runMigrations` in `db/index.ts`), because table rebuilds inside the migrator's transaction would otherwise cascade-delete rows. `drizzle-kit` in the repo (0.18) cannot `generate`; write migrations by hand (SQL + `meta/_journal.json`).
- **systemd service** (`./einrichten.sh dienst installieren`) runs `apps/server/dist/index.js` with `NODE_ENV=production` and `WORDFLOW_SERVICE=1`; then `scripts/update.mjs` stops the server after the build and systemd (`Restart=always`) starts the new version.
- **Update button** runs `scripts/update.mjs` as a detached process (downloads `justusvoelkle3141592654-ux/Phase-6-2.0@claude/new-session-uq36ca`, keeps `data/`, `.env`, `node_modules/`).
- **Account picker**: there is no online/offline switch any more; `GET /api/auth/mode` always lists all accounts (email + name) so the sign-in page can offer them; passwords are still checked. There is no setup wizard and no learning reminder.
- **Daily limit**: `user_settings.daily_limit` (default 30) caps the different words asked per day (`learn/limit.ts`); words already asked today may always come again.
- **AI providers** are configured by the superuser (separate provider + model for `check` and `vision`); the server stores encrypted API keys using `APP_SECRET` (generated if absent).
- **Prettier** formatting is enforced via `npm run format`.

## Pitfalls & gotchas

- The server expects a `.env` file (or env vars). Missing `DATA_DIR` or `APP_SECRET` will be auto‑generated, but the generated secret is stored in `data/secrets.json`; losing that file invalidates saved API keys.
- Port 3000 must be free unless using Docker (which maps it). Conflicts cause the server to abort on start.
- Native modules may fail to compile on a fresh system; install build essentials (`build-essential` on Debian/Ubuntu) before `npm ci`.
- Do not edit `secrets.json` manually – it is written with mode 600 and read only by the server.
- When running the Docker Compose stack, the `wordflow-data` volume persists across container restarts; clearing it removes the DB and uploaded photos.
- Changing `APP_SECRET` after initial start requires re‑entering all AI API keys.
- The Android app cannot use cookies; it authenticates via Bearer token – ensure CORS origins include the app URL (handled automatically).
- For DB schema changes, run the migration generation command **after** updating `apps/server/src/db/schema.ts**`.
- The `npm run dev` script uses `concurrently` to start server watch (`tsx watch`) and Vite dev server; both logs are interleaved.
- `npm run android:sync` builds the web assets and synchronises them to the Capacitor Android project; run after any UI changes before building the APK.

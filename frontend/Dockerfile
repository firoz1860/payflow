# syntax=docker/dockerfile:1
# Build context is ./frontend (see docker-compose.yml).

# ---- Stage 1: build the Vite/React bundle ----
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json ./
# Use a lockfile-aware install when a lock is present, else a plain install.
RUN if [ -f package-lock.json ]; then npm ci --no-audit --no-fund; \
    else npm install --no-audit --no-fund; fi
COPY . .
# VITE_API_URL is intentionally left unset so the bundle calls the relative
# "/api/v1", which nginx (below) proxies to the api-gateway on the compose
# network — same-origin, so no CORS is involved.
RUN npm run build

# ---- Stage 2: serve the static bundle + proxy /api to the gateway ----
FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD wget -qO- http://localhost:80/ >/dev/null 2>&1 || exit 1

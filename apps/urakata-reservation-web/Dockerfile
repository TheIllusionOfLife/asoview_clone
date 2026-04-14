# Multi-stage Next.js standalone build for urakata-reservation-web.

# ---------- deps ----------
FROM oven/bun:1 AS deps
WORKDIR /repo
COPY package.json bun.lock* bun.lockb* ./
COPY apps/ads-web/package.json apps/ads-web/package.json
COPY apps/area-gate-web/package.json apps/area-gate-web/package.json
COPY apps/asoview-web/package.json apps/asoview-web/package.json
COPY apps/furusato-web/package.json apps/furusato-web/package.json
COPY apps/gift-web/package.json apps/gift-web/package.json
COPY apps/overseas-web/package.json apps/overseas-web/package.json
COPY apps/scanner-app/package.json apps/scanner-app/package.json
COPY apps/urakata-reservation-web/package.json apps/urakata-reservation-web/package.json
COPY apps/urakata-ticket-web/package.json apps/urakata-ticket-web/package.json
COPY libraries/frontend-shared/package.json libraries/frontend-shared/package.json
COPY libraries/design-tokens/package.json libraries/design-tokens/package.json
RUN bun install --frozen-lockfile

# ---------- build ----------
FROM oven/bun:1 AS build
WORKDIR /repo
COPY --from=deps /repo/node_modules ./node_modules
COPY . .
ARG NEXT_PUBLIC_API_BASE_URL
ARG NEXT_PUBLIC_FIREBASE_API_KEY
ARG NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN
ARG NEXT_PUBLIC_FIREBASE_PROJECT_ID
ARG NEXT_PUBLIC_FIREBASE_APP_ID
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL \
    NEXT_PUBLIC_FIREBASE_API_KEY=$NEXT_PUBLIC_FIREBASE_API_KEY \
    NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=$NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN \
    NEXT_PUBLIC_FIREBASE_PROJECT_ID=$NEXT_PUBLIC_FIREBASE_PROJECT_ID \
    NEXT_PUBLIC_FIREBASE_APP_ID=$NEXT_PUBLIC_FIREBASE_APP_ID \
    NEXT_TELEMETRY_DISABLED=1
WORKDIR /repo/apps/urakata-reservation-web
RUN bun run build

# ---------- runtime ----------
FROM node:22-slim AS runtime
WORKDIR /app
ENV NODE_ENV=production \
    PORT=3000 \
    HOSTNAME=0.0.0.0 \
    NEXT_TELEMETRY_DISABLED=1
COPY --from=build /repo/apps/urakata-reservation-web/.next/standalone ./
COPY --from=build /repo/apps/urakata-reservation-web/.next/static ./apps/urakata-reservation-web/.next/static
COPY --from=build /repo/apps/urakata-reservation-web/public ./apps/urakata-reservation-web/public
USER node
EXPOSE 3000
WORKDIR /app/apps/urakata-reservation-web
ENTRYPOINT ["node", "server.js"]

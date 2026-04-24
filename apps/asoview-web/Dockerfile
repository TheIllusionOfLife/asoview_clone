# Multi-stage Next.js standalone build for asoview-web.
#
# Monorepo standalone layout note: when next.config.ts has
# `output: "standalone"` inside a workspace, Next produces
#   .next/standalone/apps/asoview-web/server.js
# NOT .next/standalone/server.js. Static + public must land at
#   .next/standalone/apps/asoview-web/.next/static
#   .next/standalone/apps/asoview-web/public
# This was a gotcha on PR #24 CI work.
#
# NEXT_PUBLIC_* vars are inlined at build time — pass via --build-arg.

# ---------- deps ----------
FROM oven/bun:1 AS deps
WORKDIR /repo
COPY package.json bun.lock* bun.lockb* ./
# Root package.json declares `workspaces: ["apps/*", ...]` and bun.lock
# pins all 9 app workspaces + 2 libraries. bun install --frozen-lockfile
# fails if the resolved workspace set doesn't match the lockfile, so
# every app/*/package.json + libraries/*/package.json must be present
# for deps resolution — even though the build stage below only uses
# asoview-web. Keep this list in sync with the apps/ directory.
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
ARG NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY
ARG NEXT_PUBLIC_ENABLE_GOOGLE_SIGNIN=false
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL \
    NEXT_PUBLIC_FIREBASE_API_KEY=$NEXT_PUBLIC_FIREBASE_API_KEY \
    NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=$NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN \
    NEXT_PUBLIC_FIREBASE_PROJECT_ID=$NEXT_PUBLIC_FIREBASE_PROJECT_ID \
    NEXT_PUBLIC_FIREBASE_APP_ID=$NEXT_PUBLIC_FIREBASE_APP_ID \
    NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY=$NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY \
    NEXT_PUBLIC_ENABLE_GOOGLE_SIGNIN=$NEXT_PUBLIC_ENABLE_GOOGLE_SIGNIN \
    NEXT_TELEMETRY_DISABLED=1
WORKDIR /repo/apps/asoview-web
RUN bun run build

# ---------- runtime ----------
FROM node:22-slim AS runtime
WORKDIR /app
ENV NODE_ENV=production \
    PORT=3000 \
    HOSTNAME=0.0.0.0 \
    NEXT_TELEMETRY_DISABLED=1
# Copy the full standalone tree (preserves node_modules + workspace hoisting).
COPY --from=build /repo/apps/asoview-web/.next/standalone ./
# Static assets land next to the monorepo-nested server.js.
COPY --from=build /repo/apps/asoview-web/.next/static ./apps/asoview-web/.next/static
COPY --from=build /repo/apps/asoview-web/public ./apps/asoview-web/public
# The COPY commands above run as root, so /app/apps/asoview-web/.next is
# owned by root:root. Next.js's ISR runtime then tries to `mkdir .next/
# cache` as USER node and fails with ENOENT/EACCES, logging a "Failed to
# update prerender cache" warning on every dynamic render. Least-privilege
# fix: chown the `.next` directory itself (so node can create new subdirs
# like `cache/`) and recursively chown only `.next/cache/` (so node can
# write ISR entries). Root-owned files inside `.next/` such as `server.js`
# and `prerender-manifest.json` stay read-only at runtime.
RUN mkdir -p /app/apps/asoview-web/.next/cache \
    && chown node:node /app/apps/asoview-web/.next \
    && chown -R node:node /app/apps/asoview-web/.next/cache
USER node
EXPOSE 3000
WORKDIR /app/apps/asoview-web
ENTRYPOINT ["node", "server.js"]

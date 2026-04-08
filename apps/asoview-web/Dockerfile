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
COPY apps/asoview-web/package.json apps/asoview-web/package.json
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
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL \
    NEXT_PUBLIC_FIREBASE_API_KEY=$NEXT_PUBLIC_FIREBASE_API_KEY \
    NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=$NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN \
    NEXT_PUBLIC_FIREBASE_PROJECT_ID=$NEXT_PUBLIC_FIREBASE_PROJECT_ID \
    NEXT_PUBLIC_FIREBASE_APP_ID=$NEXT_PUBLIC_FIREBASE_APP_ID \
    NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY=$NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY \
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
USER node
EXPOSE 3000
WORKDIR /app/apps/asoview-web
ENTRYPOINT ["node", "server.js"]

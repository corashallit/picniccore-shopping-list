# Picnic List proxy, self-hosted (docs/PROXY.md §4). Build context: proxy/. Run it with compose.yaml.
#
# The proxy has no runtime dependencies (wrangler is only for Cloudflare deploys), so nothing is installed:
# the image is the official Node image plus a few kilobytes of JavaScript, owned by root and read-only to
# the unprivileged "node" user (uid 1000) that runs it. Works with a read-only root filesystem.
#
# Node 22 LTS on Alpine, pinned by the multi-arch index digest (amd64, arm64, ...). To update: look up the
# current 22.x-alpine tag on https://hub.docker.com/_/node and replace both the tag and the digest
# (docker buildx imagetools inspect node:<tag> prints the digest).
FROM node:22.23.3-alpine3.24@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402

ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=8787

WORKDIR /app
COPY package.json ./
COPY src/ ./src/
COPY scripts/server.mjs scripts/healthcheck.mjs scripts/mint-token.mjs scripts/smoke.mjs ./scripts/

# The base image's unprivileged "node" user, by number so runtimes can check it isn't root.
USER 1000:1000
EXPOSE 8787

# GET /healthz answers 200 only while the configuration is valid.
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD ["node", "scripts/healthcheck.mjs"]

# SIGTERM drains in-flight requests for up to SHUTDOWN_GRACE_SECONDS (120 s; compose.yaml waits 130 s).
STOPSIGNAL SIGTERM
CMD ["node", "scripts/server.mjs"]

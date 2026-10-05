# =============================================================================
# Stage 1: Build React/Vite Application
# =============================================================================
FROM node:20-alpine AS builder

WORKDIR /app

# Install dependencies
COPY package.json package-lock.json* ./
RUN npm install

# Build static assets
COPY . .
RUN npm run build

# =============================================================================
# Stage 2: Minimal Web Server Runtime
# =============================================================================
FROM nginx:alpine

# Copy custom Nginx configuration with reverse proxy for API & WebSocket
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy compiled SPA assets from builder
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1/ || exit 1

CMD ["nginx", "-g", "daemon off;"]

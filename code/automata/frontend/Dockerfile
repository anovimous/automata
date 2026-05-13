# =========================================================
# Stage 1 — build
# =========================================================
FROM node:20-alpine AS build

WORKDIR /app

# Install deps with a deterministic lockfile if present.
COPY package.json package-lock.json* ./
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi

COPY . .
RUN npm run build

# =========================================================
# Stage 2 — serve via nginx
# =========================================================
FROM nginx:alpine AS runtime

# Remove the default nginx site
RUN rm /etc/nginx/conf.d/default.conf

# Drop in our SPA + /api reverse-proxy config
COPY nginx.conf /etc/nginx/conf.d/automata.conf

# Static assets from the build stage
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

# Run nginx in the foreground
CMD ["nginx", "-g", "daemon off;"]

# Stage 1: Build the ClojureScript app
FROM clojure:temurin-21-tools-deps-bookworm-slim AS build

# Install Node.js (needed for shadow-cljs)
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    curl -fsSL https://deb.nodesource.com/setup_22.x | bash - && \
    apt-get install -y --no-install-recommends nodejs && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install npm dependencies first (layer caching)
COPY package.json package-lock.json* ./
RUN npm ci

# Copy Clojure dependency files and prefetch deps
COPY deps.edn shadow-cljs.edn ./
RUN clojure -A:dev -P

# Copy source and static assets
COPY src/ src/
COPY resources/ resources/

# Build the release bundle
RUN clojure -M:dev -m shadow.cljs.devtools.cli release app

# Stage 2: Serve with nginx
FROM nginx:alpine

COPY --from=build /app/resources/public /usr/share/nginx/html

EXPOSE 80

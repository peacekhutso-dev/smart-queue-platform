FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
# COPY preserves source file modes as-is — an asset that happens to be
# owner-only (600) on the host (e.g. dha-logo.webp was) is unreadable by
# nginx's non-root worker process and serves a 403, not a missing-file 404.
RUN chmod -R a+rX /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

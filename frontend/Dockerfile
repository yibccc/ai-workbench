FROM node:24-alpine AS build
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM nginx:1.28-alpine
RUN apk add --no-cache apache2-utils
COPY --from=build /build/dist /usr/share/nginx/html
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY deploy/40-workbench-auth.sh /docker-entrypoint.d/40-workbench-auth.sh
RUN chmod +x /docker-entrypoint.d/40-workbench-auth.sh
EXPOSE 80

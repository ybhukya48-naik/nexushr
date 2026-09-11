FROM node:22-alpine AS build

WORKDIR /app

COPY nexushr-frontend/package*.json ./
RUN npm ci

COPY nexushr-frontend/ ./

ENV VITE_API_BASE_URL=/api/v1

RUN npm run build

FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY infra/docker/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

FROM node:20-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

ARG BACKEND_API_BASE_URL=http://localhost:8080/api
ARG BACKEND_WS_BASE_URL=http://localhost:8080
ENV BACKEND_API_BASE_URL=${BACKEND_API_BASE_URL}
ENV BACKEND_WS_BASE_URL=${BACKEND_WS_BASE_URL}

RUN npm run build

FROM nginx:1.27-alpine

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

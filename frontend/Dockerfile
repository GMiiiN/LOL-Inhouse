# 역할: 1단계에서 Node로 프런트를 빌드하고, 2단계 nginx가 정적 파일을 서비스한다(설정은 infra/nginx/default.conf를 compose가 마운트).
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /src/dist /usr/share/nginx/html

# Stage 1: build the Angular app
FROM node:18-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npx ng build --configuration production

# Stage 2: serve with nginx (also proxies /api to the backend container)
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/rp-frontend /usr/share/nginx/html
EXPOSE 80

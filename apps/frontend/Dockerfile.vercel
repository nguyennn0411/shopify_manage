FROM node:24-alpine AS build
WORKDIR /workspace
COPY package.json ./
RUN npm install --ignore-scripts
COPY . .
RUN npm run build

FROM nginx:1.29-alpine
COPY --from=build /workspace/dist /usr/share/nginx/html
COPY nginx-spa.conf /etc/nginx/conf.d/default.conf
EXPOSE 80

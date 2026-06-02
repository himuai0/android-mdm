FROM node:18-alpine
RUN apk add --no-cache openjdk11-jdk android-tools
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN mkdir -p uploads builder/outputs
EXPOSE 3000
CMD ["node", "server.js"]

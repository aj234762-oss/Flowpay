FROM node:22-alpine
WORKDIR /app
COPY package.json server.mjs index.html ./
ENV PORT=8081
ENV HOST=0.0.0.0
EXPOSE 8081
CMD ["npm", "start"]

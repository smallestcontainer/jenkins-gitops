FROM node:trixie-slim

WORKDIR /app

RUN npm install --save smee-client dotenv

COPY index.js .

CMD [ "node", "index.js" ]

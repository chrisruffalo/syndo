# use node for the ease of setting up a webhook service
FROM node:16-alpine

# set node environment
ENV NODE_ENV=production
WORKDIR /app

# run the npm install for the required libraries
RUN npm init -y && \
    npm install body-parser express fs https request js-base64

# copy in the mutator application
COPY mutator.js cache-mutator-handler.js /app/

# set the entrypoint to run the mutator
CMD [ "node", "/app/mutator.js"]
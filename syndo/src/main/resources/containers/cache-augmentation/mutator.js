// from https://github.com/soukron/openshift-admission-webhooks/blob/master/enforceenv/src/app.js

// libraries
const bodyParser = require('body-parser'),
      express    = require('express'),
      fs         = require('fs'),
      https      = require('https');

// ssl certificate
const sslKey  = fs.readFileSync('/opt/app-root/src/ssl/tls.key', 'utf8'),
      sslCert = fs.readFileSync('/opt/app-root/src/ssl/tls.crt', 'utf8');

// listen port
const port = process.env.PORT || 8443;

// server instance and middlewares for parsing the body and logging
var app = express();
app.use(bodyParser.json());

// health check for readiness
app.get('/health', (req, res) => {
    res.status(200).end();
});

// include specific application handler
const handler = require('./cache-mutator-handler.js');
app.use('', handler);

// start server
https.createServer({
    key: sslKey,
    cert: sslCert
}, app)
.listen(port, () => {
    console.log(`[Main] Server running on port ${port}...`);
});
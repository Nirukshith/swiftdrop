// Minimal SSE client for the M2 end-to-end check (no dependencies).
// Prints "<epoch ms> <line>" for every non-comment line it receives, so the time between a status
// change and its event on the client can be measured.
// Usage: node sse-timed.js <stream url> [Last-Event-ID]
const http = require('http');

const [url, lastEventId] = process.argv.slice(2);
const headers = { Accept: 'text/event-stream' };
if (lastEventId) headers['Last-Event-ID'] = lastEventId;

http
  .get(url, { headers }, (res) => {
    console.log(`${Date.now()} HTTP ${res.statusCode} ${res.headers['content-type']}`);
    let buffer = '';
    res.setEncoding('utf8');
    res.on('data', (chunk) => {
      buffer += chunk;
      let i;
      while ((i = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, i).replace(/\r$/, '');
        buffer = buffer.slice(i + 1);
        if (line && !line.startsWith(':')) console.log(`${Date.now()} ${line}`);
      }
    });
    res.on('end', () => console.log(`${Date.now()} END`));
  })
  .on('error', (e) => console.log(`${Date.now()} ERROR ${e.message}`));

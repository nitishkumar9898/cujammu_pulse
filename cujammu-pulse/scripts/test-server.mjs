// Local static QA server only. No university data or authentication simulation lives here.
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { resolve, sep } from 'node:path';
const root = resolve(process.env.PULSE_QA_DIST ?? 'dist');
const port = Number(process.env.PULSE_QA_PORT ?? 4183);
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Invalid loopback QA port');
createServer(async (req, res) => {
  try {
    const path = decodeURIComponent(new URL(req.url, 'http://127.0.0.1').pathname);
    if (!path.startsWith('/app/')) { res.writeHead(404).end(); return; }
    const file = resolve(root, path === '/app/' ? 'index.html' : path.slice(5));
    if (!file.startsWith(root + sep)) { res.writeHead(404).end(); return; }
    const body = await readFile(file);
    res.writeHead(200, { 'Content-Type': file.endsWith('.js') ? 'text/javascript' : file.endsWith('.html') ? 'text/html' : 'application/octet-stream' });
    res.end(body);
  } catch { res.writeHead(404).end(); }
}).listen(port, '127.0.0.1');

/**
 * Pom Grade — Local Server for Testing OAuth 2.0
 * จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 8080;
const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml'
};

const server = http.createServer((req, res) => {
  let reqPath = decodeURIComponent(req.url.split('?')[0]);
  if (reqPath === '/' || reqPath === '') {
    reqPath = '/web-console/index.html';
  }
  const filePath = path.join(__dirname, reqPath);

  if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
    const ext = path.extname(filePath).toLowerCase();
    res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream' });
    fs.createReadStream(filePath).pipe(res);
  } else {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('404 Not Found');
  }
});

server.listen(PORT, () => {
  console.log('===================================================');
  console.log(' Pom Grade Web Console - Local Server');
  console.log(' จัดทำโดย ครูสุวรรณษา เดชรักษา');
  console.log('===================================================');
  console.log(`เว็บเซิร์ฟเวอร์พร้อมใช้งานที่: http://localhost:${PORT}/web-console/index.html`);
  console.log('กด Ctrl+C เพื่อหยุดการทำงาน');
});


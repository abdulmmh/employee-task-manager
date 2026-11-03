// Only this public URL is written into the Angular bundle. Never include secrets.
const fs = require('fs');
const path = require('path');
const raw = process.env.API_BASE_URL && process.env.API_BASE_URL.trim();
if (process.env.NETLIFY === 'true' && !raw) {
  throw new Error('Set public API_BASE_URL to the deployed backend URL ending in /api.');
}
let apiUrl = '/api';
if (raw) {
  const url = new URL(raw);
  const local = url.hostname === 'localhost' || url.hostname === '127.0.0.1';
  if ((!local && url.protocol !== 'https:') || (local && !['http:', 'https:'].includes(url.protocol))
    || url.username || url.password || url.search || url.hash || url.pathname.replace(/\/$/, '') !== '/api') {
    throw new Error('API_BASE_URL must be an HTTPS backend URL ending in /api, without credentials/query/hash. HTTP is allowed for localhost only.');
  }
  apiUrl = url.href.replace(/\/$/, '');
}
fs.writeFileSync(path.join(__dirname, '../src/environments/environment.prod.ts'),
  `// Public API URL configured at build time.\nexport const environment = {\n  production: true,\n  apiUrl: ${JSON.stringify(apiUrl)}\n};\n`);

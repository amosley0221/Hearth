// Minimal offline shell (only active when served over HTTPS; iOS home-screen apps work without it).
const CACHE = 'hearth-shell-v1';
const SHELL = ['./', 'app.css', 'app.js', 'manifest.webmanifest', 'icon-192.png'];
self.addEventListener('install', e => e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL))));
self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);
  if (url.pathname.startsWith('/api/') || url.pathname === '/ws') return;
  e.respondWith(fetch(e.request).catch(() => caches.match(e.request)));
});

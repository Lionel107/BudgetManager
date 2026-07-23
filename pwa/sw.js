// Service worker minimal : met en cache la coquille de l'app pour un chargement
// hors-ligne. Les données restent en réseau (online-first) — non mises en cache.
const CACHE = "budgetmanager-pwa-v1";
const SHELL = ["./", "./index.html", "./app.js", "./config.js", "./manifest.webmanifest", "./icon.svg"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  // On ne gère que la coquille locale ; tout le reste (API Supabase, CDN) passe au réseau.
  if (e.request.method !== "GET" || url.origin !== self.location.origin) return;
  e.respondWith(
    caches.match(e.request).then((hit) => hit || fetch(e.request))
  );
});

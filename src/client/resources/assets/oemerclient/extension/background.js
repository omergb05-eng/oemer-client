// Ömer Bridge: leitet Anfragen vom YouTube-Tab an den kleinen Server von Ömer Client (nur 127.0.0.1, dein eigener PC) weiter.
const PORTS = [43822, 43823, 43824, 43825, 43826];
let port = null;

async function tryFetch(p, path) {
  const c = new AbortController();
  const t = setTimeout(() => c.abort(), 1500);
  try {
    const r = await fetch('http://127.0.0.1:' + p + path, { cache: 'no-store', signal: c.signal });
    return await r.text();
  } finally {
    clearTimeout(t);
  }
}

async function call(path) {
  if (port) {
    try { return await tryFetch(port, path); } catch (e) { port = null; }
  }
  for (const p of PORTS) {
    try {
      const text = await tryFetch(p, path);
      port = p;
      return text;
    } catch (e) { /* nächster Port */ }
  }
  return null;
}

chrome.runtime.onMessage.addListener((m, sender, send) => {
  if (!m || typeof m.path !== 'string' || !m.path.startsWith('/api/')) { send(null); return false; }
  call(m.path).then(send);
  return true;
});

// Ömer Bridge – läuft nur in YouTube-Tabs, die Ömer Client geöffnet hat (Adresse enthält &omer=1).
// Kein Werbe-Blocker, kein Überspringen: Werbung läuft normal, den "Überspringen"-Knopf drückst du selbst.
(() => {
  const params = new URL(location.href).searchParams;
  if (params.get('omer') === '1') sessionStorage.setItem('omer', '1');
  if (sessionStorage.getItem('omer') !== '1') return;

  const enc = encodeURIComponent;
  const bg = (path) => new Promise((resolve) => {
    try { chrome.runtime.sendMessage({ path }, (r) => resolve(chrome.runtime.lastError ? null : r)); } catch (e) { resolve(null); }
  });
  const getState = async () => {
    const t = await bg('/api/state?ext=1');
    if (!t) return null;
    try { return JSON.parse(t); } catch (e) { return null; }
  };

  const videoId = () => new URL(location.href).searchParams.get('v') || '';
  const videoEl = () => document.querySelector('video.html5-main-video') || document.querySelector('video');
  const playerEl = () => document.querySelector('.html5-video-player');
  const adShowing = () => { const p = playerEl(); return !!(p && p.classList.contains('ad-showing')); };

  let st = null, lastSeek = null, lastOwn = 0, needClick = false, errorSentFor = -1, mainDur = 0, hooked = null;

  // kleine Anzeige, damit man sieht, dass die Verbindung steht
  const badge = document.createElement('div');
  badge.style.cssText = 'position:fixed;left:12px;bottom:12px;z-index:2147483647;padding:6px 10px;border-radius:8px;'
    + 'background:rgba(20,16,32,.88);color:#fff;font:12px system-ui,sans-serif;pointer-events:none;border:1px solid rgba(180,140,255,.5)';
  document.documentElement.appendChild(badge);
  const say = (t) => { if (badge.textContent !== t) badge.textContent = t; };

  const own = () => Date.now() - lastOwn < 1200;
  const cmd = (q) => bg('/api/cmd?' + q);

  function hook(v) {
    if (hooked === v) return;
    hooked = v;
    v.addEventListener('pause', () => {
      setTimeout(() => {
        if (!st || st.mode !== 1 || !v.paused || v.ended || adShowing() || own()) return;
        if (st.play) cmd('do=pause');
      }, 500);
    });
    v.addEventListener('play', () => {
      setTimeout(() => {
        if (!st || st.mode !== 1 || v.paused || adShowing() || own()) return;
        if (!st.play) cmd('do=play');
      }, 300);
    });
    v.addEventListener('ended', () => {
      if (!st || st.mode !== 1 || adShowing()) return;
      if (mainDur > 0 && Math.abs(v.duration - mainDur) > 2) return; // das war eine Werbung
      cmd('do=ended&seq=' + st.seq);
    });
  }

  async function tick() {
    const s = await getState();
    if (!s) { say('Ömer Client nicht erreichbar – ist Minecraft offen?'); return; }
    st = s;
    if (s.mode !== 1) { say('Ömer Bridge: aus (im Ömer-Menü „YouTube-Seite“ wählen)'); return; }
    const v = videoEl();
    if (v) hook(v);

    if (!s.videoId) {
      if (v && !v.paused) { lastOwn = Date.now(); v.pause(); }
      say('Ömer Client verbunden ✔ – kein Song');
      return;
    }
    if (videoId() !== s.videoId) {
      const last = Number(sessionStorage.getItem('omerNav') || 0);
      if (Date.now() - last > 4000) {
        sessionStorage.setItem('omerNav', String(Date.now()));
        sessionStorage.setItem('omerSeq', String(s.seq));
        location.href = 'https://www.youtube.com/watch?v=' + s.videoId + '&omer=1';
      }
      say('Ömer Client: lade Song …');
      return;
    }
    if (!v) { say('Ömer Client: warte auf den Player …'); return; }

    const ad = adShowing();
    if (!ad && isFinite(v.duration) && v.duration > 0) mainDur = v.duration;

    // gleicher Song nochmal gestartet (Wiederholen / nochmal anklicken)
    if (String(s.seq) !== sessionStorage.getItem('omerSeq')) {
      sessionStorage.setItem('omerSeq', String(s.seq));
      errorSentFor = -1;
      if (!ad && v.currentTime > 3) { lastOwn = Date.now(); v.currentTime = 0; }
    }
    if (lastSeek === null) lastSeek = s.seekSeq;
    if (s.seekSeq !== lastSeek) {
      lastSeek = s.seekSeq;
      if (!ad) { lastOwn = Date.now(); v.currentTime = s.seekMs / 1000; }
    }
    const vol = Math.max(0, Math.min(1, (s.volume ?? 60) / 100));
    if (Math.abs(v.volume - vol) > 0.01) v.volume = vol;
    if (v.muted && s.play) v.muted = false;

    if (s.play && v.paused && !v.ended) {
      lastOwn = Date.now();
      v.play().then(() => { needClick = false; }).catch(() => { needClick = true; });
    } else if (!s.play && !v.paused) {
      lastOwn = Date.now();
      v.pause();
    }

    // Video nicht verfügbar (privat, gelöscht)
    const err = document.querySelector('.ytp-error');
    if (err && err.offsetParent !== null && errorSentFor !== s.seq) {
      errorSentFor = s.seq;
      cmd('do=error&code=100&seq=' + s.seq);
    }

    const title = (document.querySelector('h1.ytd-watch-metadata yt-formatted-string')?.textContent
      || document.title.replace(/ - YouTube$/, '')).trim();
    const author = (document.querySelector('ytd-channel-name a')?.textContent || '').trim();
    const d = isFinite(v.duration) ? v.duration : 0;
    await bg('/api/status?ext=1&click=' + (needClick ? 1 : 0) + '&id=' + enc(videoId())
      + '&playing=' + (!v.paused && !v.ended ? 1 : 0)
      + '&pos=' + (ad ? 0 : Math.round(v.currentTime * 1000)) + '&dur=' + (ad ? 0 : Math.round(d * 1000))
      + '&title=' + enc(title) + '&author=' + enc(author) + '&ad=' + (ad ? 1 : 0));
    say(ad ? 'Ömer Client ✔ – Werbung: im Player „Überspringen“ klicken' : needClick ? 'Ömer Client: einmal ins Video klicken, dann läuft es' : 'Ömer Client verbunden ✔');
  }

  setInterval(() => { tick().catch(() => {}); }, 700);
  tick().catch(() => {});
})();

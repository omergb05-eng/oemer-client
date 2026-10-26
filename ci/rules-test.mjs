// Prüft die Datenbank-Regeln gegen den Firebase-Emulator (Auth :9099, Datenbank :9000).
const AUTH = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:';
const DB = 'http://127.0.0.1:9000';
const NS = 'demo-oemer-default-rtdb';
let fails = 0;

async function signUp(email) {
	const r = await fetch(AUTH + 'signUp?key=fake', { method: 'POST', headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ email, password: 'geheim123', returnSecureToken: true }) });
	const o = await r.json();
	return { uid: o.localId, token: o.idToken };
}
async function db(u, method, path, body, query = '') {
	const r = await fetch(`${DB}/${path}.json?ns=${NS}&auth=${u.token}${query}`, { method, body: body === undefined ? undefined : JSON.stringify(body) });
	const t = await r.text();
	return { ok: r.status < 300, status: r.status, data: t ? JSON.parse(t) : null };
}
function check(name, cond) {
	console.log(`[RULES-TEST] ${cond ? 'OK' : 'FAIL'} ${name}`);
	if (!cond) fails++;
}

const a = await signUp('a@test.de'), b = await signUp('b@test.de'), c = await signUp('c@test.de');
check('Konten angelegt', a.uid && b.uid && c.uid);

check('Name belegen', (await db(a, 'PUT', 'usernames/anna', a.uid)).ok);
check('Name doppelt abgelehnt', !(await db(b, 'PUT', 'usernames/anna', b.uid)).ok);
check('Name mit Grossbuchstaben abgelehnt', !(await db(b, 'PUT', 'usernames/Ben', b.uid)).ok);
check('Name fuer andere uid abgelehnt', !(await db(b, 'PUT', 'usernames/ben', a.uid)).ok);
check('Name ben belegen', (await db(b, 'PUT', 'usernames/ben', b.uid)).ok);
check('Name nachschlagen', (await db(c, 'GET', 'usernames/anna')).data === a.uid);
check('Namensliste nicht lesbar', !(await db(c, 'GET', 'usernames')).ok);
check('Profile anlegen', (await db(a, 'PUT', 'profiles/' + a.uid, { name: 'anna', mc: 'x', seen: Date.now() })).ok && (await db(b, 'PUT', 'profiles/' + b.uid, { name: 'ben', mc: 'y', seen: 1 })).ok);
check('Fremdes Profil nicht schreibbar', !(await db(c, 'PUT', 'profiles/' + a.uid, { name: 'evil' })).ok);

await db(c, 'PUT', 'profiles/' + c.uid, { name: 'cora', mc: 'z', seen: 1 });
await db(a, 'PUT', 'skins/' + a.uid, { cape: 'AAAA', totem: 'BBBB' });
await db(a, 'PUT', 'listening/' + a.uid, { id: 'abc', title: 'Lied', artist: 'X', pos: 1000, at: Date.now(), playing: true, service: 'YouTube' });

check('Nicht-Freund sieht Skins nicht', !(await db(b, 'GET', 'skins/' + a.uid)).ok);
check('Nicht-Freund sieht Musik nicht', !(await db(b, 'GET', 'listening/' + a.uid)).ok);

// Anfrage von A an B
check('Anfrage senden', (await db(a, 'PUT', `requests/${b.uid}/${a.uid}`, { name: 'anna', ts: Date.now() })).ok);
check('Anfrage im Namen eines anderen abgelehnt', !(await db(c, 'PUT', `requests/${b.uid}/${a.uid}`, { name: 'x', ts: 1 })).ok);
check('Empfaenger liest Anfragen', (await db(b, 'GET', 'requests/' + b.uid)).data?.[a.uid]?.name === 'anna');
check('Fremde lesen Anfragen nicht', !(await db(c, 'GET', 'requests/' + b.uid)).ok);
check('Freundschaft ohne Anfrage abgelehnt', !(await db(c, 'PUT', `friends/${c.uid}/${a.uid}`, true)).ok);
check('Freundschaft auf eigene Faust (A) abgelehnt', !(await db(a, 'PUT', `friends/${a.uid}/${b.uid}`, true)).ok);

// B nimmt an (alles auf einmal)
const acc = await db(b, 'PATCH', '', { [`friends/${b.uid}/${a.uid}`]: true, [`friends/${a.uid}/${b.uid}`]: true, [`requests/${b.uid}/${a.uid}`]: null });
check('Annehmen (Mehrfach-Schreiben)', acc.ok);
check('A sieht B als Freund', (await db(a, 'GET', 'friends/' + a.uid)).data?.[b.uid] === true);
check('B sieht A als Freund', (await db(b, 'GET', 'friends/' + b.uid)).data?.[a.uid] === true);
check('Anfrage danach weg', (await db(b, 'GET', 'requests/' + b.uid)).data === null);

check('Freund sieht Skins', (await db(b, 'GET', 'skins/' + a.uid)).data?.cape === 'AAAA');
check('Freund sieht Musik', (await db(b, 'GET', 'listening/' + a.uid)).data?.id === 'abc');
check('Dritte sehen Skins nicht', !(await db(c, 'GET', 'skins/' + a.uid)).ok);

// Musik pro Freund aus
check('Musik fuer Ben ausschalten', (await db(a, 'PUT', `sharing/${a.uid}/${b.uid}`, false)).ok);
check('Ben sieht Musik nicht mehr', !(await db(b, 'GET', 'listening/' + a.uid)).ok);
check('Ben sieht Skins weiterhin', (await db(b, 'GET', 'skins/' + a.uid)).ok);
check('Ben kann Sharing nicht umstellen', !(await db(b, 'PUT', `sharing/${a.uid}/${b.uid}`, true)).ok);
check('Musik wieder an', (await db(a, 'PUT', `sharing/${a.uid}/${b.uid}`, true)).ok && (await db(b, 'GET', 'listening/' + a.uid)).ok);

// Namensstil + Logo
check('Namensstil speichern', (await db(a, 'PATCH', 'skins/' + a.uid, { name: '{"t":"","m":1,"a":2,"b":7,"f":true}', logo: 'iVBORw0KGgo=' })).ok);
check('Freund liest Namensstil', (await db(b, 'GET', 'skins/' + a.uid)).data?.name?.includes('"m":1'));
check('Zu langer Name abgelehnt', !(await db(a, 'PATCH', 'skins/' + a.uid, { name: 'x'.repeat(201) })).ok);
check('Zu grosses Logo abgelehnt', !(await db(a, 'PATCH', 'skins/' + a.uid, { logo: 'x'.repeat(9001) })).ok);
check('Dritte sehen Namensstil nicht', !(await db(c, 'GET', 'skins/' + a.uid)).ok);

// Animierte Cape
check('Animierte Cape speichern', (await db(a, 'PATCH', 'skins/' + a.uid, { capeFrames: 8, capeMs: 100 })).ok);
check('Freund liest Animation', (await db(b, 'GET', 'skins/' + a.uid)).data?.capeFrames === 8);
check('Zu viele Bilder abgelehnt', !(await db(a, 'PATCH', 'skins/' + a.uid, { capeFrames: 99 })).ok);
check('Zu schnelle Animation abgelehnt', !(await db(a, 'PATCH', 'skins/' + a.uid, { capeMs: 1 })).ok);

// Mithoeren nur mit Erlaubnis
check('Ben darf sich nicht selbst erlauben', !(await db(b, 'PUT', `listenAllow/${a.uid}/${b.uid}`, true)).ok);
check('Anna erlaubt Ben', (await db(a, 'PUT', `listenAllow/${a.uid}/${b.uid}`, true)).ok);
check('Ben sieht seine Erlaubnis', (await db(b, 'GET', `listenAllow/${a.uid}/${b.uid}`)).data === true);
check('Cora sieht die Erlaubnis nicht', !(await db(c, 'GET', `listenAllow/${a.uid}/${b.uid}`)).ok);
check('Ben sieht die Liste nicht', !(await db(b, 'GET', `listenAllow/${a.uid}`)).ok);
check('Anna darf Nicht-Freunden nichts erlauben', !(await db(a, 'PUT', `listenAllow/${a.uid}/${c.uid}`, true)).ok);
check('Anna widerruft', (await db(a, 'PUT', `listenAllow/${a.uid}/${b.uid}`, false)).ok && (await db(b, 'GET', `listenAllow/${a.uid}/${b.uid}`)).data === false);

// Chat
const t = Date.now();
const send = await db(a, 'PATCH', '', {
	[`messages/${a.uid}/${b.uid}/m1`]: { from: a.uid, text: 'Hallo Ben', ts: t },
	[`messages/${b.uid}/${a.uid}/m1`]: { from: a.uid, text: 'Hallo Ben', ts: t },
	[`inbox/${b.uid}/${a.uid}`]: t,
});
check('Nachricht senden', send.ok);
check('Ben sieht Posteingang', (await db(b, 'GET', 'inbox/' + b.uid)).data?.[a.uid] === t);
const hist = await db(b, 'GET', `messages/${b.uid}/${a.uid}`, undefined, '&orderBy=%22ts%22&limitToLast=10');
check('Ben liest Verlauf', hist.ok && hist.data?.m1?.text === 'Hallo Ben');
check('Fremde lesen Chat nicht', !(await db(c, 'GET', `messages/${b.uid}/${a.uid}`)).ok);
check('Nachricht mit falschem Absender abgelehnt', !(await db(a, 'PATCH', '', {
	[`messages/${a.uid}/${b.uid}/m2`]: { from: b.uid, text: 'fake', ts: t },
	[`messages/${b.uid}/${a.uid}/m2`]: { from: b.uid, text: 'fake', ts: t } })).ok);
check('Nachricht an Nicht-Freund abgelehnt', !(await db(c, 'PATCH', '', {
	[`messages/${c.uid}/${a.uid}/m3`]: { from: c.uid, text: 'hi', ts: t },
	[`messages/${a.uid}/${c.uid}/m3`]: { from: c.uid, text: 'hi', ts: t } })).ok);
check('Zu lange Nachricht abgelehnt', !(await db(a, 'PUT', `messages/${a.uid}/${b.uid}/m4`, { from: a.uid, text: 'x'.repeat(301), ts: t })).ok);

// Entfernen
check('Freund entfernen', (await db(b, 'PATCH', '', { [`friends/${b.uid}/${a.uid}`]: null, [`friends/${a.uid}/${b.uid}`]: null })).ok);
check('Danach keine Skins mehr', !(await db(b, 'GET', 'skins/' + a.uid)).ok);

console.log(fails === 0 ? '[RULES-TEST] ALLES OK' : `[RULES-TEST] ${fails} FEHLER`);
process.exit(fails === 0 ? 0 : 1);

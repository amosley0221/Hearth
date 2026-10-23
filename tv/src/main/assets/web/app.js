// Hearth web remote: iPad (and any browser). Served by the TV launcher; talks to it over WebSocket.
'use strict';

const S = {
  token: localStorage.getItem('hearthToken') || '',
  ws: null, connected: false, paired: false, pairPending: false, pairError: '',
  st: null, tab: localStorage.getItem('hearthTab') || 'remote', nowTab: 'stats',
  cfg: null, cfgDirty: false, meta: { apps: [], inputs: [], entities: [], leagues: [], teams: {} },
  teamLeague: 'nfl', reconnectMs: 1000,
};

// ---------- tiny DOM helper ----------
function h(tag, attrs, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
    else if (k.startsWith('on')) el.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k === 'value') el.value = v;
    else if (k === 'checked') el.checked = !!v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const kid of kids.flat(Infinity)) {
    if (kid == null || kid === false) continue;
    el.append(kid instanceof Node ? kid : document.createTextNode(String(kid)));
  }
  return el;
}
const $ = sel => document.querySelector(sel);
const api = (path, opts = {}) => fetch(path + (path.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(S.token), opts);

function toast(text) {
  const t = $('#toast'); t.textContent = text; t.classList.add('show');
  clearTimeout(S.toastT); S.toastT = setTimeout(() => t.classList.remove('show'), 1800);
}

// ---------- connection ----------
function connect() {
  const ws = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws');
  S.ws = ws;
  ws.onopen = () => {
    S.connected = true; S.reconnectMs = 1000;
    if (S.token) send({ t: 'hello', token: S.token, name: deviceName() });
    else render();
  };
  ws.onclose = () => {
    S.connected = false; render();
    setTimeout(connect, S.reconnectMs); S.reconnectMs = Math.min(S.reconnectMs * 2, 10000);
  };
  ws.onmessage = e => { try { onMessage(JSON.parse(e.data)); } catch (err) { console.error(err); } };
}
function send(m) { if (S.ws && S.ws.readyState === 1) S.ws.send(JSON.stringify(m)); }
function deviceName() { return /iPad|Macintosh/.test(navigator.userAgent) ? 'iPad' : /iPhone/.test(navigator.userAgent) ? 'iPhone' : 'Browser'; }

function onMessage(m) {
  switch (m.t) {
    case 'welcome': S.paired = true; break;
    case 'auth_required': S.paired = false; S.token = ''; localStorage.removeItem('hearthToken'); render(); break;
    case 'pair_pending': S.pairPending = true; S.pairError = ''; render(); setTimeout(() => { const c = $('.cells input'); c && c.focus(); }, 50); break;
    case 'paired': S.token = m.token; localStorage.setItem('hearthToken', m.token); S.paired = true; S.pairPending = false; toast('Paired with ' + m.tvName); break;
    case 'pair_failed': S.pairError = m.reason || 'Try again'; render(); break;
    case 'state': S.st = m.state; S.paired = true; scheduleRender(); break;
    case 'toast': toast(m.text); break;
  }
}

let rafPending = false;
function scheduleRender() { if (!rafPending) { rafPending = true; requestAnimationFrame(() => { rafPending = false; render(true); }); } }

// ---------- render ----------
function render(fromState) {
  const app = $('#app');
  if (!S.connected && !S.st) { app.replaceChildren(h('div', { class: 'center' }, h('div', { class: 'pair' }, h('div', { class: 'title' }, 'Connecting to your TV…'), h('div', { class: 'meta' }, location.host)))); return; }
  if (!S.paired) { app.replaceChildren(pairView()); return; }
  const st = S.st || {};
  document.documentElement.style.setProperty('--hue', Math.round(st.backdropHue || 35));
  // Don't rebuild under the user's fingers while they type or edit settings.
  const typing = document.activeElement && /INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName);
  if (fromState && (S.tab === 'settings' || typing) && $('.layout')) { const l = $('.left'); l && l.replaceWith(leftColumn(st)); return; }
  app.replaceChildren(h('div', { class: 'layout' }, rail(), leftColumn(st), h('div', { class: 'right' }, rightColumn(st))));
}

function rail() {
  const tabs = [['remote', 'Remote'], ['now', 'Now'], ['apps', 'Apps'], ['home', 'Home'], ['settings', 'Settings']];
  return h('div', { class: 'rail' },
    tabs.map(([id, label]) => h('button', { class: 'tab' + (S.tab === id ? ' sel' : ''), onclick: () => setTab(id) }, h('div', { class: 'ic' }), label)),
    h('div', { class: 'spacer' }),
    h('button', { class: 'avatar', onclick: () => send({ t: 'profile', id: (S.st && S.st.profile === 'kids') ? 'adult' : 'kids' }) },
      (S.st && S.st.profile === 'kids') ? 'K' : 'A'));
}
function setTab(id) { S.tab = id; localStorage.setItem('hearthTab', id); if (id === 'settings') loadSettings(); render(); }

function leftColumn(st) {
  const np = st.nowPlaying;
  const title = np && np.title ? np.title : (st.foregroundLabel || 'Home');
  const sub = np && np.title ? [...new Set([np.appLabel, np.subtitle, remaining(np)].filter(Boolean))].join(' · ') : (st.foregroundPkg ? 'On screen' : '');
  const favs = (st.apps || []).filter(a => a.favorite).slice(0, 12);
  return h('div', { class: 'left' },
    h('div', { class: 'eyebrow' }, h('span', { class: 'dot' + (S.connected ? '' : ' amber') }), st.tvName || 'TV'),
    !st.inputControl && h('div', { class: 'banner' }, 'Basic remote: keys only reach Hearth. Finish setup on the TV: Settings › Remote control.'),
    h('div', null, h('div', { class: 'title' }, title), h('div', { class: 'meta' }, sub)),
    h('div', { class: 'mirror' },
      np && np.artUrl ? h('img', { src: np.artUrl }) : h('div', { class: 'lbl' }, 'TV mirror · what\'s on screen'),
      np && np.durationMs ? h('div', { class: 'bar' }, h('i', { style: { width: progress(np) * 100 + '%' } })) : null),
    h('div', { class: 'modes' }, (st.modes || []).slice(0, 4).map(m =>
      h('button', { class: 'btn' + (m.active ? ' on' : ''), onclick: () => send({ t: 'mode', id: m.id }) }, m.name))),
    h('div', { class: 'favs-wrap' },
      h('div', { class: 'section-label', style: { margin: '6px 0 10px' } }, 'Favorites'),
      h('div', { class: 'favs' }, favs.map(appTile))),
    h('div', { class: 'consoles' }, (st.consoles || []).slice(0, 2).map(c =>
      h('button', { class: 'chip', onclick: () => { send({ t: 'console', id: c.id }); toast('Waking ' + c.name + '…'); } },
        h('b', null, h('span', { class: 'dot ' + (c.status === 'on' ? '' : c.status === 'standby' ? 'amber' : 'grey') }), c.name),
        h('small', null, [cap(c.status === 'unknown' ? '' : c.status), c.inputLabel].filter(Boolean).join(' · ') || 'Tap to wake')))));
}

function appTile(a) {
  return h('button', { class: 'app', onclick: () => { send({ t: 'launch', pkg: a.pkg }); toast('Opening ' + a.label); } },
    h('span', null, a.label), h('img', { src: '/api/icon/' + a.pkg, alt: a.label, onerror: e => e.target.remove() }));
}

function rightColumn(st) {
  switch (S.tab) {
    case 'now': return nowTab(st);
    case 'apps': return appsTab(st);
    case 'home': return homeTab(st);
    case 'settings': return settingsTab();
    default: return remoteTab(st);
  }
}

// ---------- Remote ----------
function holdButton(attrs, key, ...kids) {
  let timer, rep;
  const stop = () => { clearTimeout(timer); clearInterval(rep); };
  return h('button', Object.assign({}, attrs, {
    onpointerdown: e => { e.preventDefault(); send({ t: 'key', key }); flash(e.currentTarget); timer = setTimeout(() => rep = setInterval(() => send({ t: 'key', key }), 110), 420); },
    onpointerup: stop, onpointerleave: stop, onpointercancel: stop,
  }), ...kids);
}
function flash(el) { el.classList.add('flash'); setTimeout(() => el.classList.remove('flash'), 150); }
function keyBtn(label, key, cls = 'btn') { return h('button', { class: cls, onclick: e => { send({ t: 'key', key }); flash(e.currentTarget); } }, label); }

function remoteTab(st) {
  const vol = st.maxVolume ? Math.round((st.volume / st.maxVolume) * 100) : 0;
  return [
    h('div', { class: 'remote-top' },
      h('div', { class: 'panel pad-panel' },
        h('div', { class: 'dpad' },
          holdButton({ class: 'arr up', 'aria-label': 'Up' }, 'up'), holdButton({ class: 'arr down', 'aria-label': 'Down' }, 'down'),
          holdButton({ class: 'arr left', 'aria-label': 'Left' }, 'left'), holdButton({ class: 'arr right', 'aria-label': 'Right' }, 'right'),
          h('button', { class: 'ok', onclick: e => { send({ t: 'key', key: 'ok' }); flash(e.currentTarget); } }, 'OK'))),
      h('div', { class: 'panel vol' },
        holdButton({}, 'vol_up', '+'),
        h('div', { class: 'track' }, h('i', { style: { height: vol + '%' } })),
        h('div', { class: 'lvl' }, String(st.volume ?? '')),
        holdButton({}, 'vol_down', '−'),
        h('button', { class: 'mute' + (st.muted ? ' on' : ''), onclick: () => send({ t: 'key', key: 'mute' }) }, '◀'))),
    h('div', { class: 'row5' },
      keyBtn('Back', 'back'), keyBtn('Home', 'home'), keyBtn('Search', 'search'),
      h('button', { class: 'btn', onclick: () => setTab('apps') }, 'Keyboard'), keyBtn('Inputs', 'inputs')),
    h('div', { class: 'row4' },
      keyBtn('−10s', 'skip_back'), h('button', { class: 'btn paper', onclick: () => send({ t: 'key', key: 'play_pause' }) }, st.nowPlaying && st.nowPlaying.playing ? 'Pause' : 'Play'),
      keyBtn('+30s', 'skip_forward'), keyBtn('Power', 'power')),
  ];
}

// ---------- Now (second screen) ----------
function nowTab(st) {
  const g = st.liveGame;
  if (st.secondScreen === 'sports' && g) return [scoreCard(g), statsPanel(st, g), compactStrip()];
  const np = st.nowPlaying;
  if (np && np.title) {
    return [h('div', { class: 'panel', style: { padding: '24px', display: 'flex', gap: '24px', alignItems: 'center', flexWrap: 'wrap' } },
      np.artUrl ? h('img', { class: 'np-art', src: np.artUrl, style: { maxWidth: '300px' } }) : h('div', { class: 'np-art', style: { maxWidth: '300px' } }),
      h('div', { style: { flex: 1, minWidth: '240px', display: 'flex', flexDirection: 'column', gap: '12px' } },
        h('div', { class: 'eyebrow' }, 'Now playing · ' + (np.appLabel || '')),
        h('div', { style: { fontFamily: 'Newsreader, serif', fontWeight: 300, fontSize: '44px', lineHeight: 1.05 } }, np.title),
        h('div', { class: 'meta' }, np.subtitle || ''),
        np.durationMs ? h('div', null, h('div', { class: 'scrub' }, h('i', { style: { width: progress(np) * 100 + '%' } })),
          h('div', { class: 'meta', style: { display: 'flex', justifyContent: 'space-between', marginTop: '6px', fontSize: '13px' } },
            h('span', null, fmt(position(np))), h('span', null, '−' + fmt(np.durationMs - position(np))))) : null,
        h('div', { class: 'row4', style: { gridTemplateColumns: '1fr 1.4fr 1fr' } },
          keyBtn('−10s', 'skip_back'), h('button', { class: 'btn paper', onclick: () => send({ t: 'key', key: 'play_pause' }) }, np.playing ? 'Pause' : 'Play'), keyBtn('+30s', 'skip_forward')))),
      compactStrip()];
  }
  const live = st.liveGames || [];
  return [h('div', { class: 'h1' }, live.length ? 'Live now' : 'Nothing playing'),
    live.length ? h('div', { class: 'meta' }, 'Pick a game to open it on the TV') : h('div', { class: 'meta' }, 'Start something on the TV and it shows up here.'),
    live.map(g => h('button', { class: 'panel chip', style: { padding: '16px 20px' }, onclick: () => send({ t: 'section', id: 'sports' }) },
      h('b', null, `${g.away.abbr} ${g.away.score} · ${g.home.abbr} ${g.home.score}`), h('small', null, `${g.leagueName} · ${g.detail} · ${g.network || ''}`))),
    compactStrip()];
}
function teamDot(t) {
  return h('div', { class: 'tdot', style: { background: t.color || '#555' } }, t.logo ? h('img', { src: t.logo, alt: t.abbr }) : t.abbr);
}
function scoreCard(g) {
  return h('div', { class: 'panel score' },
    h('div', { class: 'eyebrow' }, h('span', { class: 'dot red' }), `Live · ${g.leagueName}${g.network ? ' / ' + g.network : ''}`),
    h('div', { class: 'teams' },
      h('div', { class: 'team' }, teamDot(g.away), g.away.name),
      h('div', { style: { textAlign: 'center' } }, h('div', { class: 'num' }, g.away.score, h('span', { class: 'meta', style: { fontSize: '20px', margin: '0 14px' } }, 'vs'), g.home.score), h('div', { class: 'meta' }, g.detail)),
      h('div', { class: 'team' }, teamDot(g.home), g.home.name)),
    (g.situation || g.highlight) && h('div', { class: 'foot' }, g.situation, g.highlight ? h('span', null, ' • ', h('span', { class: 'amber' }, g.highlight)) : null));
}
function statsPanel(st, g) {
  const tabs = [['stats', 'Stats'], ['plays', 'Plays'], ['other', 'Other games']];
  let body;
  if (S.nowTab === 'plays') body = h('div', { style: { fontSize: '15px', lineHeight: 1.5 } }, g.lastPlay || 'No play-by-play yet.');
  else if (S.nowTab === 'other') body = (st.liveGames || []).filter(x => x.id !== g.id).map(x =>
    h('div', { class: 'stat' }, h('div', { class: 'l' }, h('span', null, `${x.away.abbr} ${x.away.score} · ${x.home.abbr} ${x.home.score}`), h('span', { class: 'meta' }, x.detail))));
  else body = (g.stats || []).length ? g.stats.map(s => {
    const a = parseFloat(s.away) || 0, b = parseFloat(s.home) || 0, tot = a + b || 1;
    return h('div', { class: 'stat' }, h('div', { class: 'l' }, h('b', null, s.away), h('span', { class: 'meta' }, s.label), h('b', null, s.home)),
      h('div', { class: 'b' }, h('i', { style: { flex: a / tot } }), h('i', { style: { flex: b / tot } })));
  }) : h('div', { class: 'meta' }, 'Stats appear once the game is underway.');
  return h('div', { class: 'panel', style: { padding: '18px 22px', flex: 1 } },
    h('div', { class: 'pills', style: { marginBottom: '10px' } }, tabs.map(([id, l]) => h('button', { class: 'pill' + (S.nowTab === id ? ' sel' : ''), onclick: () => { S.nowTab = id; render(); } }, l))),
    body, g.lastPlay && S.nowTab === 'stats' ? h('div', { class: 'meta', style: { marginTop: '12px', fontSize: '14px' } }, 'Last play: ' + g.lastPlay) : null);
}
function compactStrip() {
  return h('div', { class: 'row5' }, keyBtn('Back', 'back'), keyBtn('‹', 'left'), keyBtn('OK', 'ok'), keyBtn('›', 'right'), keyBtn('Pause', 'play_pause'));
}

// ---------- Apps + keyboard ----------
function appsTab(st) {
  const kb = h('input', { class: 'kbd-input', placeholder: st.keyboardActive ? 'Typing on TV · ' + (st.keyboardHint || 'search') : 'Type here to send text to the TV',
    autocapitalize: 'off', autocomplete: 'off', autocorrect: 'off', spellcheck: 'false' });
  kb.addEventListener('input', () => send({ t: 'text', text: kb.value, mode: 'set' }));
  kb.addEventListener('keydown', e => { if (e.key === 'Enter') { send({ t: 'text', text: '', mode: 'submit' }); kb.blur(); } });
  const search = h('input', { placeholder: 'Find an app', style: { maxWidth: '260px' } });
  const grid = h('div', { class: 'favs', style: { gridTemplateColumns: 'repeat(auto-fill, minmax(84px, 1fr))' } }, (st.apps || []).map(appTile));
  search.addEventListener('input', () => {
    const q = search.value.toLowerCase();
    grid.replaceChildren(...(st.apps || []).filter(a => a.label.toLowerCase().includes(q)).map(appTile));
  });
  return [h('div', { class: 'panel', style: { padding: '16px' } }, h('div', { class: 'section-label', style: { marginBottom: '8px' } }, 'Keyboard'), kb,
      h('div', { style: { display: 'flex', gap: '8px', marginTop: '10px' } },
        h('button', { class: 'pill', onclick: () => send({ t: 'text', text: '', mode: 'backspace' }) }, '⌫ Delete'),
        h('button', { class: 'pill', onclick: () => { kb.value = ''; send({ t: 'text', text: '', mode: 'set' }); } }, 'Clear'),
        h('button', { class: 'pill sel', onclick: () => send({ t: 'text', text: '', mode: 'submit' }) }, 'Search'))),
    h('div', { style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between' } }, h('div', { class: 'h1' }, 'All apps'), search),
    grid];
}

// ---------- Home (Home Assistant) ----------
function homeTab(st) {
  const hs = st.home || {};
  if (!hs.configured) return [h('div', { class: 'h1' }, 'Home'), h('div', { class: 'panel card' },
    h('div', { class: 't' }, 'Connect Home Assistant'), h('div', { class: 's' }, 'Add your Home Assistant URL and a long-lived token in Settings.'),
    h('button', { class: 'pill sel', style: { marginTop: '12px' }, onclick: () => setTab('settings') }, 'Open Settings'))];
  const dis = hs.connected ? '' : ' disabled';
  const call = (service, entity_id, data) => send({ t: 'ha_call', service, entity_id, data });
  const lightsOn = hs.lightsOn > 0;
  const camImg = hs.cameraEntity ? h('img', { id: 'camimg', src: camUrl(hs.cameraEntity), onerror: e => { e.target.style.visibility = 'hidden'; }, onload: e => { e.target.style.visibility = ''; } }) : null;
  const spk = h('input', { type: 'range', class: 'slider', min: 0, max: 100, value: hs.speakerVolume, style: { '--p': hs.speakerVolume + '%' } });
  spk.addEventListener('change', () => call('media_player.volume_set', hs.speakerEntity, { volume_level: spk.value / 100 }));
  return [
    h('div', { style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between' } }, h('div', { class: 'h1' }, 'Home'),
      h('div', { class: 'meta', style: { display: 'flex', gap: '8px', alignItems: 'center' } }, h('span', { class: 'dot' + (hs.connected ? '' : ' grey') }),
        hs.connected ? 'Home Assistant · connected' : 'Home Assistant offline')),
    h('div', { class: 'pills' }, h('span', { class: 'pill sel' }, hs.room || 'Living Room'), hs.presence && h('span', { class: 'pill' }, hs.presence), hs.outside && h('span', { class: 'pill' }, hs.outside + ' outside')),
    h('div', { class: 'grid2' + dis },
      h('div', { class: 'panel card', style: { minHeight: '170px' } }, h('div', { class: 't' }, 'Lights'),
        h('div', { class: 's' }, `${hs.lightsOn} on${hs.lightsBrightness ? ' · ' + hs.lightsBrightness + '%' : ''}`),
        h('div', { class: 'orb' + (lightsOn ? '' : ' off') }),
        h('button', { style: { position: 'absolute', left: '20px', bottom: '18px' }, onclick: () => call(lightsOn ? 'light.turn_off' : 'light.turn_on') },
          h('span', { class: 'toggle' + (lightsOn ? ' on' : '') }))),
      h('div', { class: 'panel card', style: { minHeight: '170px' } }, h('div', { class: 't' }, 'Thermostat'),
        h('div', { class: 'temp' }, hs.climateCurrent != null ? Math.round(hs.climateCurrent) + '°' : '--'),
        h('div', { class: 's' }, [cap(hs.climateMode), hs.climateTarget != null ? 'set ' + Math.round(hs.climateTarget) + '°' : ''].filter(Boolean).join(' · ')),
        h('div', { style: { position: 'absolute', right: '18px', bottom: '18px', display: 'flex', gap: '8px' } },
          h('button', { class: 'round', onclick: () => call('climate.set_temperature', undefined, { temperature: (hs.climateTarget || 70) - 1 }) }, '−'),
          h('button', { class: 'round', onclick: () => call('climate.set_temperature', undefined, { temperature: (hs.climateTarget || 70) + 1 }) }, '+')))),
    h('div', { class: 'grid2' + dis, style: { gridTemplateColumns: '1.3fr 1fr' } },
      h('div', { class: 'panel card' }, h('div', { style: { display: 'flex', justifyContent: 'space-between' } }, h('div', { class: 't' }, hs.cameraName || 'Camera'),
        hs.cameraEntity && h('span', { class: 'meta' }, h('span', { class: 'dot' }), ' Live')),
        h('div', { class: 'cam' }, camImg)),
      h('div', { style: { display: 'flex', flexDirection: 'column', gap: '12px' } },
        h('div', { class: 'panel card' }, h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' } },
          h('div', null, h('div', { class: 't' }, hs.lockName || 'Lock'), h('div', { class: 's' }, h('span', { class: 'dot' + (hs.locked ? '' : ' red') }), hs.locked == null ? ' Unknown' : hs.locked ? ' Locked' : ' Unlocked')),
          hs.lockEntity && h('button', { onclick: () => call(hs.locked ? 'lock.unlock' : 'lock.lock', hs.lockEntity) }, h('span', { class: 'toggle green' + (hs.locked ? ' on' : '') })))),
        h('div', { class: 'panel card' }, h('div', { class: 't' }, hs.speakerName || 'Speakers'),
          h('div', { class: 's', style: { margin: '6px 0 14px' } }, [hs.speakerArtist, hs.speakerVolume + '%'].filter(Boolean).join(' · ')), hs.speakerEntity && spk))),
    (hs.scenes || []).length ? h('div', { class: 'row4', style: { gridTemplateColumns: `repeat(${Math.min(hs.scenes.length, 4)}, 1fr)` } },
      hs.scenes.map(s => h('button', { class: 'btn', onclick: () => { send({ t: 'ha_call', service: 'toggle', entity_id: s.id }); toast(s.name); } }, s.name))) : null,
  ];
}
function camUrl(entity) { return '/api/camera?token=' + encodeURIComponent(S.token) + '&entity=' + encodeURIComponent(entity) + '&t=' + Date.now(); }
setInterval(() => { const img = $('#camimg'); if (img && S.st && S.st.home) { const n = new Image(); n.onload = () => { img.src = n.src; }; n.src = camUrl(S.st.home.cameraEntity); } }, 1500);

// ---------- Pairing ----------
function pairView() {
  const cells = Array.from({ length: 6 }, (_, i) => h('input', { inputmode: 'numeric', maxlength: 1, 'aria-label': 'Digit ' + (i + 1) }));
  cells.forEach((c, i) => {
    c.addEventListener('input', () => { c.value = c.value.replace(/\D/g, '').slice(-1); if (c.value && i < 5) cells[i + 1].focus(); if (cells.every(x => x.value)) confirm(); });
    c.addEventListener('keydown', e => { if (e.key === 'Backspace' && !c.value && i > 0) cells[i - 1].focus(); });
  });
  const confirm = () => send({ t: 'pair_confirm', code: cells.map(c => c.value).join(''), name: deviceName() });
  return h('div', { class: 'center' }, h('div', { class: 'pair' },
    h('div', { class: 'tvglyph' }, S.pairPending ? '• • • • • •' : 'TV'),
    h('div', { class: 'title' }, 'Connect to your TV'),
    h('div', { class: 'meta' }, S.pairPending ? 'Type the 6-digit code showing on the TV.' : 'Hearth shows a code on the TV the first time. Pairs once, reconnects automatically.'),
    S.pairPending ? h('div', { class: 'cells' }, cells) : null,
    S.pairError ? h('div', { style: { color: '#FF8A8A' } }, S.pairError) : null,
    S.pairPending ? h('button', { class: 'btn paper big', onclick: confirm }, 'Connect')
      : h('button', { class: 'btn paper big', onclick: () => send({ t: 'pair_request', name: deviceName() }) }, 'Show code on TV'),
    h('div', { class: 'meta', style: { fontSize: '13px' } }, 'Tip: in Safari tap Share › Add to Home Screen for a full-screen remote.')));
}

// ---------- Settings (config editor) ----------
async function loadSettings() {
  try {
    const [cfg, apps, inputs, ents, leagues] = await Promise.all([
      api('/api/config').then(r => r.json()), api('/api/apps').then(r => r.json()), api('/api/inputs').then(r => r.json()),
      api('/api/ha/entities').then(r => r.json()).catch(() => []), api('/api/leagues').then(r => r.json())]);
    S.cfg = cfg; S.meta = { apps, inputs, entities: ents, leagues, teams: S.meta.teams || {} }; S.cfgDirty = false;
    if (!S.meta.teams[S.teamLeague]) loadTeams(S.teamLeague);
  } catch (e) { toast('Couldn\'t load settings'); }
  if (S.tab === 'settings') render();
}
async function loadTeams(league) {
  S.meta.teams[league] = await api('/api/teams?league=' + league).then(r => r.json()).catch(() => []);
  if (S.tab === 'settings') rerenderSettings();
}
function rerenderSettings() { const r = $('.right'); if (r) { const y = r.scrollTop; r.replaceChildren(settingsTab()); r.scrollTop = y; } }
async function saveSettings() {
  const r = await api('/api/config', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(S.cfg) });
  if (r.ok) { S.cfgDirty = false; toast('Saved to TV'); setTimeout(loadSettings, 1500); } else toast('Save failed');
}
const getP = (o, p) => p.split('.').reduce((a, k) => a == null ? a : a[k], o);
function setP(o, p, v) { const ks = p.split('.'); const last = ks.pop(); const t = ks.reduce((a, k) => (a[k] ??= {}), o); t[last] = v; S.cfgDirty = true; markDirty(); }
function markDirty() { const b = $('#savebtn'); if (b) b.textContent = 'Save changes'; }
function field(label, input) { return h('div', { class: 'field' }, h('label', null, label), input); }
function text(obj, path, label, extra = {}) {
  const i = h('input', Object.assign({ value: getP(obj, path) ?? '' }, extra));
  i.addEventListener('input', () => setP(obj, path, extra.type === 'number' ? Number(i.value) : i.value));
  return field(label, i);
}
function bool(obj, path, label) {
  const i = h('input', { type: 'checkbox', checked: !!getP(obj, path), style: { width: 'auto' } });
  i.addEventListener('change', () => setP(obj, path, i.checked));
  return field(label, h('div', null, i));
}
function select(obj, path, label, options, numeric) {
  const cur = String(getP(obj, path) ?? '');
  const s = h('select', null, options.map(([v, l]) => h('option', { value: v, selected: String(v) === cur }, l)));
  s.value = cur;
  s.addEventListener('change', () => setP(obj, path, numeric ? Number(s.value) : s.value));
  return field(label, s);
}
function entityDatalist(id, domains) {
  return h('datalist', { id }, S.meta.entities.filter(e => !domains || domains.includes(e.id.split('.')[0])).map(e => h('option', { value: e.id }, e.name)));
}
function entity(obj, path, label, domains) {
  const id = 'dl-' + path.replace(/\W/g, '') + Math.random().toString(36).slice(2, 6);
  return h('div', null, text(obj, path, label, { list: id, placeholder: (domains || ['entity'])[0] + '.…', autocapitalize: 'off' }), entityDatalist(id, domains));
}
function entityList(obj, path, label, domains) {
  const i = h('input', { value: (getP(obj, path) || []).join(', '), placeholder: 'comma separated', autocapitalize: 'off' });
  i.addEventListener('input', () => setP(obj, path, i.value.split(',').map(s => s.trim()).filter(Boolean)));
  const opts = S.meta.entities.filter(e => domains.includes(e.id.split('.')[0]));
  const picker = h('select', null, h('option', { value: '' }, '+ add…'), opts.map(e => h('option', { value: e.id }, `${e.name} (${e.id})`)));
  picker.addEventListener('change', () => { if (!picker.value) return; const list = getP(obj, path) || []; if (!list.includes(picker.value)) list.push(picker.value); setP(obj, path, list); i.value = list.join(', '); picker.value = ''; });
  return field(label, h('div', { style: { display: 'grid', gap: '6px' } }, i, picker));
}
function haCall(obj, path, label) {
  const c = getP(obj, path) || {}; if (!getP(obj, path)) setP(obj, path, c);
  const data = h('textarea', { rows: 1, placeholder: '{"brightness_pct": 20}' }); data.value = c.data ? JSON.stringify(c.data) : '';
  data.addEventListener('input', () => { try { c.data = data.value.trim() ? JSON.parse(data.value) : undefined; data.style.borderColor = ''; S.cfgDirty = true; markDirty(); } catch { data.style.borderColor = '#FF8A8A'; } });
  return h('div', null, h('div', { class: 'section-label', style: { margin: '10px 0 4px' } }, label),
    text(c, 'service', 'Service', { placeholder: 'script.turn_on', autocapitalize: 'off' }),
    entity(c, 'entityId', 'Entity', null), field('Data (JSON)', data));
}
function listEditor(arr, render1, make, title) {
  return h('div', null, arr.map((item, i) => h('div', { class: 'item' },
    h('div', { class: 'tools' },
      h('button', { class: 'small', onclick: () => { if (i > 0) { [arr[i - 1], arr[i]] = [arr[i], arr[i - 1]]; S.cfgDirty = true; rerenderSettings(); } } }, '↑'),
      h('button', { class: 'small', onclick: () => { if (i < arr.length - 1) { [arr[i + 1], arr[i]] = [arr[i], arr[i + 1]]; S.cfgDirty = true; rerenderSettings(); } } }, '↓'),
      h('button', { class: 'small', style: { color: '#FF8A8A' }, onclick: () => { arr.splice(i, 1); S.cfgDirty = true; rerenderSettings(); } }, 'Remove')),
    render1(item, i))),
    h('button', { class: 'pill', onclick: () => { arr.push(make()); S.cfgDirty = true; rerenderSettings(); } }, '+ ' + title));
}

function settingsTab() {
  const c = S.cfg;
  if (!c) return h('div', { class: 'settings' }, h('div', { class: 'h1' }, 'Settings'), h('div', { class: 'meta' }, 'Loading from the TV…'));
  const st = S.st || {};
  const appOpts = [['', '—']].concat(S.meta.apps.map(a => [a.pkg, a.label]));
  const inputOpts = [['', 'No input']].concat(S.meta.inputs.map(i => [i.id, i.label]));
  const consoleOpts = c.consoles.map(x => [x.id, x.name]);
  const modeOpts = [['', '—']].concat(c.modes.map(m => [m.id, m.name]));
  const upd = st.update || {};
  const league = S.teamLeague;
  const teams = S.meta.teams[league] || [];
  const followed = new Set((c.sports.teams || []).map(t => t.league + ':' + t.id));
  return h('div', { class: 'settings' },
    h('div', { class: 'h1' }, 'Settings'),
    h('div', { class: 'meta' }, `${st.tvName || ''} · Hearth ${st.version || ''}`),

    h('h2', null, 'Updates'),
    h('div', { class: 'item' },
      h('div', null, upd.status === 'available' ? `Hearth ${upd.latest} is available for the TV.` : upd.status === 'up_to_date' ? 'The TV is up to date.' :
        upd.status === 'downloading' ? `Downloading… ${upd.progress}%` : upd.status === 'error' ? 'Update error: ' + upd.error : upd.status === 'waiting_for_user' ? 'Confirm the update on the TV.' : 'Not checked yet.'),
      h('div', { style: { display: 'flex', gap: '8px', marginTop: '10px' } },
        h('button', { class: 'pill', onclick: () => send({ t: 'update', action: 'check' }) }, 'Check'),
        upd.status === 'available' && h('button', { class: 'pill sel', onclick: () => { send({ t: 'update', action: 'install' }); toast('Updating the TV…'); } }, 'Update TV now'))),

    h('h2', null, 'General'),
    text(c, 'tvName', 'TV name'), text(c, 'adultName', 'Your name'),
    bool(c, 'replaceStockHome', 'Replace Google TV home'), bool(c, 'askProfileOnWake', 'Ask who\'s watching on wake'),
    bool(c, 'autoUpdate', 'Auto-install updates overnight'), select(c, 'mediaLibraryPkg', 'Media library app', appOpts),

    h('h2', null, 'Dock (favourites)'),
    listEditor(c.favorites, (pkg, i) => select(c.favorites, String(i), 'App', appOpts), () => '', 'Add app'),

    h('h2', null, 'Home Assistant'),
    text(c, 'home.url', 'URL', { placeholder: 'http://homeassistant.local:8123', autocapitalize: 'off' }),
    text(c, 'home.token', 'Long-lived token', { type: 'password', autocapitalize: 'off' }),
    h('div', { class: 'meta', style: { fontSize: '13px' } }, 'Create a token in Home Assistant › your profile › Security › Long-lived access tokens. Save, then reopen Settings to pick entities from lists.'),
    text(c, 'home.room', 'Room name'),
    entityList(c, 'home.lights', 'Lights', ['light', 'switch']),
    entity(c, 'home.climate', 'Thermostat', ['climate']),
    entity(c, 'home.camera', 'Camera', ['camera']),
    entity(c, 'home.lock', 'Lock', ['lock']),
    entity(c, 'home.speaker', 'Speaker', ['media_player']),
    entity(c, 'home.doorbell', 'Doorbell trigger', ['binary_sensor', 'event', 'input_boolean']),
    entity(c, 'home.weather', 'Weather', ['weather']),
    entityList(c, 'home.people', 'People (presence)', ['person', 'device_tracker']),
    entityList(c, 'home.scenes', 'Scenes & scripts', ['scene', 'script']),
    haCall(c, 'home.tvPower', 'Power button (optional HA call to turn the TV off)'),

    h('h2', null, 'Consoles'),
    listEditor(c.consoles, x => h('div', null,
      text(x, 'name', 'Name'), select(x, 'kind', 'Type', [['ps5', 'PS5'], ['xbox', 'Xbox'], ['switch', 'Switch'], ['other', 'Other']]),
      select(x, 'inputId', 'HDMI input', inputOpts), text(x, 'inputLabel', 'Input label', { placeholder: 'HDMI 1' }),
      entity(x, 'stateEntity', 'Status entity', null), haCall(x, 'wake', 'Wake action')),
      () => ({ id: 'console_' + Date.now(), name: 'Console', kind: 'other', inputId: '', inputLabel: '', wake: {}, stateEntity: '' }), 'Add console'),

    h('h2', null, 'Pinned games'),
    listEditor(c.games, g => h('div', null,
      text(g, 'name', 'Game'), select(g, 'consoleId', 'Console', consoleOpts), text(g, 'artUrl', 'Key art URL', { autocapitalize: 'off' }),
      text(g, 'hue', 'Placeholder hue (0–360)', { type: 'number' }), select(g, 'runMode', 'Also run mode', modeOpts),
      haCall(g, 'launch', 'Launch action (Xbox: media_player.play_media; leave blank for PS5/Switch)')),
      () => ({ id: 'game_' + Date.now(), name: 'New game', consoleId: (c.consoles[0] || {}).id || '', launch: {}, hue: Math.floor(Math.random() * 360), artUrl: '', runMode: '' }), 'Add game'),

    h('h2', null, 'Modes'),
    listEditor(c.modes, m => h('div', null,
      text(m, 'name', 'Name'), select(m, 'icon', 'Icon', ['film', 'gamepad', 'trophy', 'star', 'moon', 'spark'].map(x => [x, x])),
      bool(m, 'showOnHome', 'Tile on TV Home'), bool(m, 'showOnRemote', 'Button on remotes'),
      h('div', { class: 'section-label', style: { margin: '10px 0 4px' } }, 'Actions (in order)'),
      listEditor(m.actions, a => actionEditor(a, appOpts, inputOpts, consoleOpts), () => ({ type: 'ha', label: '', ha: { service: 'light.turn_on', entityId: '' }, value: '' }), 'Add action'),
      h('div', { class: 'section-label', style: { margin: '10px 0 4px' } }, 'When the mode ends'),
      listEditor(m.endActions, a => actionEditor(a, appOpts, inputOpts, consoleOpts), () => ({ type: 'ha', label: '', ha: { service: 'light.turn_on', entityId: '' }, value: '' }), 'Add end action')),
      () => ({ id: 'mode_' + Date.now(), name: 'New mode', icon: 'spark', actions: [], endActions: [], showOnHome: true, showOnRemote: true }), 'Add mode'),

    h('h2', null, 'Sports'),
    field('Leagues', h('div', { class: 'checks' }, S.meta.leagues.map(l => {
      const cb = h('input', { type: 'checkbox', checked: c.sports.leagues.includes(l.key) });
      cb.addEventListener('change', () => { c.sports.leagues = cb.checked ? [...new Set([...c.sports.leagues, l.key])] : c.sports.leagues.filter(x => x !== l.key); S.cfgDirty = true; markDirty(); });
      return h('label', null, cb, l.name);
    }))),
    field('Teams in', (() => { const s = h('select', null, S.meta.leagues.map(l => h('option', { value: l.key, selected: l.key === league }, l.name)));
      s.addEventListener('change', () => { S.teamLeague = s.value; if (!S.meta.teams[s.value]) loadTeams(s.value); else rerenderSettings(); }); return s; })()),
    h('div', { class: 'checks', style: { maxHeight: '260px', overflowY: 'auto' } }, teams.length ? teams.map(t => {
      const cb = h('input', { type: 'checkbox', checked: followed.has(t.league + ':' + t.id) });
      cb.addEventListener('change', () => { c.sports.teams = cb.checked ? [...c.sports.teams, t] : c.sports.teams.filter(x => !(x.league === t.league && x.id === t.id)); S.cfgDirty = true; markDirty(); });
      return h('label', null, cb, t.name);
    }) : h('span', { class: 'meta' }, 'Loading teams…')),
    S.meta.leagues.filter(l => c.sports.leagues.includes(l.key)).map(l => {
      c.sports.apps ??= {};
      const s = h('select', null, h('option', { value: '' }, 'Auto (by network)'), S.meta.apps.map(a => h('option', { value: a.pkg, selected: c.sports.apps[l.key] === a.pkg }, a.label)));
      s.addEventListener('change', () => { if (s.value) c.sports.apps[l.key] = s.value; else delete c.sports.apps[l.key]; S.cfgDirty = true; markDirty(); });
      return field('Watch ' + l.name + ' on', s);
    }),
    bool(c, 'sports.heroTakeover', 'Hero takeover'), bool(c, 'sports.pushStatsToPhone', 'Push live stats to remotes'),

    h('h2', null, 'Kids profile'),
    text(c, 'kids.name', 'Name'), bool(c, 'kids.enabled', 'Show Kids profile'),
    select(c, 'kids.dailyLimitMin', 'Daily limit', [[0, 'Off'], [60, '1h'], [90, '1.5h'], [120, '2h'], [150, '2.5h'], [180, '3h']].map(([v, l]) => [String(v), l]), true),
    text(c, 'kids.bedtime', 'Bedtime', { type: 'time' }), text(c, 'kids.wakeTime', 'TV wakes at', { type: 'time' }),
    field('Approved apps', h('div', { class: 'checks' }, S.meta.apps.map(a => {
      const cb = h('input', { type: 'checkbox', checked: c.kids.approvedApps.includes(a.pkg) });
      cb.addEventListener('change', () => { c.kids.approvedApps = cb.checked ? [...c.kids.approvedApps, a.pkg] : c.kids.approvedApps.filter(x => x !== a.pkg); S.cfgDirty = true; markDirty(); });
      return h('label', null, cb, a.label);
    }))),
    h('div', { class: 'meta', style: { fontSize: '13px' } }, 'The parent PIN is set on the TV (Settings › Profiles). Remotes paired to the TV can always switch profiles.'),

    h('h2', null, 'This device'),
    h('div', { style: { display: 'flex', gap: '8px' } },
      h('button', { class: 'pill', onclick: () => { localStorage.removeItem('hearthToken'); location.reload(); } }, 'Unpair this device')),

    h('div', { class: 'savebar' },
      h('button', { id: 'savebtn', class: 'btn paper', style: { padding: '0 28px' }, onclick: saveSettings }, S.cfgDirty ? 'Save changes' : 'Save'),
      h('button', { class: 'btn', style: { padding: '0 22px' }, onclick: loadSettings }, 'Reload')));
}

function actionEditor(a, appOpts, inputOpts, consoleOpts) {
  const types = [['ha', 'Home Assistant'], ['app', 'Open app'], ['input', 'Switch input'], ['console', 'Wake console'], ['volume', 'Set volume'],
    ['profile', 'Switch profile'], ['wait', 'Wait (seconds)'], ['silence', 'Silence pop-ups'], ['section', 'Open Hearth section']];
  const typeSel = select(a, 'type', 'Type', types);
  typeSel.querySelector('select').addEventListener('change', () => { if (a.type === 'ha' && !a.ha) a.ha = { service: '', entityId: '' }; rerenderSettings(); });
  let detail;
  switch (a.type) {
    case 'ha': a.ha ??= { service: '', entityId: '' }; detail = haCall(a, 'ha', 'Service call (leave entity empty for the room lights)'); break;
    case 'app': detail = select(a, 'value', 'App', appOpts); break;
    case 'input': detail = select(a, 'value', 'Input', inputOpts); break;
    case 'console': detail = select(a, 'value', 'Console', consoleOpts); break;
    case 'profile': detail = select(a, 'value', 'Profile', [['adult', 'Adult'], ['kids', 'Kids']]); break;
    case 'silence': detail = select(a, 'value', 'Pop-ups', [['on', 'Silence (doorbell still shows)'], ['off', 'Back on']]); break;
    case 'section': detail = select(a, 'value', 'Section', [['home', 'Home'], ['library', 'Library'], ['sports', 'Sports'], ['smart', 'Home control'], ['games', 'Games']]); break;
    default: detail = text(a, 'value', a.type === 'volume' ? 'Level (0–100)' : 'Seconds', { type: 'number' });
  }
  return h('div', null, typeSel, text(a, 'label', 'Label'), detail);
}

// ---------- utils ----------
function cap(s) { return s ? s.charAt(0).toUpperCase() + s.slice(1) : ''; }
function position(np) { return np.playing ? Math.min(np.durationMs || Infinity, np.positionMs + (Date.now() - (np.sampledAt || Date.now()))) : np.positionMs; }
function progress(np) { return np.durationMs ? Math.max(0, Math.min(1, position(np) / np.durationMs)) : 0; }
function remaining(np) { if (!np.durationMs) return ''; const m = Math.round((np.durationMs - position(np)) / 60000); return m >= 60 ? `${Math.floor(m / 60)}h ${m % 60}m left` : `${m}m left`; }
function fmt(ms) { const s = Math.max(0, Math.floor(ms / 1000)); const hh = Math.floor(s / 3600), mm = Math.floor(s % 3600 / 60), ss = s % 60;
  return (hh ? hh + ':' + String(mm).padStart(2, '0') : mm) + ':' + String(ss).padStart(2, '0'); }
setInterval(() => { if (S.st && S.st.nowPlaying && S.st.nowPlaying.playing && S.tab !== 'settings') scheduleRender(); }, 5000);

if ('serviceWorker' in navigator && window.isSecureContext) navigator.serviceWorker.register('sw.js').catch(() => {});
document.addEventListener('gesturestart', e => e.preventDefault());
if (S.tab === 'settings') loadSettings();
connect();
render();

/**
 * Lymon backend — Google Apps Script
 * النشر: Deploy > New deployment > Web app
 *   Execute as: Me | Who has access: Anyone
 * ثم شغّل الدالة setupAdmin() مرة واحدة، ثم احذف كلمة السر من سطرها.
 */
const SS = SpreadsheetApp.getActiveSpreadsheet();
const P_ = () => PropertiesService.getScriptProperties();

function sheet_(name, headers) {
  let s = SS.getSheetByName(name);
  if (!s) { s = SS.insertSheet(name); s.appendRow(headers); }
  return s;
}
const US = () => sheet_('Users', ['username', 'hash', 'salt', 'verified', 'banned', 'token', 'created']);
const MS = () => sheet_('Messages', ['id', 'from', 'to', 'type', 'body', 'ts']);

function hash_(p, salt) {
  return Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, salt + p)
    .map(b => ('0' + (b & 0xff).toString(16)).slice(-2)).join('');
}
function out_(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}

/** شغّلها مرة واحدة ثم امسح كلمة السر من الكود. الحساب الإداري محفوظ كـ hash فقط، لا داخل التطبيق. */
function setupAdmin() {
  const salt = Utilities.getUuid();
  P_().setProperties({
    ADMIN_USER: 'Lymon',
    ADMIN_SALT: salt,
    ADMIN_HASH: hash_('Mmaymanmm2020', salt)   // <-- احذف كلمة السر بعد التشغيل
  });
}

function users_() {
  const v = US().getDataRange().getValues(); v.shift();
  return v.map((r, i) => ({
    row: i + 2, username: r[0], hash: r[1], salt: r[2],
    verified: r[3] === true, banned: r[4] === true, token: r[5]
  }));
}

function auth_(token) {
  if (!token) throw new Error('unauthorized');
  const p = P_();
  if (token === p.getProperty('ADMIN_TOKEN')) return { username: p.getProperty('ADMIN_USER'), admin: true };
  const u = users_().find(x => x.token === token);
  if (!u) throw new Error('unauthorized');
  if (u.banned) throw new Error('banned');
  return { username: u.username, admin: false };
}
function admin_(r) {
  const a = auth_(r.token);
  if (!a.admin) throw new Error('unauthorized');
  return a;
}
function badgeOf_(u) { return u.verified ? 'blue' : ''; }

const H = {
  register(r) {
    const n = String(r.username || '').trim(), pw = String(r.password || '');
    if (!/^[A-Za-z0-9_]{3,20}$/.test(n)) throw new Error('اسم المستخدم: 3-20 حرفاً إنجليزياً أو أرقاماً');
    if (pw.length < 6) throw new Error('كلمة المرور 6 أحرف على الأقل');
    const adminName = P_().getProperty('ADMIN_USER') || 'Lymon';
    if (n.toLowerCase() === adminName.toLowerCase() ||
        users_().some(u => u.username.toLowerCase() === n.toLowerCase())) throw new Error('الاسم مستخدم');
    const salt = Utilities.getUuid(), token = Utilities.getUuid();
    US().appendRow([n, hash_(pw, salt), salt, false, false, token, Date.now()]);
    return { ok: true, token, username: n, admin: false };
  },

  login(r) {
    const n = String(r.username || '').trim(), pw = String(r.password || '');
    const p = P_();
    if (n === p.getProperty('ADMIN_USER')) {                     // مسار المشرف: مطابقة تامة
      if (hash_(pw, p.getProperty('ADMIN_SALT')) !== p.getProperty('ADMIN_HASH')) throw new Error('بيانات غير صحيحة');
      const t = Utilities.getUuid(); p.setProperty('ADMIN_TOKEN', t);
      return { ok: true, token: t, username: n, admin: true };
    }
    const u = users_().find(x => x.username === n);
    if (!u || hash_(pw, u.salt) !== u.hash) throw new Error('بيانات غير صحيحة');
    if (u.banned) throw new Error('banned');
    const t = Utilities.getUuid(); US().getRange(u.row, 6).setValue(t);
    return { ok: true, token: t, username: n, admin: false };
  },

  users(r) {
    const me = auth_(r.token);
    const list = users_().filter(u => u.username !== me.username && (me.admin || !u.banned))
      .map(u => ({ username: u.username, badge: badgeOf_(u), banned: me.admin ? u.banned : false }));
    if (!me.admin) list.unshift({ username: P_().getProperty('ADMIN_USER'), badge: 'gold', banned: false });
    return { ok: true, users: list };
  },

  send(r) {
    const me = auth_(r.token);
    const to = String(r.to), type = r.type === 'voice' ? 'voice' : 'text';
    const adminName = P_().getProperty('ADMIN_USER');
    const target = users_().find(u => u.username === to);
    if (!(to === adminName || (target && !target.banned))) throw new Error('المستلم غير موجود');
    const id = Utilities.getUuid();
    let body = String(r.body || '');
    if (type === 'text') { if (!body || body.length > 4000) throw new Error('رسالة غير صالحة'); }
    else {
      const it = DriveApp.getFoldersByName('LymonVoice');
      const folder = it.hasNext() ? it.next() : DriveApp.createFolder('LymonVoice');
      body = folder.createFile(Utilities.newBlob(Utilities.base64Decode(body), 'audio/mp4', id + '.m4a')).getId();
    }
    MS().appendRow([id, me.username, to, type, body, Date.now()]);
    return { ok: true, id };
  },

  poll(r) {
    const me = auth_(r.token).username, peer = String(r.peer), since = Number(r.since) || 0;
    const v = MS().getDataRange().getValues(); v.shift();
    const msgs = v.filter(m => m[5] > since &&
        ((m[1] === me && m[2] === peer) || (m[1] === peer && m[2] === me)))
      .map(m => ({ id: m[0], from: m[1], to: m[2], type: m[3], body: m[4], ts: m[5] }));
    return { ok: true, messages: msgs };
  },

  media(r) {
    const me = auth_(r.token).username;
    const v = MS().getDataRange().getValues(); v.shift();
    const m = v.find(x => x[0] === r.id && x[3] === 'voice' && (x[1] === me || x[2] === me));
    if (!m) throw new Error('غير موجود');
    return { ok: true, data: Utilities.base64Encode(DriveApp.getFileById(m[4]).getBlob().getBytes()) };
  },

  ban(r)      { return setFlag_(r, 5, true,  true);  },
  unban(r)    { return setFlag_(r, 5, false, false); },
  verify(r)   { return setFlag_(r, 4, true,  false); },
  unverify(r) { return setFlag_(r, 4, false, false); }
};

function setFlag_(r, col, val, killToken) {
  admin_(r);
  const u = users_().find(x => x.username === r.target);
  if (!u) throw new Error('المستخدم غير موجود');
  US().getRange(u.row, col).setValue(val);
  if (killToken) US().getRange(u.row, 6).setValue('');
  return { ok: true };
}

function doGet() { return out_({ ok: true, app: 'Lymon' }); }

function doPost(e) {
  const lock = LockService.getScriptLock(); lock.waitLock(20000);
  try {
    const r = JSON.parse(e.postData.contents);
    const f = H[r.action];
    if (!f) throw new Error('bad_action');
    return out_(f(r));
  } catch (err) {
    return out_({ ok: false, error: String(err.message || err) });
  } finally { lock.releaseLock(); }
}

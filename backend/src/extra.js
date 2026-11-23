import crypto from 'crypto'; import { verifySsv } from './ssv.js'; import nodemailer from 'nodemailer'; import express from 'express'; import path from 'path'; import { fileURLToPath } from 'url';
const sha = t => crypto.createHash('sha256').update(t).digest('hex');
export default async function extra({ app, db, auth, tx, audit, err, wrap, bcrypt, token, limit }) {
  const { SMTP_URL, MAIL_FROM, PUBLIC_URL, FCM_SERVICE_ACCOUNT_JSON } = process.env;
  const mailer = SMTP_URL ? nodemailer.createTransport(SMTP_URL) : null;
  const mail = async (to, subject, text) => { if (!mailer || !MAIL_FROM || !PUBLIC_URL) throw err(503, 'email not configured'); await mailer.sendMail({ from: MAIL_FROM, to, subject, text }); };
  const issue = async (c, uid, purpose, mins) => { const t = crypto.randomBytes(32).toString('hex');
    await c.query(`INSERT INTO email_tokens(token_hash,user_id,purpose,expires_at) VALUES($1,$2,$3,now()+$4*interval '1 minute')`, [sha(t), uid, purpose, mins]); return t; };
  const consume = (c, t, purpose) => c.query(`UPDATE email_tokens SET used=true WHERE token_hash=$1 AND purpose=$2 AND NOT used AND expires_at>now() RETURNING user_id`, [sha(String(t || '')), purpose]);

  app.post('/auth/send-verification', auth(), wrap(async (q, s) => { const u = (await db.query('SELECT email,email_verified FROM users WHERE id=$1', [q.user.id])).rows[0];
    if (u.email_verified) return s.sendStatus(204); const t = await tx(c => issue(c, q.user.id, 'verify', 60));
    await mail(u.email, 'Verify your HONEST REWARD email', `Open this link to verify: ${PUBLIC_URL}/auth/verify?token=${t}`); s.sendStatus(204); }));
  app.get('/auth/verify', wrap(async (q, s) => { const r = await tx(async c => { const k = await consume(c, q.query.token, 'verify'); if (!k.rows[0]) return false;
    await c.query('UPDATE users SET email_verified=true WHERE id=$1', [k.rows[0].user_id]); return true; });
    s.status(r ? 200 : 400).send(r ? 'Email verified. You can return to the app.' : 'Invalid or expired link.'); }));
  app.post('/auth/forgot', wrap(async (q, s) => { limit('forgot:' + q.ip, 5, 36e5); const u = (await db.query('SELECT id,email FROM users WHERE email=lower($1)', [q.body.email || ''])).rows[0];
    if (u) { const t = await tx(c => issue(c, u.id, 'reset', 30)); await mail(u.email, 'Reset your HONEST REWARD password', `Open this link: ${PUBLIC_URL}/admin-ui/reset.html?token=${t}`).catch(() => {}); }
    s.sendStatus(204); })); // same reply whether or not the account exists
  app.post('/auth/reset', wrap(async (q, s) => { if ((q.body.password || '').length < 8) throw err(400, 'password too short');
    const h = await bcrypt.hash(q.body.password, 12);
    const ok = await tx(async c => { const k = await consume(c, q.body.token, 'reset'); if (!k.rows[0]) return false;
      await c.query('UPDATE users SET pass_hash=$1 WHERE id=$2', [h, k.rows[0].user_id]); await audit(c, k.rows[0].user_id, 'password_reset', {}); return true; });
    if (!ok) throw err(400, 'invalid or expired token'); s.sendStatus(204); }));

  app.get('/me/ledger', auth(), wrap(async (q, s) => s.json((await db.query(`SELECT id,kind,amount,created_at FROM ledger WHERE user_id=$1 AND ($2::text IS NULL OR kind=$2) ORDER BY id DESC LIMIT 100`, [q.user.id, q.query.kind || null])).rows)));
  app.post('/me/fcm', auth(), wrap(async (q, s) => { await db.query('UPDATE users SET fcm_token=$1 WHERE id=$2', [String(q.body.token || '').slice(0, 500), q.user.id]); s.sendStatus(204); }));
  app.get('/notifications', auth(), wrap(async (q, s) => s.json((await db.query('SELECT * FROM notifications WHERE user_id=$1 ORDER BY created_at DESC LIMIT 50', [q.user.id])).rows)));

  let fcm = null; if (FCM_SERVICE_ACCOUNT_JSON) { const a = (await import('firebase-admin')).default; a.initializeApp({ credential: a.credential.cert(JSON.parse(FCM_SERVICE_ACCOUNT_JSON)) }); fcm = a.messaging(); }
  const notifyAdmins = async o => { try { const ad = (await db.query(`SELECT id,fcm_token FROM users WHERE role='admin'`)).rows;
    for (const a of ad) await db.query('INSERT INTO notifications(user_id,title,body) VALUES($1,$2,$3)', [a.id, 'New recharge order', o.id]);
    const tokens = ad.map(a => a.fcm_token).filter(Boolean); if (fcm && tokens.length) await fcm.sendEachForMulticast({ tokens, notification: { title: 'New recharge order', body: o.id }, data: { order_id: o.id } });
  } catch (e) { console.error('notifyAdmins failed', e.message); } };

  const adUnits = () => (process.env.ADMOB_AD_UNIT_IDS || '').split(',').map(x => x.trim().split('/').pop()).filter(Boolean);
  const cfg = async c => Object.fromEntries((await c.query('SELECT * FROM settings')).rows.map(r => [r.key, r.value]));
  app.get('/ads/status', auth(), wrap(async (q, s) => { const st = await cfg(db);
    const n = (await db.query(`SELECT count(*) FROM ledger WHERE user_id=$1 AND kind='ad_reward' AND created_at>=date_trunc('day',now())`, [q.user.id])).rows[0].count;
    const ago = (await db.query(`SELECT EXTRACT(EPOCH FROM (now()-max(created_at))) AS ago FROM ledger WHERE user_id=$1 AND kind='ad_reward'`, [q.user.id])).rows[0].ago;
    const wait = ago == null ? 0 : Math.max(0, Math.ceil((+st.ad_interval_seconds || 0) - +ago));
    s.json({ enabled: st.ads_enabled === 'true' && adUnits().length > 0, today: +n, limit: +st.daily_ad_limit, wait, interval: +st.ad_interval_seconds || 0 }); }));
  // AdMob SSV callback (GET from Google). Always grants exactly 10 points; reward_amount from the callback is ignored.
  app.get('/ads/callback', wrap(async (q, s) => {
    const raw = q.originalUrl.split('?')[1] || ''; let ok = false;
    try { ok = await verifySsv(raw); } catch (e) { console.error('ssv key error:', e.message); }
    if (!ok) { console.warn('ssv rejected (bad signature):', raw.slice(0, 500)); throw err(403, 'bad signature'); }
    const { user_id, transaction_id, ad_unit } = q.query;
    if (!adUnits().includes(String(ad_unit))) { console.warn('ssv ack without credit (other ad unit, e.g. AdMob test ping):', ad_unit); return s.sendStatus(200); } // validly signed by Google, but not our unit: acknowledge, never credit
    if (!/^[0-9a-f-]{36}$/.test(user_id || '') || !/^[0-9a-f]{8,64}$/.test(transaction_id || '')) throw err(400, 'bad params');
    const lastAgo = (await db.query(`SELECT EXTRACT(EPOCH FROM (now()-max(created_at))) AS ago FROM ledger WHERE user_id=$1 AND kind='ad_reward'`, [user_id])).rows[0].ago;
    if (lastAgo != null && +lastAgo < (+(await cfg(db)).ad_interval_seconds || 0)) { console.warn('ssv: too soon after the previous ad, no credit'); return s.sendStatus(200); }
    await tx(async c => { if ((await cfg(c)).ads_enabled !== 'true') throw err(503, 'ads disabled');
      if (!(await c.query('SELECT id FROM users WHERE id=$1 FOR UPDATE', [user_id])).rows[0]) throw err(404, 'unknown user');
      const n = (await c.query(`SELECT count(*) FROM ledger WHERE user_id=$1 AND kind='ad_reward' AND created_at>=date_trunc('day',now())`, [user_id])).rows[0].count;
      if (+n >= +(await cfg(c)).daily_ad_limit) throw err(429, 'daily limit');
      await c.query(`INSERT INTO ledger(user_id,kind,amount,ref) VALUES($1,'ad_reward',10,$2)`, [user_id, 'ad:' + transaction_id]); // UNIQUE ref blocks duplicates
    }).catch(e => { throw e.code === '23505' ? err(409, 'duplicate') : e; });
    s.sendStatus(200); }));
  const A = auth('admin');
  app.get('/admin/stats', A, wrap(async (q, s) => s.json((await db.query(`SELECT
    (SELECT COALESCE(SUM(amount),0) FROM ledger WHERE kind='ad_reward') issued,
    (SELECT COALESCE(SUM(cost),0) FROM orders WHERE status='completed') redeemed,
    (SELECT count(*) FROM orders WHERE status IN('pending','under_review','processing')) open_orders,
    (SELECT count(*) FROM users) users`)).rows[0])));
  app.get('/admin/users', A, wrap(async (q, s) => s.json((await db.query(`SELECT id,email,role,balance,email_verified,created_at FROM users
    WHERE $1='' OR email ILIKE '%'||$1||'%' OR id::text=$1 ORDER BY created_at DESC LIMIT 100`, [String(q.query.q || '')])).rows)));
  app.get('/admin/users/:id/ledger', A, wrap(async (q, s) => s.json((await db.query('SELECT * FROM ledger WHERE user_id=$1 ORDER BY id DESC LIMIT 200', [q.params.id])).rows)));
  app.post('/admin/users/:id/adjust', A, wrap(async (q, s) => { const amt = parseInt(q.body.amount, 10); if (!amt || !q.body.reason) throw err(400, 'amount and reason required');
    await tx(async c => { await c.query('SELECT id FROM users WHERE id=$1 FOR UPDATE', [q.params.id]);
      await c.query(`INSERT INTO ledger(user_id,kind,amount,ref) VALUES($1,'admin_adjust',$2,$3)`, [q.params.id, amt, 'adj:' + crypto.randomUUID()]);
      await audit(c, q.user.id, 'admin_adjust', { user: q.params.id, amt, reason: q.body.reason }); }).catch(e => { throw e.code === '23514' ? err(400, 'would make balance negative') : e; }); s.sendStatus(204); }));
  app.post('/admin/notify', A, wrap(async (q, s) => { await db.query('INSERT INTO notifications(user_id,title,body) VALUES($1,$2,$3)', [q.body.user_id, String(q.body.title || '').slice(0, 120), String(q.body.body || '').slice(0, 500)]); s.sendStatus(204); }));
  app.get('/admin/packages', A, wrap(async (q, s) => s.json((await db.query('SELECT * FROM packages ORDER BY game,amount')).rows)));
  app.post('/admin/packages', A, wrap(async (q, s) => { const { game, amount, price_points } = q.body; if (!['pubg', 'freefire'].includes(game) || !(amount > 0) || !(price_points > 0)) throw err(400, 'invalid package');
    const r = await tx(async c => { const p = (await c.query('INSERT INTO packages(game,amount,price_points) VALUES($1,$2,$3) RETURNING *', [game, amount, price_points])).rows[0]; await audit(c, q.user.id, 'package_add', p); return p; }); s.json(r); }));
  app.delete('/admin/packages/:id', A, wrap(async (q, s) => { await tx(async c => { await c.query('UPDATE packages SET active=false WHERE id=$1', [q.params.id]); await audit(c, q.user.id, 'package_disable', { id: q.params.id }); }); s.sendStatus(204); })); // soft delete keeps order history valid
  app.put('/admin/settings/:key', A, wrap(async (q, s) => { if (!['daily_ad_limit', 'ads_enabled'].includes(q.params.key)) throw err(400, 'unknown setting');
    await tx(async c => { await c.query('UPDATE settings SET value=$1 WHERE key=$2', [String(q.body.value), q.params.key]); await audit(c, q.user.id, 'setting', { key: q.params.key, value: q.body.value }); }); s.sendStatus(204); }));
  app.use('/admin-ui', express.static(path.join(path.dirname(fileURLToPath(import.meta.url)), '../public/admin')));
  return { notifyAdmins };
}

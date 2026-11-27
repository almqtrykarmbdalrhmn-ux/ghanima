import express from 'express'; import pg from 'pg'; import bcrypt from 'bcryptjs'; import jwt from 'jsonwebtoken'; import crypto from 'crypto'; import fs from 'fs'; import extra from './extra.js';
const { DATABASE_URL, JWT_SECRET, ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_CODE } = process.env;
if (!DATABASE_URL || !JWT_SECRET) throw new Error('Set DATABASE_URL and JWT_SECRET');
const adminEmail = (ADMIN_EMAIL || '').trim().toLowerCase();
const db = new pg.Pool({ connectionString: DATABASE_URL, max: 5, idleTimeoutMillis: 30000, connectionTimeoutMillis: 10000, query_timeout: 20000, keepAlive: true });
db.on('error', e => console.error('pool error:', e.message)); // idle connections can be dropped by Neon when it sleeps
const app = express(); app.set('trust proxy', 1); app.use(express.json());
const audit = (c, actor, action, detail) => c.query('INSERT INTO audit_log(actor,action,detail) VALUES($1,$2,$3)', [actor, action, detail]);
const err = (status, message) => Object.assign(new Error(message), { status });
const wrap = f => (q, s) => f(q, s).catch(e => { if (!e.status) console.error('request error:', q.method, q.path, e.message); s.status(e.status || 500).json({ error: e.status ? e.message : 'server error' }); });
// role 'admin' also requires the second step (mfa) to have been completed; a "pre" token (after password only) is never accepted here
const auth = (role) => (q, s, n) => { try { q.user = jwt.verify((q.headers.authorization || '').slice(7), JWT_SECRET);
  if (q.user.pre) return s.sendStatus(401);
  if (role && (q.user.role !== role || !q.user.mfa)) return s.sendStatus(403); n(); } catch { s.sendStatus(401); } };
const tx = async fn => { const c = await db.connect(); try { await c.query('BEGIN'); const r = await fn(c); await c.query('COMMIT'); return r; }
  catch (e) { await c.query('ROLLBACK'); throw e; } finally { c.release(); } };
const token = (u, mfa) => jwt.sign({ id: u.id, role: u.role, mfa: !!mfa }, JWT_SECRET, { expiresIn: u.role === 'admin' ? '12h' : '30d' });
const sha = t => crypto.createHash('sha256').update(String(t)).digest();
const safeEq = (a, b) => crypto.timingSafeEqual(sha(a), sha(b));
const hits = new Map(); // in-memory rate limit (single instance); use Redis if you scale out
const limit = (key, max, ms) => { const now = Date.now(), a = (hits.get(key) || []).filter(t => now - t < ms); if (a.length >= max) throw err(429, 'too many attempts, try later'); a.push(now); hits.set(key, a); };

app.post('/auth/register', wrap(async (q, s) => { limit('reg:' + q.ip, 10, 36e5); const email = String(q.body.email || '').trim().toLowerCase(), password = q.body.password || '';
  if (!/^\S+@\S+$/.test(email) || password.length < 8) throw err(400, 'invalid input');
  if (email === adminEmail) throw err(409, 'email taken'); // the admin account can never be registered by anyone
  const r = await db.query('INSERT INTO users(email,pass_hash) VALUES($1,$2) RETURNING id,role', [email, await bcrypt.hash(password, 12)]).catch(() => { throw err(409, 'email taken'); });
  s.json({ token: token(r.rows[0]) }); }));
app.post('/auth/login', wrap(async (q, s) => { const email = String(q.body.email || '').trim().toLowerCase(); limit('login:' + q.ip + ':' + email, 10, 9e5);
  const r = await db.query('SELECT * FROM users WHERE email=$1', [email]); const u = r.rows[0];
  if (!u) throw err(404, 'not registered');
  if (!await bcrypt.compare(q.body.password || '', u.pass_hash)) throw err(401, 'bad credentials');
  if (u.role === 'admin') return s.json({ step: 'admin_code', pre: jwt.sign({ id: u.id, pre: true }, JWT_SECRET, { expiresIn: '5m' }) });
  s.json({ token: token(u) }); }));
app.post('/auth/admin-code', wrap(async (q, s) => { limit('adm:' + q.ip, 5, 9e5); if (!ADMIN_CODE) throw err(503, 'admin code not configured');
  let p; try { p = jwt.verify(String(q.body.pre || ''), JWT_SECRET); } catch { throw err(401, 'login again'); }
  if (!p.pre || !safeEq(q.body.code || '', ADMIN_CODE)) throw err(401, 'bad code');
  const u = (await db.query('SELECT id,role FROM users WHERE id=$1', [p.id])).rows[0]; if (!u || u.role !== 'admin') throw err(403, 'forbidden');
  await audit(db, u.id, 'admin_login', {}); s.json({ token: token(u, true) }); }));
app.get('/me', auth(), wrap(async (q, s) => { const r = await db.query(
  `SELECT id,email,role,balance,(SELECT COALESCE(SUM(amount),0) FROM ledger WHERE user_id=u.id AND kind='ad_reward') earned,(SELECT COALESCE(SUM(cost),0) FROM orders WHERE user_id=u.id AND status='completed') redeemed FROM users u WHERE id=$1`, [q.user.id]); s.json(r.rows[0]); }));
app.post('/me/password', auth(), wrap(async (q, s) => { limit('pw:' + q.user.id, 5, 9e5); const { current, password } = q.body;
  if ((password || '').length < 8) throw err(400, 'invalid input');
  const u = (await db.query('SELECT pass_hash,role FROM users WHERE id=$1', [q.user.id])).rows[0];
  if (u.role === 'admin') throw err(403, 'admin password is managed by the server');
  if (!await bcrypt.compare(current || '', u.pass_hash)) throw err(401, 'bad credentials');
  await db.query('UPDATE users SET pass_hash=$1 WHERE id=$2', [await bcrypt.hash(password, 12), q.user.id]); s.sendStatus(204); }));
app.get('/packages', wrap(async (q, s) => s.json((await db.query('SELECT * FROM packages WHERE active ORDER BY game,amount')).rows)));

app.post('/orders', auth(), wrap(async (q, s) => { const { package_id, player_id, server } = q.body;
  if (!/^\d{5,15}$/.test(player_id || '')) throw err(400, 'invalid player id');
  const o = await tx(async c => { const p = (await c.query('SELECT * FROM packages WHERE id=$1 AND active', [package_id])).rows[0]; if (!p) throw err(404, 'package unavailable');
    const u = (await c.query('SELECT balance FROM users WHERE id=$1 FOR UPDATE', [q.user.id])).rows[0];
    if (u.balance < p.price_points) throw err(402, 'insufficient points');
    const o = (await c.query('INSERT INTO orders(user_id,package_id,player_id,server,cost) VALUES($1,$2,$3,$4,$5) RETURNING *', [q.user.id, p.id, player_id, server || '', p.price_points])).rows[0];
    await c.query(`INSERT INTO ledger(user_id,kind,amount,ref) VALUES($1,'reserve',$2,$3)`, [q.user.id, -p.price_points, 'order:' + o.id]);
    await audit(c, q.user.id, 'order_created', { order: o.id }); return o; });
  await notifyAdmins(o);
  s.json(o); }));
app.get('/orders', auth(), wrap(async (q, s) => s.json((await db.query('SELECT o.*,p.game,p.amount FROM orders o JOIN packages p ON p.id=o.package_id WHERE o.user_id=$1 ORDER BY o.created_at DESC', [q.user.id])).rows)));

app.get('/admin/orders', auth('admin'), wrap(async (q, s) => s.json((await db.query('SELECT o.*,u.email,p.game,p.amount FROM orders o JOIN users u ON u.id=o.user_id JOIN packages p ON p.id=o.package_id ORDER BY (o.status IN (\'completed\',\'rejected\')), o.created_at DESC')).rows)));
app.post('/admin/orders/:id/status', auth('admin'), wrap(async (q, s) => { const st = q.body.status;
  if (!['completed', 'rejected'].includes(st)) throw err(400, 'bad status');
  await tx(async c => { const o = (await c.query('SELECT * FROM orders WHERE id=$1 FOR UPDATE', [q.params.id])).rows[0]; if (!o) throw err(404, 'not found');
    if (['completed', 'rejected'].includes(o.status)) throw err(409, 'order is final');
    await c.query('UPDATE orders SET status=$1 WHERE id=$2', [st, o.id]);
    if (st === 'rejected') await c.query(`INSERT INTO ledger(user_id,kind,amount,ref) VALUES($1,'release',$2,$3)`, [o.user_id, o.cost, 'release:' + o.id]);
    await c.query('INSERT INTO notifications(user_id,title,body) VALUES($1,$2,$3)', [o.user_id, st === 'completed' ? 'order_completed' : 'order_rejected', o.id]);
    await audit(c, q.user.id, 'order_status', { order: o.id, status: st }); });
  s.sendStatus(204); }));
app.put('/admin/packages/:id', auth('admin'), wrap(async (q, s) => { const { amount, price_points, active } = q.body;
  await tx(async c => { await c.query('UPDATE packages SET amount=$1,price_points=$2,active=$3 WHERE id=$4', [amount, price_points, active, q.params.id]); await audit(c, q.user.id, 'package_edit', { id: q.params.id, ...q.body }); }); s.sendStatus(204); }));

// First start on an empty database: create tables and the initial packages from schema.sql.
if (!(await db.query("SELECT to_regclass('public.users') AS t")).rows[0].t) { await db.query(fs.readFileSync(new URL('../schema.sql', import.meta.url), 'utf8')); console.log('schema applied'); }
await db.query("INSERT INTO settings(key,value) VALUES('ad_interval_seconds','60') ON CONFLICT (key) DO NOTHING");
await db.query("INSERT INTO settings(key,value) VALUES('app_open_enabled','false') ON CONFLICT (key) DO NOTHING");
await db.query("UPDATE settings SET value='60' WHERE key='ad_interval_seconds' AND value='30'");
await db.query("UPDATE settings SET value='200' WHERE key='daily_ad_limit' AND value IN ('20','1000')");
// Admin account comes ONLY from server environment variables (never from app code): ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_CODE
if (adminEmail && ADMIN_PASSWORD) {
  await db.query(`INSERT INTO users(email,pass_hash,role,email_verified) VALUES($1,$2,'admin',true) ON CONFLICT (email) DO UPDATE SET pass_hash=EXCLUDED.pass_hash, role='admin'`, [adminEmail, await bcrypt.hash(ADMIN_PASSWORD, 12)]);
  console.log('admin account ready');
}
const { notifyAdmins } = await extra({ app, db, auth, tx, audit, err, wrap, bcrypt, token, limit });
app.listen(process.env.PORT || 3000);

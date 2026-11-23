import crypto from 'crypto';
// AdMob rewarded server-side verification: ECDSA (SHA-256) over the raw query string that precedes "&signature=".
const KEYS_URL = 'https://www.gstatic.com/admob/reward/verifier-keys.json';
let cache = { at: 0, keys: {} };
export async function loadKeys(force) {
  if (!force && Date.now() - cache.at < 864e5 && Object.keys(cache.keys).length) return cache.keys;
  const r = await fetch(KEYS_URL); if (!r.ok) throw new Error('verifier keys fetch failed');
  cache = { at: Date.now(), keys: Object.fromEntries((await r.json()).keys.map(k => [String(k.keyId), k.pem])) }; return cache.keys;
}
export function splitQuery(raw) {
  const i = raw.indexOf('&signature='); if (i < 0) return null;
  const rest = new URLSearchParams(raw.slice(i + 1)); return { content: raw.slice(0, i), signature: rest.get('signature'), keyId: rest.get('key_id') };
}
export async function verifySsv(raw, getKeys = loadKeys) {
  const p = splitQuery(raw); if (!p || !p.signature || !p.keyId) return false;
  let pem = (await getKeys(false))[p.keyId]; if (!pem) pem = (await getKeys(true))[p.keyId]; if (!pem) return false;
  try { return crypto.verify('sha256', Buffer.from(p.content), pem, Buffer.from(p.signature, 'base64url')); } catch { return false; }
}

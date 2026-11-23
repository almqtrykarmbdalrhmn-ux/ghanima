import test from 'node:test'; import assert from 'node:assert'; import crypto from 'crypto'; import { verifySsv } from '../src/ssv.js';
const { publicKey, privateKey } = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const keys = async () => ({ '111': publicKey.export({ type: 'spki', format: 'pem' }) });
const content = 'ad_network=5&ad_unit=123&reward_amount=1&reward_item=x&timestamp=1&transaction_id=abc&user_id=u1';
const sig = crypto.sign('sha256', Buffer.from(content), privateKey).toString('base64url');
test('valid signature accepted', async () => assert.equal(await verifySsv(`${content}&signature=${sig}&key_id=111`, keys), true));
test('tampered user_id rejected', async () => assert.equal(await verifySsv(`${content.replace('u1', 'u2')}&signature=${sig}&key_id=111`, keys), false));
test('unknown key rejected', async () => assert.equal(await verifySsv(`${content}&signature=${sig}&key_id=999`, keys), false));
test('missing signature rejected', async () => assert.equal(await verifySsv(content, keys), false));

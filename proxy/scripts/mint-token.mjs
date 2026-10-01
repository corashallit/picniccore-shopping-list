#!/usr/bin/env node
// Mints a device token for the Picnic List proxy.
//
//   node scripts/mint-token.mjs sams-phone          # human-readable instructions
//   node scripts/mint-token.mjs sams-phone --json   # {"name","token","hash","entry","kv"} for scripts
//   node scripts/mint-token.mjs --hash < token.txt  # SHA-256 of an existing token (read from stdin)
//
// The token is printed once and never written anywhere: give it to the device, keep only the hash.
// The proxy stores only SHA-256 hashes (KV key or ACCESS_TOKEN_HASHES entry).

import { createHash, randomBytes } from 'node:crypto';
import { pathToFileURL } from 'node:url';

const NAME = /^[A-Za-z0-9._-]{1,48}$/;

export function mintToken() {
  return `pk_${randomBytes(32).toString('base64url')}`; // 256 bits of entropy
}

export function hashToken(token) {
  return createHash('sha256').update(token, 'utf8').digest('hex');
}

async function readStdin() {
  const chunks = [];
  for await (const chunk of process.stdin) chunks.push(chunk);
  return Buffer.concat(chunks).toString('utf8').trim();
}

async function main(argv) {
  const json = argv.includes('--json');
  const args = argv.filter((a) => a !== '--json');

  if (args[0] === '--hash') {
    const token = await readStdin();
    if (!token) throw new Error('pipe the token on stdin, e.g. node scripts/mint-token.mjs --hash < token.txt');
    process.stdout.write(`${hashToken(token)}\n`);
    return;
  }
  if (args[0] === '--help' || args[0] === '-h') {
    process.stdout.write('usage: node scripts/mint-token.mjs <device-name> [--json] | --hash < token.txt\n');
    return;
  }

  const name = args[0] ?? `device-${new Date().toISOString().slice(0, 10)}`;
  if (!NAME.test(name)) throw new Error('device name must be 1-48 characters of A-Z a-z 0-9 . _ -');
  const token = mintToken();
  const hash = hashToken(token);
  const created = new Date().toISOString();
  const kvValue = JSON.stringify({ name, created });
  const entry = `${name}:${hash}`;

  if (json) {
    process.stdout.write(`${JSON.stringify({ name, token, hash, entry, kv: kvValue })}\n`);
    return;
  }
  process.stdout.write(`Device token for "${name}" (shown once; paste it into the app under
Settings > Connection > Access token, then forget it):

  ${token}

SHA-256 hash (the only thing the proxy stores):

  ${hash}

Register it with ONE of:

  KV namespace (recommended; per-device disable/delete without redeploying):
    npx wrangler kv key put --binding TOKENS --remote ${hash} '${kvValue}'

  ACCESS_TOKEN_HASHES secret (comma-separated list; re-enter the whole list each time):
    add  ${entry}  then run: npx wrangler secret put ACCESS_TOKEN_HASHES
`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main(process.argv.slice(2)).catch((err) => {
    process.stderr.write(`error: ${err.message}\n`);
    process.exit(1);
  });
}

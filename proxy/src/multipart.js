// A deliberately strict multipart/form-data parser and serializer (RFC 7578 / RFC 2046).
//
// Why not request.formData()? Two reasons:
//  1. The allow-list must see exactly what OpenAI will see. A lenient parser here and a different one
//     upstream could disagree (bare LF line endings, duplicate or case-variant "model" fields, a "model"
//     part with a filename, preamble tricks) and let a disallowed model through. So this parser rejects
//     anything ambiguous, and the proxy re-serializes the parts it parsed (canonical CRLF framing, no
//     preamble or epilogue) instead of forwarding the original bytes.
//  2. Every other part stays byte-identical, filenames included: the transcription endpoint picks the
//     decoder from the file extension, and re-encoding through FormData would also rewrite part headers.

const encoder = new TextEncoder();
const utf8 = new TextDecoder('utf-8', { fatal: true });
const CRLF = encoder.encode('\r\n');
const CRLFCRLF = encoder.encode('\r\n\r\n');
const HEADER_LINE = /^([!#$%&'*+.^_`|~0-9A-Za-z-]+):[ \t]*(.*?)[ \t]*$/;
const BCHARS = /^[0-9A-Za-z'()+_,\-./:=? ]{0,69}[0-9A-Za-z'()+_,\-./:=?]$/;

export class MultipartError extends Error {
  constructor(message) {
    super(message);
    this.name = 'MultipartError';
  }
}

/**
 * The boundary of a multipart/form-data Content-Type, or null when the type is not multipart/form-data.
 * Throws MultipartError for a multipart/form-data type with a missing or invalid boundary.
 */
export function multipartBoundary(contentType) {
  if (!contentType) return null;
  const { type, params } = parseHeaderValue(contentType, 'Content-Type');
  if (type.toLowerCase() !== 'multipart/form-data') return null;
  const boundary = params.get('boundary');
  if (boundary === undefined) throw new MultipartError('missing boundary in Content-Type');
  if (!BCHARS.test(boundary)) throw new MultipartError('invalid boundary in Content-Type');
  return boundary;
}

/**
 * @typedef {object} Part
 * @property {Uint8Array} headerBytes  raw header block (without the blank line), forwarded verbatim
 * @property {string} name             form field name
 * @property {string|undefined} filename
 * @property {Uint8Array} body
 */

/** @returns {Part[]} */
export function parseMultipart(bytes, boundary) {
  const dash = encoder.encode(`--${boundary}`);
  const delimiter = encoder.encode(`\r\n--${boundary}`);

  // Locate the first boundary. Anything before it is preamble and is dropped.
  let pos;
  if (startsWith(bytes, dash, 0)) {
    pos = dash.length;
  } else {
    const first = indexOfBytes(bytes, delimiter, 0);
    if (first < 0) throw new MultipartError('no boundary found in body');
    pos = first + delimiter.length;
  }

  const parts = [];
  for (;;) {
    if (bytes[pos] === 0x2d && bytes[pos + 1] === 0x2d) break; // "--": close delimiter; epilogue ignored
    if (!(bytes[pos] === 0x0d && bytes[pos + 1] === 0x0a)) {
      throw new MultipartError('boundary must be followed by CRLF');
    }
    const start = pos + 2;
    const next = indexOfBytes(bytes, delimiter, start);
    if (next < 0) throw new MultipartError('missing closing boundary');
    const headersEnd = indexOfBytes(bytes, CRLFCRLF, start);
    if (headersEnd < 0 || headersEnd + CRLFCRLF.length > next) {
      throw new MultipartError('part has no header block');
    }
    const headerBytes = bytes.subarray(start, headersEnd);
    const body = bytes.subarray(headersEnd + CRLFCRLF.length, next);
    parts.push({ headerBytes, body, ...parsePartHeaders(headerBytes, body.length) });
    pos = next + delimiter.length;
    if (parts.length > 1000) throw new MultipartError('too many parts');
  }
  if (parts.length === 0) throw new MultipartError('no parts');
  return parts;
}

/** Canonical multipart body: `--B CRLF headers CRLF CRLF body CRLF` per part, then `--B-- CRLF`. */
export function serializeMultipart(parts, boundary) {
  const dash = encoder.encode(`--${boundary}`);
  const close = encoder.encode(`--${boundary}--\r\n`);
  let length = close.length;
  for (const p of parts) length += dash.length + CRLF.length + p.headerBytes.length + CRLFCRLF.length + p.body.length + CRLF.length;
  const out = new Uint8Array(length);
  let o = 0;
  const put = (chunk) => {
    out.set(chunk, o);
    o += chunk.length;
  };
  for (const p of parts) {
    put(dash);
    put(CRLF);
    put(p.headerBytes);
    put(CRLFCRLF);
    put(p.body);
    put(CRLF);
  }
  put(close);
  return out;
}

/** A plain text field part with canonical headers (used to replace the model field). */
export function textPart(name, value) {
  return {
    headerBytes: encoder.encode(`Content-Disposition: form-data; name="${name}"`),
    name,
    filename: undefined,
    body: encoder.encode(value),
  };
}

export function decodeText(bytes) {
  try {
    return utf8.decode(bytes);
  } catch {
    throw new MultipartError('field value is not valid UTF-8');
  }
}

function parsePartHeaders(headerBytes, bodyLength) {
  let text;
  try {
    text = utf8.decode(headerBytes);
  } catch {
    throw new MultipartError('part headers are not valid UTF-8');
  }
  let disposition;
  let contentLength;
  const seen = new Set();
  for (const line of text.split('\r\n')) {
    if (line.includes('\r') || line.includes('\n')) throw new MultipartError('bare CR or LF in part headers');
    if (line.startsWith(' ') || line.startsWith('\t')) throw new MultipartError('folded part headers are not supported');
    const m = HEADER_LINE.exec(line);
    if (!m) throw new MultipartError('malformed part header');
    const name = m[1].toLowerCase();
    if (seen.has(name)) throw new MultipartError(`duplicate ${name} header in a part`);
    seen.add(name);
    if (name === 'content-disposition') disposition = m[2];
    if (name === 'content-length') contentLength = m[2];
  }
  if (disposition === undefined) throw new MultipartError('part without Content-Disposition');
  if (contentLength !== undefined && (!/^[0-9]{1,12}$/.test(contentLength) || Number(contentLength) !== bodyLength)) {
    throw new MultipartError('part Content-Length does not match its body');
  }
  const { type, params } = parseHeaderValue(disposition, 'Content-Disposition');
  if (type.toLowerCase() !== 'form-data') throw new MultipartError('Content-Disposition must be form-data');
  if (params.has('name*') || params.has('filename*')) {
    throw new MultipartError('extended (RFC 5987) name/filename parameters are not supported');
  }
  const fieldName = params.get('name');
  if (fieldName === undefined || fieldName === '') throw new MultipartError('part without a field name');
  return { name: fieldName, filename: params.get('filename') };
}

/**
 * Parses `type; key=value; key="quoted value"`. Parameter names are case-insensitive. Rejects duplicate
 * parameters and backslash escapes in quoted strings (implementations disagree on them).
 */
function parseHeaderValue(value, what) {
  let i = 0;
  const n = value.length;
  const ws = () => {
    while (i < n && (value[i] === ' ' || value[i] === '\t')) i++;
  };
  const token = () => {
    const s = i;
    while (i < n && /[!#$%&'*+.^_`|~0-9A-Za-z/-]/.test(value[i])) i++;
    return value.slice(s, i);
  };
  ws();
  const type = token();
  if (!type) throw new MultipartError(`malformed ${what}`);
  const params = new Map();
  for (;;) {
    ws();
    if (i >= n) break;
    if (value[i] !== ';') throw new MultipartError(`malformed ${what}`);
    i++;
    ws();
    if (i >= n) break; // tolerate a trailing ";"
    const key = token().toLowerCase();
    if (!key || value[i] !== '=') throw new MultipartError(`malformed ${what} parameter`);
    i++;
    let val;
    if (value[i] === '"') {
      i++;
      const s = i;
      while (i < n && value[i] !== '"') {
        if (value[i] === '\\') throw new MultipartError(`escaped quotes in ${what} are not supported`);
        i++;
      }
      if (i >= n) throw new MultipartError(`unterminated quoted string in ${what}`);
      val = value.slice(s, i);
      i++;
    } else {
      val = token();
      if (!val) throw new MultipartError(`malformed ${what} parameter`);
    }
    if (params.has(key)) throw new MultipartError(`duplicate ${key} parameter in ${what}`);
    params.set(key, val);
  }
  return { type, params };
}

function startsWith(hay, needle, at) {
  if (at + needle.length > hay.length) return false;
  for (let j = 0; j < needle.length; j++) if (hay[at + j] !== needle[j]) return false;
  return true;
}

/** indexOf for a byte sequence; uses the native single-byte search to skip ahead. */
export function indexOfBytes(hay, needle, from) {
  const first = needle[0];
  const last = hay.length - needle.length;
  let i = from;
  while (i <= last) {
    i = hay.indexOf(first, i);
    if (i < 0 || i > last) return -1;
    let j = 1;
    while (j < needle.length && hay[i + j] === needle[j]) j++;
    if (j === needle.length) return i;
    i++;
  }
  return -1;
}

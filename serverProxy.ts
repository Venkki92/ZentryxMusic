import fs from 'fs';
import https from 'https';
import type { IncomingMessage, ServerResponse } from 'http';

export const ALLOWED_NEXTCLOUD_HOST = 'cloud.zentryx.bg';
const MAX_CHUNK_BYTES = 8 * 1024 * 1024; // 8 MiB chunks

function appendProxyLog(line: string): void {
  try {
    fs.appendFileSync('/tmp/proxy.log', `[${new Date().toISOString()}] ${line}\n`);
  } catch {}
}

function resolveMimeType(filePath: string, fallbackType?: string): string {
  const lower = filePath.toLowerCase();
  if (lower.endsWith('.mp3')) return 'audio/mpeg';
  if (lower.endsWith('.flac')) return 'audio/flac';
  if (lower.endsWith('.ogg') || lower.endsWith('.opus')) return 'audio/ogg';
  if (lower.endsWith('.wav')) return 'audio/wav';
  if (lower.endsWith('.m4a') || lower.endsWith('.aac')) return 'audio/mp4';
  return fallbackType || 'audio/mpeg';
}

export function handleAudioStreamRequest(req: IncomingMessage, res: ServerResponse): void {
  if (req.method === 'OPTIONS') {
    res.statusCode = 204;
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Authorization, Range, Content-Type, If-Range');
    res.setHeader('Access-Control-Max-Age', '86400');
    res.end();
    return;
  }

  try {
    const urlObj = new URL(req.url || '/', 'http://localhost:3000');
    const auth = urlObj.searchParams.get('auth') || '';
    const rawPath = urlObj.searchParams.get('path') || '';

    if (!rawPath) {
      res.statusCode = 400;
      res.end('Missing audio path parameter');
      return;
    }

    let decodedPath = decodeURIComponent(rawPath).replace(/^\/+/, '');
    if (decodedPath.includes('%')) {
      try {
        decodedPath = decodeURIComponent(decodedPath);
      } catch {}
    }

    const upstreamPath = decodedPath.startsWith('remote.php')
      ? `/${decodedPath.split('/').map(encodeURIComponent).join('/')}`
      : `/remote.php/webdav/${decodedPath.split('/').map(encodeURIComponent).join('/')}`;

    const headers: Record<string, string> = {
      'User-Agent': 'ZentryxMusic/1.0 (Nextcloud WebDAV Client)',
      'Accept-Encoding': 'identity',
    };

    if (auth) {
      headers['Authorization'] = auth.startsWith('Basic ') ? auth : `Basic ${auth}`;
    } else if (req.headers.authorization) {
      headers['Authorization'] = req.headers.authorization;
    }

    if (req.method === 'GET') {
      const clientRange = req.headers.range;
      if (clientRange) {
        const match = clientRange.match(/bytes=(\d+)-(\d*)/i);
        if (match) {
          const start = parseInt(match[1], 10);
          const endStr = match[2];
          if (endStr) {
            const end = parseInt(endStr, 10);
            if (end - start + 1 > MAX_CHUNK_BYTES) {
              headers['Range'] = `bytes=${start}-${start + MAX_CHUNK_BYTES - 1}`;
            } else {
              headers['Range'] = clientRange;
            }
          } else {
            headers['Range'] = `bytes=${start}-${start + MAX_CHUNK_BYTES - 1}`;
          }
        } else {
          headers['Range'] = clientRange;
        }
      } else {
        headers['Range'] = `bytes=0-${MAX_CHUNK_BYTES - 1}`;
      }
    }

    if (req.headers['if-range']) {
      headers['If-Range'] = String(req.headers['if-range']);
    }

    appendProxyLog(
      `AUDIO_STREAM ${req.method} -> ${ALLOWED_NEXTCLOUD_HOST}${upstreamPath} (Auth: ${
        headers['Authorization'] ? 'PRESENT' : 'MISSING'
      }, Range: ${headers['Range'] || 'none'})`
    );

    const upstreamReq = https.request(
      {
        hostname: ALLOWED_NEXTCLOUD_HOST,
        port: 443,
        path: upstreamPath,
        method: req.method || 'GET',
        headers,
      },
      (upstreamRes) => {
        const statusCode = upstreamRes.statusCode || 200;
        res.statusCode = statusCode;

        if (statusCode >= 400) {
          res.setHeader('Content-Type', upstreamRes.headers['content-type'] || 'text/plain');
          res.setHeader('Access-Control-Allow-Origin', '*');
          let errBody = '';
          upstreamRes.on('data', (chunk) => {
            errBody += chunk.toString();
          });
          upstreamRes.on('end', () => {
            appendProxyLog(`AUDIO_STREAM RES ${statusCode} ERROR: ${errBody.slice(0, 500)}`);
            res.end(errBody);
          });
          return;
        }

        const mimeType = resolveMimeType(decodedPath, upstreamRes.headers['content-type']);
        res.setHeader('Content-Type', mimeType);
        res.setHeader('Accept-Ranges', 'bytes');
        res.setHeader('Content-Disposition', 'inline');
        res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
        res.setHeader('Access-Control-Allow-Origin', '*');
        res.setHeader('Access-Control-Allow-Headers', 'Range, Authorization, Content-Type, If-Range');
        res.setHeader(
          'Access-Control-Expose-Headers',
          'Content-Range, Content-Length, Accept-Ranges, Content-Type, ETag, Last-Modified'
        );

        if (upstreamRes.headers['content-length']) {
          res.setHeader('Content-Length', upstreamRes.headers['content-length']);
        }
        if (upstreamRes.headers['content-range']) {
          res.setHeader('Content-Range', upstreamRes.headers['content-range']);
        }
        if (upstreamRes.headers['etag']) {
          res.setHeader('ETag', upstreamRes.headers['etag']);
        }
        if (upstreamRes.headers['last-modified']) {
          res.setHeader('Last-Modified', upstreamRes.headers['last-modified']);
        }

        appendProxyLog(
          `AUDIO_STREAM RES ${statusCode} (Type: ${mimeType}, Range: ${
            upstreamRes.headers['content-range'] || 'none'
          }, Len: ${upstreamRes.headers['content-length'] || 'unknown'})`
        );
        upstreamRes.pipe(res);
      }
    );

    upstreamReq.on('error', (err) => {
      appendProxyLog(`AUDIO_STREAM ERROR: ${err.message}`);
      if (!res.headersSent) {
        res.statusCode = 502;
        res.end('Upstream stream error: ' + err.message);
      }
    });

    if (req.method === 'GET' || req.method === 'HEAD' || req.method === 'OPTIONS') {
      upstreamReq.end();
    } else {
      req.pipe(upstreamReq);
    }
  } catch (err: any) {
    if (!res.headersSent) {
      res.statusCode = 500;
      res.end('Stream handler error: ' + err.message);
    }
  }
}

export function handleNextcloudProxyRequest(req: IncomingMessage, res: ServerResponse): void {
  if (req.method === 'OPTIONS') {
    res.statusCode = 204;
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader(
      'Access-Control-Allow-Headers',
      'Authorization, Content-Type, Range, If-Range, X-Requested-With, Depth, OCS-APIREQUEST'
    );
    res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, POST, PUT, DELETE, PROPFIND, OPTIONS');
    res.setHeader(
      'Access-Control-Expose-Headers',
      'Content-Range, Content-Length, Accept-Ranges, Content-Type, ETag, Last-Modified'
    );
    res.end();
    return;
  }

  try {
    const rawUrl = req.url || '/';
    const rewrittenPath = rawUrl.replace(/^\/api\/nextcloud/, '') || '/';
    const urlObj = new URL(rewrittenPath, `https://${ALLOWED_NEXTCLOUD_HOST}`);
    const authParam = urlObj.searchParams.get('auth');
    if (authParam) {
      urlObj.searchParams.delete('auth');
    }

    const upstreamPath = urlObj.pathname + (urlObj.search ? urlObj.search : '');
    const outHeaders: Record<string, string> = {};

    for (const [k, v] of Object.entries(req.headers)) {
      if (v === undefined) continue;
      const lk = k.toLowerCase();
      if (
        lk === 'host' ||
        lk === 'origin' ||
        lk === 'referer' ||
        lk === 'cookie' ||
        lk === 'x-requested-with' ||
        lk === 'sec-fetch-mode' ||
        lk === 'sec-fetch-site' ||
        lk === 'sec-fetch-dest'
      ) {
        continue;
      }
      outHeaders[k] = Array.isArray(v) ? v.join(', ') : v;
    }

    if (authParam) {
      outHeaders['authorization'] = authParam.startsWith('Basic ') ? authParam : `Basic ${authParam}`;
    }
    outHeaders['accept-encoding'] = 'identity';
    outHeaders['user-agent'] = 'ZentryxMusic/1.0 (Nextcloud WebDAV Client)';

    if (req.method === 'HEAD') {
      delete outHeaders['range'];
    } else if (/\.(mp3|flac|m4a|aac|ogg|opus|wav|wma|m4b|alac|aiff)(\?|$)/i.test(upstreamPath)) {
      delete outHeaders['if-none-match'];
      delete outHeaders['if-modified-since'];
      const clientRange = outHeaders['range'];
      if (clientRange) {
        const match = clientRange.match(/bytes=(\d+)-(\d*)/i);
        if (match) {
          const start = parseInt(match[1], 10);
          const endStr = match[2];
          if (endStr) {
            const end = parseInt(endStr, 10);
            if (end - start + 1 > MAX_CHUNK_BYTES) {
              outHeaders['range'] = `bytes=${start}-${start + MAX_CHUNK_BYTES - 1}`;
            }
          } else {
            outHeaders['range'] = `bytes=${start}-${start + MAX_CHUNK_BYTES - 1}`;
          }
        }
      } else if (req.method === 'GET') {
        outHeaders['range'] = `bytes=0-${MAX_CHUNK_BYTES - 1}`;
      }
    }

    appendProxyLog(
      `REQ ${req.method} ${req.url} -> ${ALLOWED_NEXTCLOUD_HOST}${upstreamPath} (Auth: ${
        outHeaders['authorization'] ? 'PRESENT' : 'MISSING'
      }, Range: ${outHeaders['range'] || 'none'})`
    );

    const upstreamReq = https.request(
      {
        hostname: ALLOWED_NEXTCLOUD_HOST,
        port: 443,
        path: upstreamPath,
        method: req.method,
        headers: outHeaders,
      },
      (upstreamRes) => {
        const headers: Record<string, string | string[]> = {};
        for (const [k, v] of Object.entries(upstreamRes.headers)) {
          if (v === undefined) continue;
          const lk = k.toLowerCase();
          if (lk === 'x-frame-options' || lk === 'content-security-policy' || lk === 'x-content-type-options') {
            continue;
          }
          headers[k] = v;
        }

        const isAudio = /\.(mp3|flac|m4a|aac|ogg|opus|wav|wma|m4b|alac|aiff)(\?|$)/i.test(upstreamPath);
        if (isAudio) {
          headers['content-type'] = resolveMimeType(
            upstreamPath.split('?')[0],
            typeof headers['content-type'] === 'string' ? headers['content-type'] : undefined
          );
          headers['content-disposition'] = 'inline';
        }
        headers['access-control-allow-origin'] = '*';
        headers['access-control-allow-headers'] =
          'Authorization, Content-Type, Range, If-Range, X-Requested-With, Depth, OCS-APIREQUEST';
        headers['access-control-allow-methods'] = 'GET, HEAD, POST, PUT, DELETE, PROPFIND, OPTIONS';
        headers['access-control-expose-headers'] =
          'Content-Range, Content-Length, Accept-Ranges, Content-Type, ETag, Last-Modified';
        if (!headers['accept-ranges']) {
          headers['accept-ranges'] = 'bytes';
        }

        res.writeHead(upstreamRes.statusCode || 502, headers);
        upstreamRes.pipe(res);

        appendProxyLog(
          `RES ${upstreamRes.statusCode} ${req.method} ${req.url} (Len: ${
            upstreamRes.headers['content-length'] || 'unknown'
          })`
        );
      }
    );

    upstreamReq.on('error', (err) => {
      appendProxyLog(`PROXY SOCKET ERROR: ${err.message} on ${req.method} ${req.url}`);
      if (!res.headersSent) {
        res.writeHead(502, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Proxy communication error', message: err.message }));
      }
    });

    req.pipe(upstreamReq);
  } catch (err: any) {
    if (!res.headersSent) {
      res.writeHead(500, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: err.message }));
    }
  }
}

export function handleDiagnosticsLogsRequest(_req: IncomingMessage, res: ServerResponse): void {
  try {
    const content = fs.existsSync('/tmp/proxy.log') ? fs.readFileSync('/tmp/proxy.log', 'utf8') : '';
    res.setHeader('Content-Type', 'text/plain; charset=utf-8');
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.end(content || 'No proxy network transactions recorded yet.');
  } catch (e: any) {
    res.setHeader('Content-Type', 'text/plain; charset=utf-8');
    res.end('Error reading proxy log: ' + e.message);
  }
}

export function handleDiagnosticsClearRequest(_req: IncomingMessage, res: ServerResponse): void {
  try {
    if (fs.existsSync('/tmp/proxy.log')) {
      fs.writeFileSync('/tmp/proxy.log', `[${new Date().toISOString()}] Diagnostics log reset.\n`);
    }
    res.setHeader('Content-Type', 'application/json');
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.end(JSON.stringify({ success: true, message: 'Server proxy logs cleared.' }));
  } catch (e: any) {
    res.statusCode = 500;
    res.end(JSON.stringify({ error: e.message }));
  }
}

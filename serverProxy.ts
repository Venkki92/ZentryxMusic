import fs from 'fs';
import path from 'path';
import https from 'https';
import crypto from 'crypto';
import { spawn } from 'child_process';
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

interface ApkVariantInfo {
  exists: boolean;
  filename: string;
  version: string;
  versionCode: number;
  sizeBytes: number;
  updatedAt: string | null;
  url: string;
  filePath: string;
}

interface ApkVersionInfo {
  versionName: string;
  versionCode: number;
}

let activeApkBuild: { running: boolean; startedAt: string | null; lastError: string | null } = {
  running: false,
  startedAt: null,
  lastError: null,
};

function readVersionProperties(cwd: string): ApkVersionInfo {
  const propsPath = path.resolve(cwd, 'android/version.properties');
  if (!fs.existsSync(propsPath)) {
    return { versionName: '0.0.1', versionCode: 1 };
  }
  try {
    const raw = fs.readFileSync(propsPath, 'utf8');
    let versionName = '0.0.1';
    let versionCode = 1;
    for (const line of raw.split(/\r?\n/)) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const eq = trimmed.indexOf('=');
      if (eq === -1) continue;
      const key = trimmed.slice(0, eq).trim();
      const val = trimmed.slice(eq + 1).trim();
      if (key === 'VERSION_NAME' && val) versionName = val;
      if (key === 'VERSION_CODE') {
        const parsed = parseInt(val, 10);
        if (Number.isFinite(parsed) && parsed > 0) versionCode = parsed;
      }
    }
    return { versionName, versionCode };
  } catch {
    return { versionName: '0.0.1', versionCode: 1 };
  }
}

function readOutputMetadataVersion(cwd: string, variant: 'release' | 'debug'): ApkVersionInfo | null {
  const metaPath = path.resolve(cwd, `android/app/build/outputs/apk/${variant}/output-metadata.json`);
  if (!fs.existsSync(metaPath)) return null;
  try {
    const parsed = JSON.parse(fs.readFileSync(metaPath, 'utf8'));
    const el = Array.isArray(parsed?.elements) ? parsed.elements[0] : null;
    if (el && typeof el.versionName === 'string' && el.versionName.trim()) {
      return {
        versionName: el.versionName.trim(),
        versionCode: typeof el.versionCode === 'number' ? el.versionCode : 1,
      };
    }
  } catch {}
  return null;
}

function collectAndroidFiles(dirPath: string, out: string[] = []): string[] {
  if (!fs.existsSync(dirPath)) return out;
  const entries = fs.readdirSync(dirPath, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name));
  for (const entry of entries) {
    const full = path.join(dirPath, entry.name);
    if (entry.isDirectory()) {
      collectAndroidFiles(full, out);
    } else if (entry.isFile()) {
      out.push(full);
    }
  }
  return out;
}

function isAndroidApkStale(cwd: string): boolean {
  const androidDir = path.resolve(cwd, 'android');
  const releaseApk = path.resolve(androidDir, 'app/build/outputs/apk/release/app-release.apk');
  if (!fs.existsSync(releaseApk)) return true;

  const fingerprintPath = path.resolve(androidDir, 'app/build/.apk-source-fingerprint');
  const explicitFiles = [
    path.join(androidDir, 'build.gradle.kts'),
    path.join(androidDir, 'settings.gradle.kts'),
    path.join(androidDir, 'gradle.properties'),
    path.join(androidDir, 'gradle/libs.versions.toml'),
    path.join(androidDir, 'app/build.gradle.kts'),
    path.join(androidDir, 'app/proguard-rules.pro'),
  ];
  const allFiles = [...explicitFiles, ...collectAndroidFiles(path.join(androidDir, 'app/src'))];

  if (fs.existsSync(fingerprintPath)) {
    try {
      const saved = fs.readFileSync(fingerprintPath, 'utf8').trim();
      const hash = crypto.createHash('sha256');
      for (const file of allFiles) {
        if (!fs.existsSync(file)) continue;
        hash.update(path.relative(androidDir, file));
        hash.update('\0');
        hash.update(fs.readFileSync(file));
        hash.update('\0');
      }
      return hash.digest('hex') !== saved;
    } catch {}
  }

  const apkMtimeMs = fs.statSync(releaseApk).mtimeMs;
  return allFiles.some((f) => fs.existsSync(f) && fs.statSync(f).mtimeMs > apkMtimeMs);
}

function resolveApkInfo(variant: 'release' | 'debug'): ApkVariantInfo {
  const cwd = process.cwd();
  const candidates =
    variant === 'release'
      ? [
          path.resolve(cwd, 'android/app/build/outputs/apk/release/app-release.apk'),
          path.resolve(cwd, 'android/app/build/outputs/apk/release/app-release-unsigned.apk'),
        ]
      : [path.resolve(cwd, 'android/app/build/outputs/apk/debug/app-debug.apk')];

  const versionInfo = readOutputMetadataVersion(cwd, variant) || readVersionProperties(cwd);
  const foundPath = candidates.find((p) => fs.existsSync(p));
  const filename = `ZentryxMusic-v${versionInfo.versionName}-b${versionInfo.versionCode}-${variant}.apk`;

  if (!foundPath) {
    return {
      exists: false,
      filename,
      version: versionInfo.versionName,
      versionCode: versionInfo.versionCode,
      sizeBytes: 0,
      updatedAt: null,
      url: `/api/android-apk/${variant}`,
      filePath: candidates[0],
    };
  }

  const stat = fs.statSync(foundPath);
  return {
    exists: true,
    filename,
    version: versionInfo.versionName,
    versionCode: versionInfo.versionCode,
    sizeBytes: stat.size,
    updatedAt: stat.mtime.toISOString(),
    url: `/api/android-apk/${variant}`,
    filePath: foundPath,
  };
}

export function handleAndroidApkStatusRequest(_req: IncomingMessage, res: ServerResponse): void {
  const cwd = process.cwd();
  const release = resolveApkInfo('release');
  const debug = resolveApkInfo('debug');
  const activeVersion = release.exists ? release.version : debug.exists ? debug.version : readVersionProperties(cwd).versionName;
  const activeVersionCode = release.exists ? release.versionCode : debug.exists ? debug.versionCode : readVersionProperties(cwd).versionCode;

  res.statusCode = 200;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.end(
    JSON.stringify({
      available: release.exists || debug.exists,
      version: activeVersion,
      versionCode: activeVersionCode,
      applicationId: 'com.zentryx.music',
      stale: isAndroidApkStale(cwd),
      building: activeApkBuild.running,
      buildStartedAt: activeApkBuild.startedAt,
      buildError: activeApkBuild.lastError,
      release: {
        exists: release.exists,
        filename: release.filename,
        version: release.version,
        versionCode: release.versionCode,
        sizeBytes: release.sizeBytes,
        updatedAt: release.updatedAt,
        url: release.url,
      },
      debug: {
        exists: debug.exists,
        filename: debug.filename,
        version: debug.version,
        versionCode: debug.versionCode,
        sizeBytes: debug.sizeBytes,
        updatedAt: debug.updatedAt,
        url: debug.url,
      },
    })
  );
}

export function handleAndroidApkRebuildRequest(req: IncomingMessage, res: ServerResponse): void {
  if (req.method === 'OPTIONS') {
    res.statusCode = 204;
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
    res.end();
    return;
  }

  if (req.method !== 'POST') {
    res.statusCode = 405;
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.end(JSON.stringify({ error: 'Method Not Allowed', message: 'Use POST to trigger APK rebuild.' }));
    return;
  }

  if (activeApkBuild.running) {
    res.statusCode = 202;
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.end(JSON.stringify({ started: false, building: true, startedAt: activeApkBuild.startedAt }));
    return;
  }

  const cwd = process.cwd();
  const scriptPath = path.resolve(cwd, 'scripts/build-android-apk.mjs');
  if (!fs.existsSync(scriptPath)) {
    res.statusCode = 500;
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.end(JSON.stringify({ error: 'Build script missing' }));
    return;
  }

  activeApkBuild = {
    running: true,
    startedAt: new Date().toISOString(),
    lastError: null,
  };

  let stderrTail = '';
  const child = spawn(process.execPath, [scriptPath], {
    cwd,
    stdio: ['ignore', 'ignore', 'pipe'],
  });

  child.stderr?.on('data', (chunk) => {
    stderrTail = (stderrTail + chunk.toString()).slice(-2000);
  });

  child.on('close', (code) => {
    activeApkBuild = {
      running: false,
      startedAt: activeApkBuild.startedAt,
      lastError: code === 0 ? null : stderrTail.trim() || `Gradle exited with code ${code}`,
    };
  });

  child.on('error', (err) => {
    activeApkBuild = {
      running: false,
      startedAt: activeApkBuild.startedAt,
      lastError: err.message,
    };
  });

  res.statusCode = 202;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.end(JSON.stringify({ started: true, building: true, startedAt: activeApkBuild.startedAt }));
}

export function handleAndroidApkDownloadRequest(
  req: IncomingMessage,
  res: ServerResponse,
  variant: 'release' | 'debug'
): void {
  let info = resolveApkInfo(variant);
  if (!info.exists && variant === 'release') {
    const fallback = resolveApkInfo('debug');
    if (fallback.exists) info = fallback;
  }

  if (!info.exists) {
    res.statusCode = 404;
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.end(
      JSON.stringify({
        error: 'APK not found',
        message: `No compiled ${variant} APK present yet.`,
      })
    );
    return;
  }

  res.statusCode = 200;
  res.setHeader('Content-Type', 'application/vnd.android.package-archive');
  res.setHeader('Content-Disposition', `attachment; filename="${info.filename}"`);
  res.setHeader('Content-Length', String(info.sizeBytes));
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Access-Control-Allow-Origin', '*');

  if (req.method === 'HEAD') {
    res.end();
    return;
  }

  const stream = fs.createReadStream(info.filePath);
  stream.on('error', (err) => {
    if (!res.headersSent) {
      res.statusCode = 500;
      res.end('Failed to read APK: ' + err.message);
    } else {
      res.destroy(err);
    }
  });
  stream.pipe(res);
}



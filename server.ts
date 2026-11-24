import express from 'express';
import fs from 'fs';
import path from 'path';
import zlib from 'zlib';
import { fileURLToPath } from 'url';
import {
  handleAndroidApkDownloadRequest,
  handleAndroidApkRebuildRequest,
  handleAndroidApkStatusRequest,
  handleAudioStreamRequest,
  handleDiagnosticsClearRequest,
  handleDiagnosticsLogsRequest,
  handleNextcloudProxyRequest,
} from './serverProxy';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

function resolvePort(): number {
  const argv = process.argv.slice(2);
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--port' && argv[i + 1]) {
      const parsed = Number(argv[i + 1]);
      if (Number.isFinite(parsed) && parsed > 0) return parsed;
    } else if (argv[i].startsWith('--port=')) {
      const parsed = Number(argv[i].slice('--port='.length));
      if (Number.isFinite(parsed) && parsed > 0) return parsed;
    }
  }
  return Number(process.env.PORT || 3000);
}

const PORT = resolvePort();
const DIST_DIR = path.join(__dirname, 'dist');
const DIST_INDEX = path.join(DIST_DIR, 'index.html');
const IS_PREVIEW_NO_HMR = process.env.DISABLE_HMR === 'true';
const IS_PRODUCTION = process.env.NODE_ENV === 'production';

const COMPRESSIBLE_MIME: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.svg': 'image/svg+xml; charset=utf-8',
};

const gzipCache = new Map<string, { mtimeMs: number; raw: Buffer; gz: Buffer }>();

function readCachedAsset(filePath: string): { raw: Buffer; gz: Buffer } | null {
  try {
    const stat = fs.statSync(filePath);
    if (!stat.isFile()) return null;
    const cached = gzipCache.get(filePath);
    if (cached && cached.mtimeMs === stat.mtimeMs) {
      return { raw: cached.raw, gz: cached.gz };
    }
    const raw = fs.readFileSync(filePath);
    const gz = zlib.gzipSync(raw, { level: 6 });
    gzipCache.set(filePath, { mtimeMs: stat.mtimeMs, raw, gz });
    return { raw, gz };
  } catch {
    return null;
  }
}

function sendCompressedFile(
  req: express.Request,
  res: express.Response,
  filePath: string,
  cacheControl: string
): boolean {
  const ext = path.extname(filePath).toLowerCase();
  const contentType = COMPRESSIBLE_MIME[ext];
  if (!contentType) return false;

  const asset = readCachedAsset(filePath);
  if (!asset) return false;

  const acceptEncoding = String(req.headers['accept-encoding'] || '');
  res.setHeader('Content-Type', contentType);
  res.setHeader('Cache-Control', cacheControl);
  res.setHeader('Vary', 'Accept-Encoding');

  if (/\bgzip\b/i.test(acceptEncoding)) {
    res.setHeader('Content-Encoding', 'gzip');
    res.setHeader('Content-Length', String(asset.gz.length));
    res.status(200).end(asset.gz);
  } else {
    res.setHeader('Content-Length', String(asset.raw.length));
    res.status(200).end(asset.raw);
  }
  return true;
}

const PREVIEW_SW_UNREGISTER_SCRIPT = `
if (typeof navigator !== 'undefined' && 'serviceWorker' in navigator) {
  navigator.serviceWorker.getRegistrations().then(function (regs) {
    regs.forEach(function (r) { r.unregister().catch(function () {}); });
  }).catch(function () {});
}
`;

async function startServer() {
  const app = express();
  app.disable('x-powered-by');

  // API & Nextcloud WebDAV Proxy Routes
  app.use('/api/audio-stream', (req, res) => handleAudioStreamRequest(req, res));
  app.use('/api/nextcloud', (req, res) => {
    req.url = `/api/nextcloud${req.url === '/' ? '' : req.url}`;
    handleNextcloudProxyRequest(req, res);
  });
  app.use('/api/diagnostics/logs', (req, res) => handleDiagnosticsLogsRequest(req, res));
  app.use('/api/diagnostics/clear', (req, res) => handleDiagnosticsClearRequest(req, res));
  app.use('/api/android-apk/status', (req, res) => handleAndroidApkStatusRequest(req, res));
  app.use('/api/android-apk/rebuild', (req, res) => handleAndroidApkRebuildRequest(req, res));
  app.use('/api/android-apk/release', (req, res) => handleAndroidApkDownloadRequest(req, res, 'release'));
  app.use('/api/android-apk/debug', (req, res) => handleAndroidApkDownloadRequest(req, res, 'debug'));
  app.use('/zentryx-music.apk', (req, res) => handleAndroidApkDownloadRequest(req, res, 'release'));

  const serveBuiltBundle = (IS_PREVIEW_NO_HMR || IS_PRODUCTION) && fs.existsSync(DIST_INDEX);

  if (serveBuiltBundle) {
    if (IS_PREVIEW_NO_HMR) {
      // Prevent stale preview service worker caches across AI Studio reloads
      app.get('/registerSW.js', (_req, res) => {
        res.setHeader('Content-Type', 'application/javascript; charset=utf-8');
        res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
        res.status(200).send(PREVIEW_SW_UNREGISTER_SCRIPT);
      });
    }

    // Serve gzip-compressed static assets from dist/ (cuts boot from 45 dev requests / 9.8MB -> 3 requests / ~305KB)
    app.use((req, res, next) => {
      if (req.method !== 'GET' && req.method !== 'HEAD') {
        next();
        return;
      }
      let cleanPath = req.path || '/';
      try {
        cleanPath = decodeURIComponent(cleanPath);
      } catch {}
      if (cleanPath === '/' || cleanPath === '/index.html') {
        if (sendCompressedFile(req, res, DIST_INDEX, 'no-cache, no-store, must-revalidate')) {
          return;
        }
      }
      const resolved = path.resolve(DIST_DIR, `.${cleanPath}`);
      if (resolved.startsWith(DIST_DIR + path.sep)) {
        const isHashedAsset = cleanPath.startsWith('/assets/');
        const cacheHeader = isHashedAsset
          ? 'public, max-age=31536000, immutable'
          : 'public, max-age=300';
        if (sendCompressedFile(req, res, resolved, cacheHeader)) {
          return;
        }
      }
      next();
    });

    app.use(express.static(DIST_DIR, { index: false, maxAge: '1h' }));

    app.use((req, res) => {
      if (!sendCompressedFile(req, res, DIST_INDEX, 'no-cache, no-store, must-revalidate')) {
        res.sendFile(DIST_INDEX);
      }
    });
  } else {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  }

  app.listen(PORT, '0.0.0.0', () => {
    const mode = serveBuiltBundle ? (IS_PREVIEW_NO_HMR ? 'preview-bundled-gzip' : 'production-gzip') : 'vite-dev-hmr';
    console.log(`Zentryx Music server (${mode}) listening on http://0.0.0.0:${PORT}`);
  });
}

startServer().catch((err) => {
  console.error('Failed to start Zentryx Music server:', err);
  process.exit(1);
});


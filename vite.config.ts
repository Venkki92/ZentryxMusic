import fs from 'fs';
import path from 'path';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import {defineConfig} from 'vite';
import {VitePWA} from 'vite-plugin-pwa';
import {
  AUDIO_FILE_EXTENSION_RE,
  WEBDAV_CHUNK_MAX_RETRIES,
  computeRetryBackoffMs,
  handleAndroidApkDownloadRequest,
  handleAndroidApkRebuildRequest,
  handleAndroidApkStatusRequest,
  handleAudioStreamRequest,
  handleDiagnosticsClearRequest,
  handleDiagnosticsLogsRequest,
  isRetryableProxyError,
  shouldRetryChunkRequest,
} from './serverProxy.ts';

const WEBDAV_PROXY_TARGET = 'https://cloud.zentryx.bg';

type RetryableProxyReq = {
  method?: string;
  url?: string;
  destroyed?: boolean;
  headers?: Record<string, string | string[] | undefined>;
  once?: (event: string, listener: () => void) => void;
  __webdavRetryCount?: number;
  __webdavRetryTimer?: ReturnType<typeof setTimeout>;
  __webdavClientAborted?: boolean;
  __webdavCloseHooked?: boolean;
};

const diagnosticsApiPlugin = {
  name: 'diagnostics-api-plugin',
  configureServer(server: any) {
    server.middlewares.use('/api/audio-stream', handleAudioStreamRequest);
    server.middlewares.use('/api/diagnostics/logs', handleDiagnosticsLogsRequest);
    server.middlewares.use('/api/diagnostics/clear', handleDiagnosticsClearRequest);
    server.middlewares.use('/api/android-apk/status', handleAndroidApkStatusRequest);
    server.middlewares.use('/api/android-apk/rebuild', handleAndroidApkRebuildRequest);
    server.middlewares.use('/api/android-apk/release', (req: any, res: any) =>
      handleAndroidApkDownloadRequest(req, res, 'release')
    );
    server.middlewares.use('/api/android-apk/debug', (req: any, res: any) =>
      handleAndroidApkDownloadRequest(req, res, 'debug')
    );
    server.middlewares.use('/zentryx-music.apk', (req: any, res: any) =>
      handleAndroidApkDownloadRequest(req, res, 'release')
    );
  },
  configurePreviewServer(server: any) {
    server.middlewares.use('/api/audio-stream', handleAudioStreamRequest);
    server.middlewares.use('/api/diagnostics/logs', handleDiagnosticsLogsRequest);
    server.middlewares.use('/api/diagnostics/clear', handleDiagnosticsClearRequest);
    server.middlewares.use('/api/android-apk/status', handleAndroidApkStatusRequest);
    server.middlewares.use('/api/android-apk/rebuild', handleAndroidApkRebuildRequest);
    server.middlewares.use('/api/android-apk/release', (req: any, res: any) =>
      handleAndroidApkDownloadRequest(req, res, 'release')
    );
    server.middlewares.use('/api/android-apk/debug', (req: any, res: any) =>
      handleAndroidApkDownloadRequest(req, res, 'debug')
    );
    server.middlewares.use('/zentryx-music.apk', (req: any, res: any) =>
      handleAndroidApkDownloadRequest(req, res, 'release')
    );
  },
};

const NOOP_VITE_CLIENT_STUB = `
const sheetsMap = new Map();
export function updateStyle(id, content) {
  let style = sheetsMap.get(id);
  if (!style) {
    style = document.createElement('style');
    style.setAttribute('type', 'text/css');
    style.setAttribute('data-vite-dev-id', id);
    style.textContent = content;
    document.head.appendChild(style);
    sheetsMap.set(id, style);
  } else {
    style.textContent = content;
  }
}
export function removeStyle(id) {
  const style = sheetsMap.get(id);
  if (style) {
    document.head.removeChild(style);
    sheetsMap.delete(id);
  }
}
export function createHotContext() {
  return {
    data: {},
    accept() {},
    acceptExports() {},
    dispose() {},
    prune() {},
    invalidate() {},
    on() {},
    off() {},
    send() {},
  };
}
export function injectQuery(url) {
  return url;
}
export class ErrorOverlay extends HTMLElement {}
`;

const disableHmrClientPlugin = {
  name: 'disable-hmr-client-stub',
  configureServer(server: any) {
    if (process.env.DISABLE_HMR !== 'true') return;
    server.middlewares.use((req: any, res: any, next: () => void) => {
      const reqUrl = (req.url || '').split('?')[0];
      if (reqUrl === '/@vite/client') {
        res.statusCode = 200;
        res.setHeader('Content-Type', 'application/javascript; charset=utf-8');
        res.setHeader('Cache-Control', 'public, max-age=300');
        res.end(NOOP_VITE_CLIENT_STUB);
        return;
      }
      next();
    });
  },
  transformIndexHtml: {
    order: 'post' as const,
    handler(html: string) {
      if (process.env.DISABLE_HMR !== 'true') return html;
      return html.replace(/<script\s+type="module"\s+src="\/@vite\/client"><\/script>\s*/gi, '');
    },
  },
};

export default defineConfig(() => {
  return {
    plugins: [
      react(),
      tailwindcss(),
      diagnosticsApiPlugin,
      disableHmrClientPlugin,
      VitePWA({
        registerType: 'autoUpdate',
        includeAssets: ['icon.svg', 'apple-touch-icon.png', 'pwa-192x192.png', 'pwa-512x512.png', 'pwa-maskable-512x512.png'],
        manifest: {
          id: '/',
          name: 'Zentryx Music',
          short_name: 'Zentryx',
          description: 'Mercedes S203 Android headunit Nextcloud music player with live DSP, offline caching, and custom visualizers.',
          theme_color: '#020617',
          background_color: '#020617',
          display: 'standalone',
          start_url: '/',
          scope: '/',
          icons: [
            {
              src: '/pwa-192x192.png',
              sizes: '192x192',
              type: 'image/png',
              purpose: 'any',
            },
            {
              src: '/pwa-512x512.png',
              sizes: '512x512',
              type: 'image/png',
              purpose: 'any',
            },
            {
              src: '/pwa-maskable-512x512.png',
              sizes: '512x512',
              type: 'image/png',
              purpose: 'maskable',
            },
          ],
        },
        workbox: {
          globPatterns: ['**/*.{js,css,html,ico,png,svg,woff,woff2}'],
          navigateFallbackDenylist: [/^\/api\//, /\.apk$/i],
        },
        devOptions: {
          enabled: false,
        },
      }),
    ],
    optimizeDeps: {
      include: ['react', 'react-dom', 'react-dom/client', 'lucide-react', 'motion', 'motion/react'],
    },
    resolve: {
      alias: {
        '@': path.resolve(import.meta.dirname, '.'),
      },
    },
    server: {
      // Allow AI Studio / Cloud Run preview proxy hostnames (Vite 6+/8+ blocks unknown Host headers with 403 by default)
      allowedHosts: true,
      // HMR is disabled in AI Studio via DISABLE_HMR env var.
      // Do not modify—file watching is disabled to prevent flickering during agent edits.
      hmr: process.env.DISABLE_HMR !== 'true',
      // Disable file watching when DISABLE_HMR is true to save CPU during agent edits.
      watch: process.env.DISABLE_HMR === 'true' ? null : {},
      proxy: {
        '/api/nextcloud': {
          target: WEBDAV_PROXY_TARGET,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api\/nextcloud/, ''),
          secure: true,
          timeout: 300000,
          proxyTimeout: 300000,
          configure: (proxy) => {
            proxy.on('proxyReq', (proxyReq, req) => {
              const r = req as unknown as RetryableProxyReq;
              if (!r.__webdavCloseHooked && typeof r.once === 'function') {
                r.__webdavCloseHooked = true;
                r.once('close', () => {
                  r.__webdavClientAborted = true;
                  if (r.__webdavRetryTimer) {
                    clearTimeout(r.__webdavRetryTimer);
                    r.__webdavRetryTimer = undefined;
                  }
                });
              }

              // 1. Robust Authentication extraction: handle query param ?auth= or incoming Authorization header
              if (proxyReq.path) {
                const queryIndex = proxyReq.path.indexOf('?');
                if (queryIndex !== -1) {
                  const query = proxyReq.path.slice(queryIndex + 1);
                  const params = new URLSearchParams(query);
                  const authVal = params.get('auth');
                  if (authVal) {
                    const cleanAuth = authVal.startsWith('Basic ') ? authVal : `Basic ${authVal}`;
                    proxyReq.setHeader('Authorization', cleanAuth);
                    params.delete('auth');
                    const remaining = params.toString();
                    proxyReq.path = proxyReq.path.slice(0, queryIndex) + (remaining ? `?${remaining}` : '');
                  }
                }
              }

              // Preserve incoming header Authorization if not already set from query
              if (req.headers && req.headers.authorization && !proxyReq.getHeader('authorization')) {
                proxyReq.setHeader('Authorization', req.headers.authorization);
              }

              // 2. WebDAV Range & Partial Content Support:
              // Strip Range on HEAD requests: Nextcloud SabreDAV crashes with HTTP 500 when Range is present on HEAD!
              if (req.method === 'HEAD') {
                proxyReq.removeHeader('range');
              } else if (req.url && AUDIO_FILE_EXTENSION_RE.test(req.url)) {
                // Audio stream: cap chunk to <= 8 MiB so responses never exceed Cloud Run 32 MiB GFE limit
                const MAX_CHUNK = 8 * 1024 * 1024;
                const clientRange = req.headers && req.headers.range;
                if (clientRange) {
                  const match = clientRange.match(/bytes=(\d+)-(\d*)/i);
                  if (match) {
                    const start = parseInt(match[1], 10);
                    const endStr = match[2];
                    if (endStr) {
                      const end = parseInt(endStr, 10);
                      if (end - start + 1 > MAX_CHUNK) {
                        proxyReq.setHeader('Range', `bytes=${start}-${start + MAX_CHUNK - 1}`);
                      } else {
                        proxyReq.setHeader('Range', clientRange);
                      }
                    } else {
                      proxyReq.setHeader('Range', `bytes=${start}-${start + MAX_CHUNK - 1}`);
                    }
                  } else {
                    proxyReq.setHeader('Range', clientRange);
                  }
                } else if (req.method === 'GET') {
                  proxyReq.setHeader('Range', `bytes=0-${MAX_CHUNK - 1}`);
                }
              } else if (req.headers && req.headers.range) {
                proxyReq.setHeader('Range', req.headers.range);
              }
              if (req.headers && req.headers['if-range']) {
                proxyReq.setHeader('If-Range', req.headers['if-range']);
              }

              // 3. Disable upstream compression on media streams:
              // Compression breaks Content-Range responses in SabreDAV and triggers HTTP 500 errors
              proxyReq.setHeader('Accept-Encoding', 'identity');

              // 4. API & WebDAV client identification:
              // Strip x-requested-with: Nextcloud SabreDAV explicitly rejects Basic Auth with
              // "Cannot authenticate over ajax calls" if X-Requested-With is present!
              proxyReq.removeHeader('x-requested-with');
              proxyReq.setHeader('User-Agent', 'ZentryxMusic/1.0 (Nextcloud WebDAV Client)');

              // 5. Strip browser-specific origin, referer, cookies and sec-fetch headers
              // These headers cause Nextcloud to treat the request as an unauthenticated web session
              proxyReq.removeHeader('origin');
              proxyReq.removeHeader('referer');
              proxyReq.removeHeader('cookie');
              proxyReq.removeHeader('sec-fetch-mode');
              proxyReq.removeHeader('sec-fetch-site');
              proxyReq.removeHeader('sec-fetch-dest');

              // 6. Strip conditional caching headers on audio streams so Nextcloud never returns 0-byte 304 Not Modified
              if (req.url && AUDIO_FILE_EXTENSION_RE.test(req.url)) {
                proxyReq.removeHeader('if-none-match');
                proxyReq.removeHeader('if-modified-since');
              }

              // Log outgoing proxy request to /tmp/proxy.log for settings diagnostics
              try {
                const authPresent = Boolean(proxyReq.getHeader('authorization'));
                const rangeHeader = proxyReq.getHeader('range') || 'none';
                const retryAttempt = r.__webdavRetryCount || 0;
                const retryTag = retryAttempt > 0 ? `, Retry: ${retryAttempt}/${WEBDAV_CHUNK_MAX_RETRIES}` : '';
                const logLine = `[${new Date().toISOString()}] REQ ${req.method} ${req.url} -> cloud.zentryx.bg${proxyReq.path} (Auth: ${authPresent ? 'PRESENT' : 'MISSING'}, Range: ${rangeHeader}${retryTag})\n`;
                fs.appendFileSync('/tmp/proxy.log', logLine);
              } catch {}
            });

            proxy.on('proxyRes', (proxyRes, req) => {
              const r = req as unknown as RetryableProxyReq;
              const retries = r.__webdavRetryCount || 0;

              // Ensure cross-origin framing and streaming headers
              delete proxyRes.headers['x-frame-options'];
              delete proxyRes.headers['content-security-policy'];
              delete proxyRes.headers['x-content-type-options'];

              // Force inline disposition so browsers treat audio as media stream instead of attachment download
              delete proxyRes.headers['content-disposition'];
              proxyRes.headers['content-disposition'] = 'inline';

              // Ensure correct MIME type for audio streams
              if (req.url) {
                const cleanUrl = req.url.split('?')[0].toLowerCase();
                if (cleanUrl.endsWith('.mp3')) {
                  proxyRes.headers['content-type'] = 'audio/mpeg';
                } else if (cleanUrl.endsWith('.flac')) {
                  proxyRes.headers['content-type'] = 'audio/flac';
                } else if (cleanUrl.endsWith('.ogg') || cleanUrl.endsWith('.opus')) {
                  proxyRes.headers['content-type'] = 'audio/ogg';
                } else if (cleanUrl.endsWith('.wav')) {
                  proxyRes.headers['content-type'] = 'audio/wav';
                } else if (cleanUrl.endsWith('.m4a') || cleanUrl.endsWith('.aac')) {
                  proxyRes.headers['content-type'] = 'audio/mp4';
                }
              }

              proxyRes.headers['access-control-allow-origin'] = '*';
              proxyRes.headers['access-control-allow-headers'] =
                'Authorization, Content-Type, Range, If-Range, X-Requested-With, Depth, OCS-APIREQUEST';
              proxyRes.headers['access-control-allow-methods'] =
                'GET, HEAD, POST, PUT, DELETE, PROPFIND, OPTIONS';
              proxyRes.headers['access-control-expose-headers'] =
                'Content-Range, Content-Length, Accept-Ranges, Content-Type, ETag, Last-Modified, X-Zentryx-Proxy-Retries';
              if (retries > 0) {
                proxyRes.headers['x-zentryx-proxy-retries'] = String(retries);
              }

              // Explicitly signal range support to HTML5 audio players for smooth seeking
              if (!proxyRes.headers['accept-ranges']) {
                proxyRes.headers['accept-ranges'] = 'bytes';
              }

              // Log response metadata
              try {
                const status = proxyRes.statusCode;
                const ctype = proxyRes.headers['content-type'] || 'unknown';
                const crange = proxyRes.headers['content-range'] || 'none';
                const clen = proxyRes.headers['content-length'] || 'unknown';
                const recoveryTag = retries > 0 ? `, Recovered after ${retries} retries` : '';
                const logLine = `[${new Date().toISOString()}] RES ${status} ${req.method} ${req.url} (Type: ${ctype}, Range: ${crange}, Len: ${clen}${recoveryTag})\n`;
                fs.appendFileSync('/tmp/proxy.log', logLine);

                // If error status (>= 400), capture body chunk to log
                if (status && status >= 400) {
                  let errBody = '';
                  proxyRes.on('data', (chunk) => {
                    errBody += chunk.toString();
                  });
                  proxyRes.on('end', () => {
                    try {
                      fs.appendFileSync('/tmp/proxy.log', `[${new Date().toISOString()}] ERROR BODY (${status}):\n${errBody.slice(0, 2000)}\n----------------------------------------\n`);
                    } catch {}
                  });
                }
              } catch {}
            });

            proxy.on('error', (err, req, res: any) => {
              const r = req as unknown as RetryableProxyReq;
              const currentAttempt = r.__webdavRetryCount || 0;
              const rangeHeader =
                typeof req.headers?.range === 'string'
                  ? req.headers.range
                  : Array.isArray(req.headers?.range)
                    ? req.headers.range[0]
                    : undefined;

              const canRetry = shouldRetryChunkRequest({
                method: req.method,
                url: req.url,
                rangeHeader,
                attempt: currentAttempt,
                maxRetries: WEBDAV_CHUNK_MAX_RETRIES,
                headersSent: Boolean(res && res.headersSent),
                responseEnded: Boolean(res && res.writableEnded),
                clientDisconnected: Boolean(r.__webdavClientAborted || req.destroyed),
                error: err,
              });

              if (canRetry) {
                const nextAttempt = currentAttempt + 1;
                r.__webdavRetryCount = nextAttempt;
                const delayMs = computeRetryBackoffMs(nextAttempt);
                const errCode = (err as NodeJS.ErrnoException).code || err.message;
                console.warn(
                  `[Nextcloud WebDAV Proxy Retry ${nextAttempt}/${WEBDAV_CHUNK_MAX_RETRIES} in ${delayMs}ms]:`,
                  errCode,
                  req.url
                );
                try {
                  fs.appendFileSync(
                    '/tmp/proxy.log',
                    `[${new Date().toISOString()}] PROXY CHUNK RETRY ${nextAttempt}/${WEBDAV_CHUNK_MAX_RETRIES} in ${delayMs}ms (${errCode}) on ${req.method} ${req.url}\n`
                  );
                } catch {}

                r.__webdavRetryTimer = setTimeout(() => {
                  r.__webdavRetryTimer = undefined;
                  if (r.__webdavClientAborted || req.destroyed || res?.headersSent || res?.writableEnded) {
                    return;
                  }
                  proxy.web(req, res, {
                    target: WEBDAV_PROXY_TARGET,
                    changeOrigin: true,
                    secure: true,
                    timeout: 300000,
                    proxyTimeout: 300000,
                  });
                }, delayMs);
                return;
              }

              console.warn('[Nextcloud WebDAV Proxy Notice]:', err.message);
              try {
                fs.appendFileSync(
                  '/tmp/proxy.log',
                  `[${new Date().toISOString()}] PROXY SOCKET ERROR (after ${currentAttempt} retries): ${err.message} on ${req.method} ${req.url}\n`
                );
              } catch {}
              if (res && typeof res.writeHead === 'function' && !res.headersSent && !res.writableEnded) {
                res.writeHead(502, {
                  'Content-Type': 'application/json',
                  'Retry-After': '2',
                  'X-Zentryx-Proxy-Retries': String(currentAttempt),
                });
                res.end(
                  JSON.stringify({
                    error: 'Proxy communication error',
                    message: err.message,
                    code: (err as NodeJS.ErrnoException).code || 'PROXY_ERROR',
                    retriesAttempted: currentAttempt,
                    retryable: isRetryableProxyError(err),
                  })
                );
              }
            });
          }
        }
      }
    },
  };
});

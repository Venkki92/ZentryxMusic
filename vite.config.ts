import fs from 'fs';
import path from 'path';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import {defineConfig} from 'vite';
import {VitePWA} from 'vite-plugin-pwa';
import {
  handleAndroidApkDownloadRequest,
  handleAndroidApkRebuildRequest,
  handleAndroidApkStatusRequest,
  handleAudioStreamRequest,
  handleDiagnosticsClearRequest,
  handleDiagnosticsLogsRequest,
} from './serverProxy.ts';

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

export default defineConfig(() => {
  return {
    plugins: [
      react(),
      tailwindcss(),
      diagnosticsApiPlugin,
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
          enabled: true,
          type: 'classic',
        },
      }),
    ],
    resolve: {
      alias: {
        '@': path.resolve(import.meta.dirname, '.'),
      },
    },
    server: {
      // HMR is disabled in AI Studio via DISABLE_HMR env var.
      // Do not modify—file watching is disabled to prevent flickering during agent edits.
      hmr: process.env.DISABLE_HMR !== 'true',
      // Disable file watching when DISABLE_HMR is true to save CPU during agent edits.
      watch: process.env.DISABLE_HMR === 'true' ? null : {},
      proxy: {
        '/api/nextcloud': {
          target: 'https://cloud.zentryx.bg',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api\/nextcloud/, ''),
          secure: true,
          timeout: 300000,
          proxyTimeout: 300000,
          configure: (proxy) => {
            proxy.on('proxyReq', (proxyReq, req) => {
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
              } else if (req.url && /\.(mp3|flac|m4a|aac|ogg|opus|wav|wma|m4b|alac|aiff)(\?|$)/i.test(req.url)) {
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
              if (req.url && /\.(mp3|flac|m4a|aac|ogg|opus|wav|wma|m4b|alac|aiff)(\?|$)/i.test(req.url)) {
                proxyReq.removeHeader('if-none-match');
                proxyReq.removeHeader('if-modified-since');
              }

              // Log outgoing proxy request to /tmp/proxy.log for settings diagnostics
              try {
                const authPresent = Boolean(proxyReq.getHeader('authorization'));
                const rangeHeader = proxyReq.getHeader('range') || 'none';
                const logLine = `[${new Date().toISOString()}] REQ ${req.method} ${req.url} -> cloud.zentryx.bg${proxyReq.path} (Auth: ${authPresent ? 'PRESENT' : 'MISSING'}, Range: ${rangeHeader})\n`;
                fs.appendFileSync('/tmp/proxy.log', logLine);
              } catch {}
            });

            proxy.on('proxyRes', (proxyRes, req) => {
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
                'Content-Range, Content-Length, Accept-Ranges, Content-Type, ETag, Last-Modified';

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
                const logLine = `[${new Date().toISOString()}] RES ${status} ${req.method} ${req.url} (Type: ${ctype}, Range: ${crange}, Len: ${clen})\n`;
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
              console.warn('[Nextcloud WebDAV Proxy Notice]:', err.message);
              try {
                fs.appendFileSync('/tmp/proxy.log', `[${new Date().toISOString()}] PROXY SOCKET ERROR: ${err.message} on ${req.method} ${req.url}\n`);
              } catch {}
              if (res && typeof res.writeHead === 'function' && !res.headersSent) {
                res.writeHead(502, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Proxy communication error', message: err.message }));
              }
            });
          }
        }
      }
    },
  };
});

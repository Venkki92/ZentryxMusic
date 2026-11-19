import express from 'express';
import path from 'path';
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

const app = express();
const PORT = Number(process.env.PORT || 3000);
const DIST_DIR = path.join(__dirname, 'dist');

app.use('/api/audio-stream', (req, res) => handleAudioStreamRequest(req, res));
app.use('/api/nextcloud', (req, res) => {
  // Restore full /api/nextcloud prefix so handleNextcloudProxyRequest parses path identically
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

app.use(express.static(DIST_DIR, { index: 'index.html' }));

app.use((_req, res) => {
  res.sendFile(path.join(DIST_DIR, 'index.html'));
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Zentryx Music production server listening on http://0.0.0.0:${PORT}`);
});

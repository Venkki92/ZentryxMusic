import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { spawnSync } from 'child_process';
import { fileURLToPath } from 'url';

const ROOT_DIR = process.cwd();
const ANDROID_DIR = path.join(ROOT_DIR, 'android');
const VERSION_PROPS_PATH = path.join(ANDROID_DIR, 'version.properties');
const FINGERPRINT_PATH = path.join(ANDROID_DIR, 'app/build/.apk-source-fingerprint');
const RELEASE_APK_PATH = path.join(ANDROID_DIR, 'app/build/outputs/apk/release/app-release.apk');
const DEBUG_APK_PATH = path.join(ANDROID_DIR, 'app/build/outputs/apk/debug/app-debug.apk');

export function readVersionProps() {
  if (!fs.existsSync(VERSION_PROPS_PATH)) {
    return { versionCode: 1, versionName: '0.0.1' };
  }
  const raw = fs.readFileSync(VERSION_PROPS_PATH, 'utf8');
  let versionCode = 1;
  let versionName = '0.0.1';
  for (const line of raw.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq === -1) continue;
    const key = trimmed.slice(0, eq).trim();
    const val = trimmed.slice(eq + 1).trim();
    if (key === 'VERSION_CODE') {
      const parsed = parseInt(val, 10);
      if (Number.isFinite(parsed) && parsed > 0) versionCode = parsed;
    } else if (key === 'VERSION_NAME' && val) {
      versionName = val;
    }
  }
  return { versionCode, versionName };
}

function writeVersionProps(versionCode, versionName) {
  fs.writeFileSync(
    VERSION_PROPS_PATH,
    `VERSION_CODE=${versionCode}\nVERSION_NAME=${versionName}\n`,
    'utf8'
  );
}

export function incrementPatchVersion(versionName) {
  const match = versionName.trim().match(/^(\d+)\.(\d+)\.(\d+)$/);
  if (!match) {
    throw new Error(`Invalid VERSION_NAME "${versionName}". Expected X.Y.Z semver.`);
  }
  const major = parseInt(match[1], 10);
  const minor = parseInt(match[2], 10);
  const patch = parseInt(match[3], 10) + 1;
  return `${major}.${minor}.${patch}`;
}

function collectSourceFiles(dirPath, out = []) {
  if (!fs.existsSync(dirPath)) return out;
  const entries = fs.readdirSync(dirPath, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name));
  for (const entry of entries) {
    const full = path.join(dirPath, entry.name);
    if (entry.isDirectory()) {
      collectSourceFiles(full, out);
    } else if (entry.isFile()) {
      out.push(full);
    }
  }
  return out;
}

export function computeAndroidSourceFingerprint() {
  const hash = crypto.createHash('sha256');
  const explicitFiles = [
    path.join(ANDROID_DIR, 'build.gradle.kts'),
    path.join(ANDROID_DIR, 'settings.gradle.kts'),
    path.join(ANDROID_DIR, 'gradle.properties'),
    path.join(ANDROID_DIR, 'gradle/libs.versions.toml'),
    path.join(ANDROID_DIR, 'app/build.gradle.kts'),
    path.join(ANDROID_DIR, 'app/proguard-rules.pro'),
  ];
  const srcFiles = collectSourceFiles(path.join(ANDROID_DIR, 'app/src'));
  const allFiles = [...explicitFiles, ...srcFiles];

  for (const file of allFiles) {
    if (!fs.existsSync(file)) continue;
    const rel = path.relative(ANDROID_DIR, file);
    hash.update(rel);
    hash.update('\0');
    hash.update(fs.readFileSync(file));
    hash.update('\0');
  }
  return hash.digest('hex');
}

function main() {
  const args = process.argv.slice(2);
  const bumpPatch = args.includes('--bump-patch');
  const ifChanged = args.includes('--if-changed');
  const setArg = args.find((a) => a.startsWith('--set='));

  const currentFingerprint = computeAndroidSourceFingerprint();
  const apksExist = fs.existsSync(RELEASE_APK_PATH) && fs.existsSync(DEBUG_APK_PATH);
  const savedFingerprint = fs.existsSync(FINGERPRINT_PATH)
    ? fs.readFileSync(FINGERPRINT_PATH, 'utf8').trim()
    : '';

  if (ifChanged && apksExist && savedFingerprint === currentFingerprint) {
    const cur = readVersionProps();
    console.log(
      `[APK] No Android feature/source changes detected; APK v${cur.versionName} (#${cur.versionCode}) is up to date.`
    );
    return;
  }

  const prevProps = readVersionProps();
  let highestBuiltCode = prevProps.versionCode;
  const metaPath = path.join(ANDROID_DIR, 'app/build/outputs/apk/release/output-metadata.json');
  if (fs.existsSync(metaPath)) {
    try {
      const meta = JSON.parse(fs.readFileSync(metaPath, 'utf8'));
      const metaCode = Number(meta?.elements?.[0]?.versionCode);
      if (Number.isFinite(metaCode) && metaCode > highestBuiltCode) {
        highestBuiltCode = metaCode;
      }
    } catch {
      // Ignore malformed metadata
    }
  }
  const prev = { versionCode: highestBuiltCode, versionName: prevProps.versionName };

  if (setArg) {
    const nextName = setArg.slice('--set='.length).trim();
    writeVersionProps(prev.versionCode, nextName);
  } else if (bumpPatch) {
    const nextName = incrementPatchVersion(prev.versionName);
    writeVersionProps(prev.versionCode, nextName);
  } else if (prev.versionCode !== prevProps.versionCode) {
    writeVersionProps(prev.versionCode, prev.versionName);
  }
  console.log(
    `[APK] Starting Gradle build (current v${prev.versionName} #${prev.versionCode}; Gradle will auto-increment VERSION_CODE to #${prev.versionCode + 1})...`
  );

  const javaHome = fs.existsSync('/usr/lib/jvm/java-17-openjdk-amd64')
    ? '/usr/lib/jvm/java-17-openjdk-amd64'
    : process.env.JAVA_HOME || '';
  const androidSdk = fs.existsSync('/opt/android-sdk')
    ? '/opt/android-sdk'
    : process.env.ANDROID_HOME || '';
  const gradleBin = fs.existsSync('/opt/gradle/gradle-8.9/bin/gradle')
    ? '/opt/gradle/gradle-8.9/bin/gradle'
    : 'gradle';

  const env = { ...process.env };
  if (javaHome) env.JAVA_HOME = javaHome;
  if (androidSdk) {
    env.ANDROID_HOME = androidSdk;
    env.ANDROID_SDK_ROOT = androidSdk;
  }
  const extraPaths = [];
  if (fs.existsSync('/opt/gradle/gradle-8.9/bin')) extraPaths.push('/opt/gradle/gradle-8.9/bin');
  if (javaHome) extraPaths.push(path.join(javaHome, 'bin'));
  if (extraPaths.length > 0) {
    env.PATH = `${extraPaths.join(':')}:${env.PATH || ''}`;
  }

  const result = spawnSync(
    gradleBin,
    ['-p', 'android', 'testDebugUnitTest', 'assembleDebug', 'assembleRelease', '--no-daemon', '--console=plain'],
    {
      cwd: ROOT_DIR,
      env,
      stdio: 'inherit',
    }
  );

  if (result.status !== 0) {
    console.error(`[APK] Gradle build failed (exit ${result.status}); restoring version v${prev.versionName} (#${prev.versionCode}).`);
    writeVersionProps(prev.versionCode, prev.versionName);
    process.exit(result.status || 1);
  }

  const built = readVersionProps();
  fs.mkdirSync(path.dirname(FINGERPRINT_PATH), { recursive: true });
  fs.writeFileSync(FINGERPRINT_PATH, `${currentFingerprint}\n`, 'utf8');
  console.log(`[APK] Build complete: v${built.versionName} (#${built.versionCode}) ready to serve.`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}

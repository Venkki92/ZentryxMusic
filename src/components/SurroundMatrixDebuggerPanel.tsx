import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Activity, ChevronDown, ChevronUp, Copy, Check } from 'lucide-react';
import {
  DspSettings,
  SURROUND_MODES,
  SurroundModeId,
  buildSurroundMatrixDiagnostics,
} from '../utils/dspConsole';
import {
  STEREO_SPECTRUM_LABELS,
  StereoSpectrumSnapshot,
} from '../utils/w203Cockpit';

interface SurroundMatrixDebuggerPanelProps {
  accentHex: string;
  dspSettings: DspSettings;
  onSelectMode: (mode: SurroundModeId) => void;
  getStereoSpectrumSnapshot?: () => StereoSpectrumSnapshot | null;
}

const QUICK_DEBUG_MODES: SurroundModeId[] = [
  'off',
  'logic7_surround',
  'concert_hall',
  'dolby_pro_logic_ii',
  'dolby_atmos_virtual',
  'virtual_surround_360',
];

const EMPTY_SPECTRUM: StereoSpectrumSnapshot = {
  leftBands: new Array(16).fill(0),
  rightBands: new Array(16).fill(0),
  midRms: 0,
  sideRms: 0,
  stereoSeparationPercent: 0,
  isActive: false,
  sampleRateHz: 48000,
};

export const SurroundMatrixDebuggerPanel: React.FC<SurroundMatrixDebuggerPanelProps> = ({
  accentHex,
  dspSettings,
  onSelectMode,
  getStereoSpectrumSnapshot,
}) => {
  const [expanded, setExpanded] = useState(true);
  const [showJson, setShowJson] = useState(false);
  const [copiedJson, setCopiedJson] = useState(false);
  const [spectrum, setSpectrum] = useState<StereoSpectrumSnapshot>(EMPTY_SPECTRUM);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  const diagnostics = useMemo(
    () =>
      buildSurroundMatrixDiagnostics(
        `SurroundDebugger(${dspSettings.surroundMode})`,
        dspSettings,
        spectrum.sampleRateHz || 48000
      ),
    [dspSettings, spectrum.sampleRateHz]
  );

  // Poll live post-surround L/R spectrum (~10 Hz) only while expanded
  useEffect(() => {
    if (!expanded || !getStereoSpectrumSnapshot) return;
    let active = true;
    const poll = () => {
      if (!active) return;
      const snap = getStereoSpectrumSnapshot();
      if (snap) setSpectrum(snap);
    };
    poll();
    const timer = window.setInterval(poll, 100);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [expanded, getStereoSpectrumSnapshot]);

  // Draw crisp 16-band paired L (Cyan) vs R (Amber) frequency distribution overlay
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !expanded) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = typeof window !== 'undefined' ? Math.min(2, window.devicePixelRatio || 1) : 1;
    const rect = canvas.getBoundingClientRect();
    const width = Math.max(280, Math.floor(rect.width || 560));
    const height = Math.max(96, Math.floor(rect.height || 108));
    if (canvas.width !== width * dpr || canvas.height !== height * dpr) {
      canvas.width = width * dpr;
      canvas.height = height * dpr;
    }

    ctx.save();
    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, width, height);

    const padLeft = 6;
    const padRight = 6;
    const padTop = 16;
    const padBottom = 16;
    const plotW = width - padLeft - padRight;
    const plotH = height - padTop - padBottom;

    // Horizontal reference grid lines (25%, 50%, 75%)
    ctx.strokeStyle = 'rgba(148, 163, 184, 0.14)';
    ctx.lineWidth = 1;
    for (const frac of [0.25, 0.5, 0.75, 1]) {
      const y = padTop + plotH * (1 - frac);
      ctx.beginPath();
      ctx.moveTo(padLeft, y);
      ctx.lineTo(width - padRight, y);
      ctx.stroke();
    }

    const bands = 16;
    const slotW = plotW / bands;
    const barGap = 1.5;
    const pairGap = Math.max(2, slotW * 0.16);
    const barW = Math.max(2, (slotW - pairGap - barGap) / 2);

    // 2.2 kHz Center Vocal Lock (index 8) & 7.2 kHz Atmos Height (index 12) reference markers
    const markers: Array<{ idx: number; label: string; color: string }> = [
      { idx: 8, label: '2.2k CTR', color: 'rgba(56, 189, 248, 0.7)' },
      { idx: 12, label: '7.2k HT', color: 'rgba(245, 158, 11, 0.75)' },
    ];
    ctx.font = '600 9px ui-monospace, SFMono-Regular, Menlo, monospace';
    ctx.textAlign = 'center';
    for (const m of markers) {
      const mx = padLeft + m.idx * slotW + slotW / 2;
      ctx.save();
      ctx.setLineDash([3, 3]);
      ctx.strokeStyle = m.color;
      ctx.beginPath();
      ctx.moveTo(mx, padTop - 2);
      ctx.lineTo(mx, padTop + plotH);
      ctx.stroke();
      ctx.restore();
      ctx.fillStyle = m.color;
      ctx.fillText(m.label, mx, 10);
    }

    // Paired Left (Cyan #38BDF8) and Right (Amber #F59E0B) bars
    for (let i = 0; i < bands; i++) {
      const lVal = Math.max(0.03, Math.min(1, spectrum.leftBands[i] ?? 0));
      const rVal = Math.max(0.03, Math.min(1, spectrum.rightBands[i] ?? 0));

      const x0 = padLeft + i * slotW + pairGap / 2;
      const lH = lVal * plotH;
      const rH = rVal * plotH;

      ctx.fillStyle = spectrum.isActive ? '#38bdf8' : 'rgba(56, 189, 248, 0.32)';
      ctx.fillRect(x0, padTop + plotH - lH, barW, lH);

      ctx.fillStyle = spectrum.isActive ? '#f59e0b' : 'rgba(245, 158, 11, 0.32)';
      ctx.fillRect(x0 + barW + barGap, padTop + plotH - rH, barW, rH);

      if (i % 2 === 0 || i === 15) {
        ctx.fillStyle = 'rgba(148, 163, 184, 0.72)';
        ctx.fillText(STEREO_SPECTRUM_LABELS[i], padLeft + i * slotW + slotW / 2, height - 3);
      }
    }

    ctx.restore();
  }, [spectrum, expanded]);

  const jsonPayload = useMemo(() => {
    return JSON.stringify(
      {
        ...diagnostics,
        liveStereoSpectrum: {
          isActive: spectrum.isActive,
          stereoSeparationPercent: Number(spectrum.stereoSeparationPercent.toFixed(1)),
          midRms: Number(spectrum.midRms.toFixed(4)),
          sideRms: Number(spectrum.sideRms.toFixed(4)),
        },
      },
      null,
      2
    );
  }, [diagnostics, spectrum]);

  const handleCopyJson = async () => {
    try {
      await navigator.clipboard.writeText(jsonPayload);
      setCopiedJson(true);
      setTimeout(() => setCopiedJson(false), 1800);
    } catch {}
  };

  const isSurroundActive = dspSettings.dspEnabled && !dspSettings.monoFoldDown && dspSettings.surroundMode !== 'off';

  return (
    <div className="bg-[#0b1120] w203-surface-card border border-slate-800/80 rounded-2xl p-4 sm:p-5 shadow-xl space-y-3.5">
      <div className="flex items-center justify-between gap-3">
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className="flex items-center gap-2.5 text-left min-w-0 flex-1"
        >
          <div
            className="w-9 h-9 rounded-xl flex items-center justify-center border shrink-0"
            style={{ backgroundColor: `${accentHex}16`, borderColor: `${accentHex}44` }}
          >
            <Activity className="w-4 h-4" style={{ color: accentHex }} />
          </div>
          <div className="min-w-0">
            <div className="text-sm font-bold text-white flex items-center gap-2 flex-wrap">
              <span>Surround Matrix Debugger &amp; Live L/R Spectrum</span>
            </div>
            <div className="text-[11px] font-mono text-slate-400 tabular-nums truncate">
              {isSurroundActive ? `ACTIVE · ${SURROUND_MODES[dspSettings.surroundMode].shortLabel}` : 'DRY DIRECT STEREO'}
              {' · '}Width {diagnostics.effectiveStereoWidthPercent}%
              {' · '}L−R Spread {Math.round(spectrum.stereoSeparationPercent)}%
            </div>
          </div>
        </button>

        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          aria-label={expanded ? 'Collapse Surround Matrix Debugger' : 'Expand Surround Matrix Debugger'}
          className="min-h-[40px] min-w-[40px] rounded-xl bg-black/50 border border-slate-800 hover:border-slate-600 text-slate-300 hover:text-white flex items-center justify-center shrink-0"
        >
          {expanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
        </button>
      </div>

      {expanded && (
        <div className="space-y-3.5 pt-1 border-t border-slate-800/70">
          {/* Real-Time 16-Band L/R Frequency Distribution Overlay */}
          <div className="rounded-xl bg-black/60 border border-slate-800/90 p-3 space-y-2.5">
            <div className="flex flex-wrap items-center justify-between gap-2 text-[11px] font-mono">
              <div className="flex items-center gap-3 text-slate-300">
                <span className="font-bold text-slate-200">L/R FREQUENCY DISTRIBUTION</span>
                <span className="flex items-center gap-1.5 text-sky-400">
                  <span className="w-2.5 h-2.5 rounded-xs bg-sky-400 inline-block" />L
                </span>
                <span className="flex items-center gap-1.5 text-amber-400">
                  <span className="w-2.5 h-2.5 rounded-xs bg-amber-400 inline-block" />R
                </span>
              </div>
              <span className="text-[10px] text-slate-400 tabular-nums">
                {spectrum.isActive
                  ? `${Math.round(spectrum.sampleRateHz / 1000)} kHz · Mid ${(spectrum.midRms * 100).toFixed(1)} · Side ${(spectrum.sideRms * 100).toFixed(1)}`
                  : 'Play audio to inspect live L/R DSP spectrum'}
              </span>
            </div>

            <canvas
              ref={canvasRef}
              className="w-full h-[110px] block rounded-lg bg-[#050810]"
              aria-label="Real-time stereo Left and Right 16-band frequency distribution"
            />

            {/* Stereo L-R Spread Meter */}
            <div className="flex items-center gap-3">
              <span className="text-[10px] font-mono font-bold text-slate-400 whitespace-nowrap">
                STEREO L−R SPREAD
              </span>
              <div className="flex-1 h-2 rounded-full bg-slate-900 border border-slate-800 overflow-hidden">
                <div
                  className="h-full rounded-full transition-[width] duration-150"
                  style={{
                    width: `${Math.max(2, Math.min(100, spectrum.stereoSeparationPercent))}%`,
                    background: `linear-gradient(90deg, #38bdf8 0%, ${accentHex} 60%, #f59e0b 100%)`,
                  }}
                />
              </div>
              <span className="text-xs font-mono font-bold tabular-nums w-10 text-right" style={{ color: accentHex }}>
                {Math.round(spectrum.stereoSeparationPercent)}%
              </span>
            </div>
          </div>

          {/* Quick Audition Profile Switcher */}
          <div className="flex flex-wrap items-center gap-1.5">
            {QUICK_DEBUG_MODES.map((modeId) => {
              const info = SURROUND_MODES[modeId];
              const isSel = dspSettings.surroundMode === modeId;
              return (
                <button
                  key={modeId}
                  type="button"
                  onClick={() => onSelectMode(modeId)}
                  className={`min-h-[38px] px-3 py-1.5 rounded-xl text-xs font-bold border transition-colors whitespace-nowrap ${
                    isSel
                      ? 'text-black border-transparent'
                      : 'bg-black/50 border-slate-800 text-slate-300 hover:text-white hover:border-slate-600'
                  }`}
                  style={isSel ? { backgroundColor: accentHex } : undefined}
                >
                  {info.shortLabel}
                </button>
              );
            })}
          </div>

          {/* Copyable JSON Parameter Map */}
          <div className="flex items-center justify-between gap-2">
            <button
              type="button"
              onClick={() => setShowJson((v) => !v)}
              className="text-[11px] font-mono text-slate-400 hover:text-white underline-offset-2 hover:underline"
            >
              {showJson ? 'Hide DSP Parameter JSON' : 'Inspect DSP Parameter JSON'}
            </button>
            <button
              type="button"
              onClick={handleCopyJson}
              className="min-h-[36px] px-3 py-1 rounded-lg bg-black/50 border border-slate-800 hover:border-slate-600 text-[11px] font-mono text-slate-200 flex items-center gap-1.5 whitespace-nowrap"
            >
              {copiedJson ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
              <span>{copiedJson ? 'Copied JSON' : 'Copy Debug JSON'}</span>
            </button>
          </div>

          {showJson && (
            <pre className="p-3 rounded-xl bg-black/80 border border-slate-800 text-[11px] font-mono text-slate-300 overflow-x-auto max-h-52">
              {jsonPayload}
            </pre>
          )}
        </div>
      )}
    </div>
  );
};

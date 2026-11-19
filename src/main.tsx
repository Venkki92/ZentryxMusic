import React from 'react';
import {createRoot} from 'react-dom/client';
import App from './App.tsx';
import './index.css';

interface ErrorBoundaryState {
  error: Error | null;
}

class AppErrorBoundary extends React.Component<
  {children: React.ReactNode},
  ErrorBoundaryState
> {
  state: ErrorBoundaryState = {error: null};

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return {error};
  }

  render() {
    if (this.state.error) {
      return (
        <div className="min-h-screen bg-[#020617] text-slate-100 flex items-center justify-center p-6 font-sans">
          <div className="w-full max-w-md rounded-2xl bg-[#0b1120] border border-slate-800 p-6 space-y-4 shadow-2xl">
            <h1 className="text-lg font-bold text-white">Zentryx Music Recovery</h1>
            <p className="text-xs text-slate-400 leading-relaxed">
              The cockpit UI hit an unexpected runtime error:
            </p>
            <pre className="p-3 rounded-xl bg-black/70 border border-red-900/50 text-[11px] text-red-300 overflow-x-auto whitespace-pre-wrap">
              {this.state.error.message}
            </pre>
            <div className="flex items-center gap-2 pt-1">
              <button
                type="button"
                onClick={() => window.location.reload()}
                className="px-4 py-2 rounded-xl text-xs font-bold text-black bg-[#a3e635] cursor-pointer"
              >
                Reload Player
              </button>
              <a
                href="/api/android-apk/release"
                className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-200 bg-slate-800 border border-slate-700"
              >
                Download Android APK
              </a>
            </div>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

createRoot(document.getElementById('root')!).render(
  <AppErrorBoundary>
    <App />
  </AppErrorBoundary>
);

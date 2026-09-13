import { useEffect, useState } from 'react';
import { Download, FileCode, CheckCircle2, Loader2, BookOpen, ChevronRight, Terminal, RefreshCw } from 'lucide-react';

interface WorkspaceExporterProps {
  onClose: () => void;
  isOpen: boolean;
}

export default function WorkspaceExporter({ onClose, isOpen }: WorkspaceExporterProps) {
  const [files, setFiles] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [currentTab, setCurrentTab] = useState<'files'|'guide'>('files');

  const fetchWorkspaceFiles = async () => {
    setLoading(true);
    try {
      const response = await fetch('/api/workspace-files');
      if (response.ok) {
        const data = await response.json();
        setFiles(data.files || []);
      }
    } catch (err) {
      console.error('Failed to query workspace structure:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isOpen) {
      fetchWorkspaceFiles();
    }
  }, [isOpen]);

  const handleDownloadZip = async () => {
    setDownloading(true);
    try {
      // Direct browser-safe downloading
      const link = document.createElement('a');
      link.href = '/api/download-zip';
      link.download = 'our-sanctuary-project.zip';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch (error) {
      console.error('Download compilation error:', error);
    } finally {
      setDownloading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      {/* Backdrop overlay */}
      <div 
        onClick={onClose}
        className="absolute inset-0 bg-[#0d0914]/65 backdrop-blur-md transition-opacity duration-300"
      />

      {/* Exporter drawer */}
      <div className="relative w-full max-w-md h-full bg-[#1b1421]/95 text-white shadow-2xl flex flex-col border-l border-white/10 backdrop-blur-xl animate-slide-left">
        {/* Header */}
        <div className="p-6 border-b border-white/10 flex items-center justify-between">
          <div>
            <h2 className="font-headline-md text-xl font-bold text-[#ffdad8] flex items-center gap-2">
              <Download className="w-5 h-5 text-[#f47c7c]" /> Project Exporter
            </h2>
            <p className="text-xs text-white/50 mt-1">Compile your complete Sanctuary files into a standard ZIP</p>
          </div>
          <button 
            onClick={onClose}
            className="w-8 h-8 rounded-full flex items-center justify-center hover:bg-white/10 text-white/70 hover:text-white transition-colors"
          >
            ✕
          </button>
        </div>

        {/* Tab Selector */}
        <div className="flex border-b border-white/5 px-6">
          <button
            onClick={() => setCurrentTab('files')}
            className={`py-3 px-4 text-sm font-medium border-b-2 transition-all flex items-center gap-1.5 ${
              currentTab === 'files' 
                ? 'border-[#f47c7c] text-[#ffdad8] font-semibold' 
                : 'border-transparent text-white/60 hover:text-white'
            }`}
          >
            <FileCode className="w-4 h-4" /> Codebase Files ({files.length})
          </button>
          <button
            onClick={() => setCurrentTab('guide')}
            className={`py-3 px-4 text-sm font-medium border-b-2 transition-all flex items-center gap-1.5 ${
              currentTab === 'guide' 
                ? 'border-[#f47c7c] text-[#ffdad8] font-semibold' 
                : 'border-transparent text-white/60 hover:text-white'
            }`}
          >
            <BookOpen className="w-4 h-4" /> Quick Run Guide
          </button>
        </div>

        {/* Content Body */}
        <div className="flex-1 overflow-y-auto p-6 scrollbar">
          {currentTab === 'files' ? (
            <div className="space-y-4">
              <div className="bg-black/30 rounded-xl p-4 border border-white/5 space-y-2">
                <div className="flex justify-between items-center text-xs text-white/60">
                  <span>Target Format:</span>
                  <span className="font-mono text-emerald-400 font-semibold">Standard ZIP Package</span>
                </div>
                <div className="flex justify-between items-center text-xs text-white/60">
                  <span>Compression:</span>
                  <span className="font-mono text-white/80">DEFLATE (Fast)</span>
                </div>
                <div className="flex justify-between items-center text-xs text-white/60">
                  <span>Exclusions:</span>
                  <span className="font-mono text-[#f47c7c]/90">node_modules/, dist/, .env</span>
                </div>
              </div>

              <div className="flex justify-between items-center">
                <span className="text-xs text-white/40 tracking-wider uppercase font-semibold">Workspace Inventory</span>
                <button 
                  onClick={fetchWorkspaceFiles} 
                  disabled={loading}
                  className="p-1 rounded hover:bg-white/10 text-white/60 hover:text-white transition-all self-end flex items-center gap-1 text-[11px]"
                >
                  <RefreshCw className={`w-3 h-3 ${loading ? 'animate-spin' : ''}`} /> Refresh list
                </button>
              </div>

              {loading ? (
                <div className="flex flex-col items-center justify-center py-20 text-white/40 gap-3">
                  <Loader2 className="w-8 h-8 animate-spin text-[#f47c7c]" />
                  <span className="text-sm">Scanning codebase structure...</span>
                </div>
              ) : (
                <div className="space-y-2 max-h-[300px] overflow-y-auto pr-1 bg-black/20 rounded-xl p-3 border border-white/5 font-mono text-[11px] text-white/70">
                  {files.length === 0 ? (
                    <div className="text-center py-4 text-white/30">No files found.</div>
                  ) : (
                    files.map((f, i) => (
                      <div key={i} className="flex items-center gap-2 py-1 border-b border-white/5 last:border-b-0 hover:bg-white/5 px-1.5 rounded transition-all">
                        <ChevronRight className="w-3 h-3 text-white/30 shrink-0" />
                        <span className="truncate">{f}</span>
                        <CheckCircle2 className="w-3 h-3 text-emerald-500 ml-auto shrink-0" />
                      </div>
                    ))
                  )}
                </div>
              )}
            </div>
          ) : (
            <div className="space-y-6">
              <div className="space-y-3">
                <h3 className="text-sm font-semibold text-[#ffdad8] flex items-center gap-1.5">
                  <Terminal className="w-4 h-4 text-[#f47c7c]" /> Running Local Dev Session
                </h3>
                <p className="text-xs text-white/70 leading-relaxed">
                  After downloading and zipping the folder, set up your workspace locally:
                </p>
                <div className="bg-black/40 rounded-xl p-4 font-mono text-xs text-[#a1ffbe] border border-white/5 space-y-1.5">
                  <div><span className="text-white/40"># 1. Open project directory</span></div>
                  <div>cd our-sanctuary-project</div>
                  <div className="pt-2"><span className="text-white/40"># 2. Install dev dependencies</span></div>
                  <div>npm install</div>
                  <div className="pt-2"><span className="text-white/40"># 3. Add secrets to environment</span></div>
                  <div>cp .env.example .env</div>
                  <div><span className="text-white/30"># Add your real GEMINI_API_KEY inside .env</span></div>
                  <div className="pt-2"><span className="text-white/40"># 4. Fire up full-stack Node server</span></div>
                  <div>npm run dev</div>
                </div>
              </div>

              <div className="bg-white/5 rounded-xl p-4 border border-white/5 space-y-2 text-xs text-white/80 leading-relaxed">
                <span className="font-semibold text-[#f47c7c] block">Production Compile Steps:</span>
                <p>To compile into CJS node bundle ready to deploy on Cloud Run or Vercel, run:</p>
                <div className="bg-black/20 font-mono p-2 rounded text-[#ffddb6] mt-1 text-[11px]">
                  npm run build
                </div>
                <p className="mt-1">
                  This builds standard Vite React assets on `dist/` and compiles server.ts cleanly.
                </p>
              </div>
            </div>
          )}
        </div>

        {/* Footer Actions */}
        <div className="p-6 border-t border-white/10 bg-black/10">
          <button
            onClick={handleDownloadZip}
            disabled={downloading}
            className="w-full bg-gradient-to-tr from-[#932731] via-[#a13d3f] to-[#f47c7c] text-white p-4 rounded-xl flex items-center justify-center gap-2 font-semibold shadow-lg hover:brightness-110 active:scale-[0.98] transition-all disabled:opacity-50"
          >
            {downloading ? (
              <>
                <Loader2 className="w-5 h-5 animate-spin" /> Compiling Bundle ZIP...
              </>
            ) : (
              <>
                <Download className="w-5 h-5 animate-bounce" /> Compile & Download Project ZIP
              </>
            )}
          </button>
          <span className="text-[10px] text-center block text-white/40 mt-3">
            Pure JavaScript dynamic compression - compiles workspace live assets.
          </span>
        </div>
      </div>

      <style>{`
        @keyframes slide-left {
          from { transform: translateX(100%); }
          to { transform: translateX(0); }
        }
        .animate-slide-left {
          animation: slide-left 0.38s cubic-bezier(0.16, 1, 0.3, 1) both;
        }
      `}</style>
    </div>
  );
}

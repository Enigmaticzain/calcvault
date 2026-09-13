import React, { useState, useRef } from 'react';
import { Image, Upload, Plus, X, Heart } from 'lucide-react';

interface MemoryBoxProps {
  onPublishMemory: (imgBase64: string, caption: string) => void;
}

export default function MemoryBox({ onPublishMemory }: MemoryBoxProps) {
  const [dragActive, setDragActive] = useState(false);
  const [preview, setPreview] = useState<string | null>(null);
  const [caption, setCaption] = useState('');
  const [isExpanded, setIsExpanded] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleDrag = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === "dragenter" || e.type === "dragover") {
      setDragActive(true);
    } else if (e.type === "dragleave") {
      setDragActive(false);
    }
  };

  const processFile = (file: File) => {
    if (!file.type.startsWith('image/')) {
      alert('Sanctuary only holds image memories.');
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        setPreview(reader.result);
      }
    };
    reader.readAsDataURL(file);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      processFile(e.dataTransfer.files[0]);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    e.preventDefault();
    if (e.target.files && e.target.files[0]) {
      processFile(e.target.files[0]);
    }
  };

  const handlePickClick = () => {
    fileInputRef.current?.click();
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!preview) return;
    onPublishMemory(preview, caption || 'Our sweet memory');
    setPreview(null);
    setCaption('');
    setIsExpanded(false);
  };

  return (
    <div className="bg-white/5 backdrop-blur-xl rounded-2xl border border-white/10 p-4 transition-all">
      {!isExpanded ? (
        <button
          onClick={() => setIsExpanded(true)}
          className="w-full flex items-center justify-between transition-colors hover:text-[#ffdad8] text-white/80 text-sm font-medium"
        >
          <span className="flex items-center gap-2">
            <Image className="w-4 h-4 text-[#f47c7c]" /> Pin a physical memory card to chat...
          </span>
          <Plus className="w-4 h-4" />
        </button>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4 animate-fade-in">
          <div className="flex items-center justify-between border-b border-white/5 pb-2">
            <h4 className="text-sm font-semibold text-[#ffdad8] flex items-center gap-1.5">
              <Upload className="w-4 h-4 text-[#f47c7c]" /> Polaroid Memory Creator
            </h4>
            <button
              type="button"
              onClick={() => {
                setIsExpanded(false);
                setPreview(null);
              }}
              className="text-white/40 hover:text-white transition-colors text-xs"
            >
              Cancel
            </button>
          </div>

          {/* Drag & Drop uploader area */}
          {!preview ? (
            <div
              onDragEnter={handleDrag}
              onDragOver={handleDrag}
              onDragLeave={handleDrag}
              onDrop={handleDrop}
              onClick={handlePickClick}
              className={`border-2 border-dashed rounded-xl p-6 text-center cursor-pointer transition-all ${
                dragActive
                  ? 'border-[#f47c7c] bg-[#f47c7c]/5'
                  : 'border-white/10 hover:border-white/25 bg-black/10'
              }`}
            >
              <input
                ref={fileInputRef}
                type="file"
                className="hidden"
                accept="image/*"
                onChange={handleChange}
              />
              <Upload className="w-8 h-8 mx-auto text-white/30 mb-2 animate-pulse" />
              <p className="text-xs font-medium text-white/80">
                Drag & drop private photo here or <span className="text-[#f47c7c] hover:underline">browse files</span>
              </p>
              <p className="text-[10px] text-white/40 mt-1">PNG, JPG or WEBP formats supported</p>
            </div>
          ) : (
            <div className="relative bg-black/20 rounded-xl p-3 flex flex-col items-center">
              {/* Polaroid mock */}
              <div className="bg-white p-2.5 pb-6 shadow-lg rotate-1 hover:rotate-0 transition-transform duration-300 w-44">
                <div className="relative group overflow-hidden">
                  <img
                    src={preview}
                    alt="Uploaded Memory"
                    className="w-full h-32 object-cover rounded-md"
                  />
                  <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center pointer-events-none">
                    <Heart className="w-6 h-6 text-white fill-current animate-beat" />
                  </div>
                </div>
                <div className="h-4 mt-2 bg-gray-100 rounded animate-pulse" style={{ display: caption ? 'none' : 'block' }}></div>
                <p className="text-[10px] text-center font-medium pr-1 text-gray-700 truncate mt-2 font-sans select-none" style={{ display: caption ? 'block' : 'none' }}>
                  {caption}
                </p>
              </div>

              {/* Close/Reset Button */}
              <button
                type="button"
                onClick={() => setPreview(null)}
                className="absolute top-2 right-2 w-7 h-7 rounded-full bg-black/60 hover:bg-black/80 flex items-center justify-center text-white/80 hover:text-white transition-colors"
                title="Discard picture"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          )}

          {/* Caption Input */}
          <div className="space-y-2">
            <label className="text-[11px] uppercase tracking-wider text-white/40 font-semibold block">Label / Diary Note</label>
            <input
              type="text"
              value={caption}
              onChange={(e) => setCaption(e.target.value)}
              placeholder="e.g. Hiking the ridges together..."
              className="w-full bg-black/20 border border-white/10 rounded-xl px-3 py-2 text-xs text-white placeholder:text-white/30 focus:outline-none focus:ring-1 focus:ring-[#f47c7c]/40 focus:border-transparent transition-all"
            />
          </div>

          {/* Submit button */}
          <button
            type="submit"
            disabled={!preview}
            className="w-full bg-gradient-to-tr from-[#932731] to-[#a13d3f] disabled:opacity-40 text-xs font-semibold py-2.5 rounded-xl text-white transition-all hover:brightness-110 active:scale-[0.98]"
          >
            Pin styled memory polaroid to Sanctuary timeline
          </button>
        </form>
      )}
    </div>
  );
}

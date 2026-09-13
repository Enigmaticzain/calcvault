import { useEffect, useRef } from 'react';

export default function BeachWaves() {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animationId: number;
    let width = (canvas.width = window.innerWidth);
    let height = (canvas.height = window.innerHeight);

    // Glimmering amber particles
    interface SunsetParticle {
      x: number;
      y: number;
      size: number;
      speedY: number;
      speedX: number;
      opacity: number;
    }

    const particles: SunsetParticle[] = [];
    const numParticles = 20;

    for (let i = 0; i < numParticles; i++) {
      particles.push({
        x: Math.random() * width,
        y: height * 0.4 + Math.random() * (height * 0.6),
        size: Math.random() * 2 + 1,
        speedY: -(Math.random() * 0.4 + 0.1),
        speedX: (Math.random() - 0.5) * 0.2,
        opacity: Math.random() * 0.4 + 0.2,
      });
    }

    const handleResize = () => {
      if (!canvas) return;
      width = canvas.width = window.innerWidth;
      height = canvas.height = window.innerHeight;
    };
    window.addEventListener('resize', handleResize);

    // Warm wave phase
    let wavePhase = 0;

    const render = () => {
      ctx.clearRect(0, 0, width, height);

      // Translucent atmospheric glow of sunset gold
      ctx.fillStyle = 'rgba(29, 14, 18, 0.25)';
      ctx.fillRect(0, 0, width, height);

      // Render glowing floating sunset glimmers
      for (const p of particles) {
        ctx.fillStyle = `rgba(255, 190, 110, ${p.opacity})`;
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
        ctx.fill();

        // Update particle
        p.y += p.speedY;
        p.x += p.speedX;
        p.opacity -= 0.002;

        if (p.y < height * 0.3 || p.opacity <= 0) {
          p.x = Math.random() * width;
          p.y = height * 0.7 + Math.random() * (height * 0.3);
          p.opacity = Math.random() * 0.5 + 0.2;
        }
      }

      // Draw subtle animated glowing tide reflections at bottom
      wavePhase += 0.008;
      const waveGradient = ctx.createLinearGradient(0, height * 0.85, 0, height);
      waveGradient.addColorStop(0, 'rgba(255, 150, 80, 0)');
      waveGradient.addColorStop(0.5, `rgba(255, 150, 80, ${0.08 + Math.sin(wavePhase) * 0.03})`);
      waveGradient.addColorStop(1, 'rgba(255, 150, 80, 0)');

      ctx.fillStyle = waveGradient;
      ctx.beginPath();
      // Wave curve representing relaxing seashore tide roll
      ctx.moveTo(0, height);
      for (let x = 0; x <= width; x += 10) {
        const waveY = height * 0.90 + Math.sin(wavePhase + x * 0.005) * 8;
        ctx.lineTo(x, waveY);
      }
      ctx.lineTo(width, height);
      ctx.closePath();
      ctx.fill();

      animationId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animationId);
      window.removeEventListener('resize', handleResize);
    };
  }, []);

  return (
    <div className="absolute inset-0 w-full h-full z-0 overflow-hidden select-none pointer-events-none bg-[#1d0e12]">
      {/* Dynamic scenic background illustration of beach couple sunset */}
      <img
        src="/src/assets/images/couple_beach_sunset_1780394336903.png"
        alt="Romantic Beach Sunset Cuddle"
        referrerPolicy="no-referrer"
        className="absolute inset-0 w-full h-full object-cover opacity-85 scale-102 filter transition-opacity duration-1000"
      />
      <canvas
        id="beach-canvas"
        ref={canvasRef}
        className="absolute inset-0 w-full h-full object-cover z-10 pointer-events-none transition-opacity duration-1000"
      />
    </div>
  );
}

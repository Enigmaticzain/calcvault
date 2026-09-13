import { useEffect, useRef } from 'react';

export default function SnowForest() {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animationId: number;
    let width = (canvas.width = window.innerWidth);
    let height = (canvas.height = window.innerHeight);

    // Snowy winter particles
    interface SnowFlake {
      x: number;
      y: number;
      size: number;
      speedY: number;
      speedX: number;
      opacity: number;
      driftPhase: number;
    }

    const flakes: SnowFlake[] = [];
    const numFlakes = 35;

    for (let i = 0; i < numFlakes; i++) {
      flakes.push({
        x: Math.random() * width,
        y: Math.random() * height,
        size: Math.random() * 2.5 + 1.2,
        speedY: Math.random() * 0.5 + 0.3,
        speedX: (Math.random() - 0.5) * 0.2,
        opacity: Math.random() * 0.6 + 0.2,
        driftPhase: Math.random() * Math.PI * 2,
      });
    }

    const handleResize = () => {
      if (!canvas) return;
      width = canvas.width = window.innerWidth;
      height = canvas.height = window.innerHeight;
    };
    window.addEventListener('resize', handleResize);

    const render = () => {
      ctx.clearRect(0, 0, width, height);

      // Deep atmospheric icy warm overlay to make the room text highly legible
      ctx.fillStyle = 'rgba(16, 21, 35, 0.35)';
      ctx.fillRect(0, 0, width, height);

      // Render snowflakes gently falling
      for (const f of flakes) {
        ctx.fillStyle = `rgba(255, 255, 255, ${f.opacity})`;
        ctx.beginPath();
        // Soft rounded snowy points
        ctx.arc(f.x, f.y, f.size, 0, Math.PI * 2);
        ctx.fill();

        // Update snowflake drift values
        f.y += f.speedY;
        f.driftPhase += 0.01;
        f.x += f.speedX + Math.sin(f.driftPhase) * 0.15; // horizontal calm sway

        if (f.y > height) {
          f.y = -10;
          f.x = Math.random() * width;
        }
      }

      animationId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animationId);
      window.removeEventListener('resize', handleResize);
    };
  }, []);

  return (
    <div className="absolute inset-0 w-full h-full z-0 overflow-hidden select-none pointer-events-none bg-[#101523]">
      {/* Dynamic scenic background illustration of snow forest couple */}
      <img
        src="/src/assets/images/couple_peaceful_snow_1780394355517.png"
        alt="Cozy Hug in Snow Forest"
        referrerPolicy="no-referrer"
        className="absolute inset-0 w-full h-full object-cover opacity-85 scale-102 filter transition-opacity duration-1000"
      />
      <canvas
        id="snow-canvas"
        ref={canvasRef}
        className="absolute inset-0 w-full h-full object-cover z-10 pointer-events-none transition-opacity duration-1000"
      />
    </div>
  );
}

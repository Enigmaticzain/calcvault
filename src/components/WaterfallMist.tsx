import { useEffect, useRef } from 'react';

export default function WaterfallMist() {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animationId: number;
    let width = (canvas.width = window.innerWidth);
    let height = (canvas.height = window.innerHeight);

    // Rising atmospheric water mist droplets
    interface MistDroplet {
      x: number;
      y: number;
      radius: number;
      speedY: number;
      speedX: number;
      opacity: number;
      maxOpacity: number;
    }

    const mist: MistDroplet[] = [];
    const numDroplets = 40;

    for (let i = 0; i < numDroplets; i++) {
      mist.push({
        x: Math.random() * width,
        y: height * 0.4 + Math.random() * (height * 0.6),
        radius: Math.random() * 12 + 4,
        speedY: -(Math.random() * 0.5 + 0.2),
        speedX: (Math.random() - 0.5) * 0.4,
        opacity: 0,
        maxOpacity: Math.random() * 0.15 + 0.05,
      });
    }

    const handleResize = () => {
      if (!canvas) return;
      width = canvas.width = window.innerWidth;
      height = canvas.height = window.innerHeight;
    };
    window.addEventListener('resize', handleResize);

    // Sunset ray pulse phase
    let sunPhase = 0;

    const render = () => {
      ctx.clearRect(0, 0, width, height);

      // Nature-themed translucent deep green/sienna overlay
      ctx.fillStyle = 'rgba(12, 19, 16, 0.3)';
      ctx.fillRect(0, 0, width, height);

      // Render gentle misty rising clouds
      for (const d of mist) {
        // Draw soft fuzzy radial circle represent steam/mist
        const gradient = ctx.createRadialGradient(d.x, d.y, 0, d.x, d.y, d.radius);
        gradient.addColorStop(0, `rgba(235, 245, 255, ${d.opacity})`);
        gradient.addColorStop(1, 'rgba(235, 245, 255, 0)');

        ctx.fillStyle = gradient;
        ctx.beginPath();
        ctx.arc(d.x, d.y, d.radius, 0, Math.PI * 2);
        ctx.fill();

        // Update position
        d.y += d.speedY;
        d.x += d.speedX;

        // Fade in initially, then fade out as they rise
        if (d.y > height * 0.7) {
          d.opacity = Math.min(d.maxOpacity, d.opacity + 0.003);
        } else {
          d.opacity -= 0.001;
        }

        // Reset
        if (d.y < height * 0.2 || d.opacity <= 0) {
          d.x = Math.random() * width;
          d.y = height * 0.9 + Math.random() * (height * 0.1);
          d.opacity = 0;
        }
      }

      // Render warm sunset light shaft rays coming from top right
      sunPhase += 0.005;
      const rayAlpha = 0.06 + Math.sin(sunPhase) * 0.02;
      const rayGradient = ctx.createRadialGradient(width, 0, 10, width, 0, width * 1.1);
      rayGradient.addColorStop(0, `rgba(255, 220, 160, ${rayAlpha * 1.5})`);
      rayGradient.addColorStop(0.5, `rgba(255, 200, 130, ${rayAlpha})`);
      rayGradient.addColorStop(1, 'rgba(0, 0, 0, 0)');

      ctx.fillStyle = rayGradient;
      ctx.fillRect(0, 0, width, height);

      animationId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animationId);
      window.removeEventListener('resize', handleResize);
    };
  }, []);

  return (
    <div className="absolute inset-0 w-full h-full z-0 overflow-hidden select-none pointer-events-none bg-[#0c1310]">
      {/* Dynamic scenic background illustration of couple in front of waterfall */}
      <img
        src="/src/assets/images/couple_waterfall_mist_1780394387327.png"
        alt="Standing Close in Front of Waterfall"
        referrerPolicy="no-referrer"
        className="absolute inset-0 w-full h-full object-cover opacity-85 scale-102 filter transition-opacity duration-1000"
      />
      <canvas
        id="waterfall-canvas"
        ref={canvasRef}
        className="absolute inset-0 w-full h-full object-cover z-10 pointer-events-none transition-opacity duration-1000"
      />
    </div>
  );
}

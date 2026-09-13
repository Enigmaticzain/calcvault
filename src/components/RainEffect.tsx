import { useEffect, useRef } from 'react';

export default function RainEffect() {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animationId: number;
    let width = (canvas.width = window.innerWidth);
    let height = (canvas.height = window.innerHeight);

    // Rain drop array
    const drops: Array<{
      x: number;
      y: number;
      length: number;
      speed: number;
      opacity: number;
    }> = [];

    // Window sliders (raindrops sliding down slowly on glass)
    const glassDrops: Array<{
      x: number;
      y: number;
      size: number;
      speedY: number;
      opacity: number;
    }> = [];

    // Initialize falling rain drops
    const numDrops = 60;
    for (let i = 0; i < numDrops; i++) {
      drops.push({
        x: Math.random() * width,
        y: Math.random() * height - height,
        length: Math.random() * 20 + 15,
        speed: Math.random() * 8 + 12,
        opacity: Math.random() * 0.2 + 0.1,
      });
    }

    // Initialize slow sliding glass drops
    const numGlassDrops = 25;
    for (let i = 0; i < numGlassDrops; i++) {
      glassDrops.push({
        x: Math.random() * width,
        y: Math.random() * height,
        size: Math.random() * 2 + 1,
        speedY: Math.random() * 0.5 + 0.2,
        opacity: Math.random() * 0.4 + 0.2,
      });
    }

    const handleResize = () => {
      if (!canvas) return;
      width = canvas.width = window.innerWidth;
      height = canvas.height = window.innerHeight;
    };
    window.addEventListener('resize', handleResize);

    const render = () => {
      // Warm dark atmospheric base gradient representing warm interior with outdoor storm
      ctx.clearRect(0, 0, width, height);

      // Translucent atmospheric overlay to blend illustration with the cozy interface
      ctx.fillStyle = 'rgba(21, 17, 26, 0.4)';
      ctx.fillRect(0, 0, width, height);

      // Draw fast outdoor falling rain
      ctx.strokeStyle = 'rgba(230, 240, 255, 0.35)';
      ctx.lineWidth = 1;
      for (const d of drops) {
        ctx.beginPath();
        ctx.strokeStyle = `rgba(200, 220, 255, ${d.opacity})`;
        ctx.moveTo(d.x, d.y);
        ctx.lineTo(d.x + 1.5, d.y + d.length);
        ctx.stroke();

        // Update positions
        d.y += d.speed;
        d.x += 0.2; // slight wind slant
        if (d.y > height) {
          d.y = -d.length;
          d.x = Math.random() * width;
        }
      }

      // Draw slow window sliding rain drops (the window pane effect)
      for (const gd of glassDrops) {
        ctx.beginPath();
        const rainGlow = ctx.createRadialGradient(
          gd.x,
          gd.y,
          0,
          gd.x,
          gd.y,
          gd.size * 2
        );
        rainGlow.addColorStop(0, `rgba(255, 255, 255, ${gd.opacity * 1.5})`);
        rainGlow.addColorStop(0.5, `rgba(180, 200, 230, ${gd.opacity * 0.8})`);
        rainGlow.addColorStop(1, 'rgba(255, 255, 255, 0)');
        
        ctx.fillStyle = rainGlow;
        ctx.arc(gd.x, gd.y, gd.size, 0, Math.PI * 2);
        ctx.fill();

        // Draw small faint trail behind the water droplets sliding down
        ctx.strokeStyle = `rgba(255, 255, 255, ${gd.opacity * 0.15})`;
        ctx.lineWidth = 0.5;
        ctx.beginPath();
        ctx.moveTo(gd.x, gd.y);
        ctx.lineTo(gd.x, gd.y - gd.size * 5);
        ctx.stroke();

        // Update positions
        gd.y += gd.speedY;
        // Occasionally wander left/right slightly
        gd.x += Math.sin(gd.y * 0.05) * 0.08;

        if (gd.y > height) {
          gd.y = -gd.size * 2;
          gd.x = Math.random() * width;
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
    <div className="absolute inset-0 w-full h-full z-0 overflow-hidden select-none pointer-events-none bg-[#15111a]">
      {/* Immediatly render the stunning rainy cafe couple illustration backdrop */}
      <img 
        src="https://lh3.googleusercontent.com/aida-public/AB6AXuCiqM2Vu9n-BpszTTpB_mC5nb-qd2PIFWNAYSBG2DeduFOiCek3LI23n70lTYCufKtJUCRZQdR1xbhZi3ozOjlyKPAUWL5G-OHtoE6WHSagIPKDuQaGpgNP5XDYiDU3sc8dJlFiZyWLVmitbwdiPIgo8n6zano-B3r6KQAJnTYnk1rrOIWWmUKbE98fA7NvlzhhAND5erxkIM9UQfoK-pN87M3zYCY3iQ1g0hhH1QZFJ98RHbkOw07-I1lVHTv7LQjzSHF497R4Jd0" 
        alt="Cozy Rainy Cafe Couple"
        referrerPolicy="no-referrer"
        className="absolute inset-0 w-full h-full object-cover opacity-85 scale-102 filter transition-opacity duration-1000"
      />
      <canvas
        id="rain-canvas"
        ref={canvasRef}
        className="absolute inset-0 w-full h-full object-cover z-10 pointer-events-none transition-opacity duration-1000"
      />
    </div>
  );
}

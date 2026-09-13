import { useEffect, useRef } from 'react';

export default function FireplaceGlow() {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animationId: number;
    let width = (canvas.width = window.innerWidth);
    let height = (canvas.height = window.innerHeight);

    // Warm hearth ember sparkles rising
    interface Ember {
      x: number;
      y: number;
      size: number;
      speedY: number;
      speedX: number;
      opacity: number;
      oscPhase: number;
    }

    const embers: Ember[] = [];
    const numEmbers = 25;

    for (let i = 0; i < numEmbers; i++) {
      embers.push({
        x: Math.random() * width,
        y: height * 0.5 + Math.random() * (height * 0.5),
        size: Math.random() * 2 + 1,
        speedY: -(Math.random() * 0.6 + 0.2),
        speedX: (Math.random() - 0.5) * 0.3,
        opacity: Math.random() * 0.5 + 0.3,
        oscPhase: Math.random() * Math.PI * 2,
      });
    }

    const handleResize = () => {
      if (!canvas) return;
      width = canvas.width = window.innerWidth;
      height = canvas.height = window.innerHeight;
    };
    window.addEventListener('resize', handleResize);

    // Cozy glow phase
    let glowFactor = 0;

    const render = () => {
      ctx.clearRect(0, 0, width, height);

      // Warm amber base gradient simulating room firelight glow shadows
      glowFactor += 0.012;
      const pulseGlow = 0.20 + Math.sin(glowFactor) * 0.08;

      ctx.fillStyle = `rgba(32, 21, 23, ${pulseGlow + 0.15})`;
      ctx.fillRect(0, 0, width, height);

      // Warm radial glow leaking frombottom right/fireplace location
      const hearthX = width * 0.85;
      const hearthY = height * 0.85;
      const radialGlow = ctx.createRadialGradient(hearthX, hearthY, 20, hearthX, hearthY, width * 0.6);
      radialGlow.addColorStop(0, `rgba(255, 110, 50, ${pulseGlow * 0.4})`);
      radialGlow.addColorStop(0.5, `rgba(180, 70, 30, ${pulseGlow * 0.1})`);
      radialGlow.addColorStop(1, 'rgba(0, 0, 0, 0)');

      ctx.fillStyle = radialGlow;
      ctx.fillRect(0, 0, width, height);

      // Render drifting burning sparks rising upwards
      for (const e of embers) {
        ctx.fillStyle = `rgba(255, 135, 60, ${e.opacity})`;
        ctx.shadowBlur = 4;
        ctx.shadowColor = '#ff6a00';
        ctx.beginPath();
        ctx.arc(e.x, e.y, e.size, 0, Math.PI * 2);
        ctx.fill();

        // Update ember position
        e.y += e.speedY;
        e.oscPhase += 0.01;
        e.x += e.speedX + Math.sin(e.oscPhase) * 0.2;
        e.opacity -= 0.0015;

        // Reset once particles leave target or fade away
        if (e.y < height * 0.1 || e.opacity <= 0) {
          e.x = Math.random() * width;
          e.y = height * 0.6 + Math.random() * (height * 0.4);
          e.opacity = Math.random() * 0.6 + 0.3;
        }
      }

      ctx.shadowBlur = 0; // reset shadow for next components

      animationId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animationId);
      window.removeEventListener('resize', handleResize);
    };
  }, []);

  return (
    <div className="absolute inset-0 w-full h-full z-0 overflow-hidden select-none pointer-events-none bg-[#201517]">
      {/* Dynamic scenic background illustration of couple cuddling on couch */}
      <img
        src="/src/assets/images/couple_cozy_couch_1780394372208.png"
        alt="Cozy Couch cuddle in Room"
        referrerPolicy="no-referrer"
        className="absolute inset-0 w-full h-full object-cover opacity-85 scale-102 filter transition-opacity duration-1000"
      />
      <canvas
        id="couch-canvas"
        ref={canvasRef}
        className="absolute inset-0 w-full h-full object-cover z-10 pointer-events-none transition-opacity duration-1000"
      />
    </div>
  );
}

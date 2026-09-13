import { useEffect, useRef } from 'react';

export default function CosmicField() {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animationId: number;
    let width = (canvas.width = window.innerWidth);
    let height = (canvas.height = window.innerHeight);

    // Star data
    const stars: Array<{
      x: number;
      y: number;
      size: number;
      alpha: number;
      speed: number;
      increasing: boolean;
    }> = [];

    const shootingStars: Array<{
      x: number;
      y: number;
      length: number;
      speed: number;
      alpha: number;
      vx: number;
      vy: number;
    }> = [];

    // Initialize stars
    const numStars = 80;
    for (let i = 0; i < numStars; i++) {
      stars.push({
        x: Math.random() * width,
        y: Math.random() * height,
        size: Math.random() * 1.5 + 0.5,
        alpha: Math.random(),
        speed: 0.005 + Math.random() * 0.015,
        increasing: Math.random() > 0.5,
      });
    }

    const triggerShootingStar = () => {
      if (shootingStars.length < 2) {
        shootingStars.push({
          x: Math.random() * width * 0.8,
          y: Math.random() * height * 0.4,
          length: Math.random() * 80 + 40,
          speed: Math.random() * 12 + 8,
          alpha: 1,
          vx: Math.random() * 3 + 4,
          vy: Math.random() * 2 + 2,
        });
      }
    };

    // Periodically spawn shooting stars
    const interval = setInterval(triggerShootingStar, 8000);

    const handleResize = () => {
      if (!canvas) return;
      width = canvas.width = window.innerWidth;
      height = canvas.height = window.innerHeight;
    };
    window.addEventListener('resize', handleResize);

    const render = () => {
      ctx.clearRect(0, 0, width, height);

      // Semi-transparent atmospheric overlay to blend illustration with the cozy interface
      ctx.fillStyle = 'rgba(10, 5, 22, 0.35)';
      ctx.fillRect(0, 0, width, height);

      // Draw stars
      for (const s of stars) {
        if (s.increasing) {
          s.alpha += s.speed;
          if (s.alpha >= 0.95) s.increasing = false;
        } else {
          s.alpha -= s.speed;
          if (s.alpha <= 0.15) s.increasing = true;
        }

        ctx.fillStyle = `rgba(255, 255, 240, ${s.alpha * 0.95})`;
        ctx.beginPath();
        ctx.arc(s.x, s.y, s.size, 0, Math.PI * 2);
        ctx.fill();

        // Extra flare on brightest stars
        if (s.size > 1.4 && s.alpha > 0.75) {
          ctx.strokeStyle = `rgba(255, 255, 255, ${s.alpha * 0.35})`;
          ctx.lineWidth = 0.5;
          ctx.beginPath();
          ctx.moveTo(s.x - 5, s.y);
          ctx.lineTo(s.x + 5, s.y);
          ctx.moveTo(s.x, s.y - 5);
          ctx.lineTo(s.x, s.y + 5);
          ctx.stroke();
        }
      }

      // Draw and update shooting stars
      for (let i = shootingStars.length - 1; i >= 0; i--) {
        const ss = shootingStars[i];
        ss.x += ss.vx;
        ss.y += ss.vy;
        ss.alpha -= 0.015;

        if (ss.alpha <= 0 || ss.x > width || ss.y > height) {
          shootingStars.splice(i, 1);
          continue;
        }

        const trailGrad = ctx.createLinearGradient(
          ss.x,
          ss.y,
          ss.x - ss.vx * 4,
          ss.y - ss.vy * 4
        );
        trailGrad.addColorStop(0, `rgba(255, 235, 210, ${ss.alpha})`);
        trailGrad.addColorStop(1, 'rgba(255, 235, 210, 0)');

        ctx.strokeStyle = trailGrad;
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(ss.x, ss.y);
        ctx.lineTo(ss.x - ss.vx * 6, ss.y - ss.vy * 6);
        ctx.stroke();
      }

      animationId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animationId);
      clearInterval(interval);
      window.removeEventListener('resize', handleResize);
    };
  }, []);

  return (
    <div className="absolute inset-0 w-full h-full z-0 overflow-hidden select-none pointer-events-none bg-[#0a0516]">
      {/* Immediatly render the stunning stargazing couple illustration backdrop */}
      <img 
        src="https://lh3.googleusercontent.com/aida-public/AB6AXuDdODphWYw10Ep9UTTUQrK2eCvG-tptE-vDZgwWYQI_6d4s3VNVH4Ib9wMPDnYSuftAfVmDzWltZZu7TzfrShLfPrh3bsOmmn2tQKyvqiRSFM8B2uj1tf3xsWOVgHXWGuN-6K3b9OT4gZGx06gX7bU8_0w3hAsXHQ9o6SY5cwSU6N_XaInWhQagbADiO2oES_xr1m1W3Lpnr0CdCZuaX6lFy0VC_xwpbXJr_8drcd3uNt5Qz2Pw34b2lH_9_gjcVAIiU3m-DDEM_ek" 
        alt="Couple Stargazing"
        referrerPolicy="no-referrer"
        className="absolute inset-0 w-full h-full object-cover opacity-85 scale-102 filter transition-opacity duration-1000"
      />
      <canvas
        id="cosmic-canvas"
        ref={canvasRef}
        className="absolute inset-0 w-full h-full object-cover z-10 pointer-events-none transition-opacity duration-1000"
      />
    </div>
  );
}

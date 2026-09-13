import { useEffect, useRef } from 'react';

export default function AutumnPark() {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animationId: number;
    let width = (canvas.width = window.innerWidth);
    let height = (canvas.height = window.innerHeight);

    // Drifting leaf data
    interface AutumnLeaf {
      x: number;
      y: number;
      size: number;
      angle: number;
      spinSpeed: number;
      speedX: number;
      speedY: number;
      opacity: number;
      color: string;
    }

    const leaves: AutumnLeaf[] = [];
    const leafColors = [
      '#e07a5f', // warm burnt orange
      '#f2cc8f', // golden mustard
      '#dd8a52', // light sienna
      '#c2593f', // maple crimson
      '#df9f3c', // amber yellow
    ];

    // Initialize drifting leaves
    const numLeaves = 22;
    for (let i = 0; i < numLeaves; i++) {
      leaves.push({
        x: Math.random() * width,
        y: Math.random() * height - height,
        size: Math.random() * 8 + 6,
        angle: Math.random() * Math.PI * 2,
        spinSpeed: (Math.random() - 0.5) * 0.03,
        speedX: Math.random() * 1.2 + 0.4,
        speedY: Math.random() * 0.9 + 0.6,
        opacity: Math.random() * 0.35 + 0.45,
        color: leafColors[Math.floor(Math.random() * leafColors.length)],
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

      // Translucent amber twilight overlay to cozy up the backdrop image
      ctx.fillStyle = 'rgba(29, 21, 23, 0.35)';
      ctx.fillRect(0, 0, width, height);

      // Draw drifting falling leaves
      for (const l of leaves) {
        ctx.save();
        ctx.translate(l.x, l.y);
        ctx.rotate(l.angle);
        ctx.fillStyle = l.color;
        ctx.globalAlpha = l.opacity;

        // Draw simple beautiful stylized leaf leaf-shape vectors
        ctx.beginPath();
        ctx.moveTo(0, -l.size);
        ctx.quadraticCurveTo(l.size * 0.8, -l.size * 0.5, l.size * 0.2, l.size);
        ctx.quadraticCurveTo(-l.size * 0.8, l.size * 0.5, 0, -l.size);
        ctx.closePath();
        ctx.fill();

        // Delicate vein stroke
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.15)';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(0, -l.size);
        ctx.lineTo(0, l.size);
        ctx.stroke();

        ctx.restore();

        // Update values
        l.y += l.speedY;
        l.x += l.speedX + Math.sin(l.y * 0.015) * 0.3; // subtle sway
        l.angle += l.spinSpeed;

        if (l.y > height + 20) {
          l.y = -20;
          l.x = Math.random() * (width - 60);
          l.angle = Math.random() * Math.PI * 2;
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
    <div className="absolute inset-0 w-full h-full z-0 overflow-hidden select-none pointer-events-none bg-[#1d1517]">
      {/* Immediately render the gorgeous couple sitting close on bench illustration */}
      <img
        src="https://lh3.googleusercontent.com/aida-public/AB6AXuC6VCEBof_dvZFXaWHnIyPd7U1txGsK5_kcICnAx46wf2QAoo3z_cum70zxWf3hu30jIhO4womZueA96fVPOsfRwPGuQ6QBgEnzjKArX9XIu5l68f4Yu0pm_XkyjnwBQoGycftdStHLLl-WXQkt9S6MDAASNvDmcYM6TRfEFdQSUVhndbaqhjx3IspWZfsCD_xV1tGvhUJYAbx3eomLAc8Bon782nnYIWfrYEz8c2Fc-J7DS1lGWhosC46CASRIXS7EyZiQWKCepjk"
        alt="Autumn Bench Sunset"
        referrerPolicy="no-referrer"
        className="absolute inset-0 w-full h-full object-cover opacity-85 scale-102 filter transition-opacity duration-1000"
      />
      <canvas
        id="autumn-canvas"
        ref={canvasRef}
        className="absolute inset-0 w-full h-full object-cover z-10 pointer-events-none transition-opacity duration-1000"
      />
    </div>
  );
}

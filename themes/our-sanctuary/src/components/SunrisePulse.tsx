import { motion } from 'motion/react';

export default function SunrisePulse() {
  return (
    <div className="absolute inset-0 z-0 overflow-hidden select-none pointer-events-none bg-[#b876a3] transition-opacity duration-1000">
      {/* Immediatly render the stunning mountain sunrise hugging couple illustration backdrop */}
      <img 
        src="https://lh3.googleusercontent.com/aida-public/AB6AXuBl3Ao3vCXNJFsqBy3TzCsnC6vWmi16bxEzV04KoE0pxCCucCdNhTrGtrOc4D3kXcZ7Xp9qABuOKI_8nvdX6h_sYE1lMk2G3DnO7nl3f5Om1esNRrD5p427ykVZg_s9jmnFTxblLDGo1AyBPEsT6lYC7RfRZ-_Aildjfa_VcEkMOJooq5h3Gfn5wVoh7PJslXVMF4Lo7KLE7dY2Scrxy1N7YHyO44JHGAZX1nLVz9CKZRgnztnSi3yg21awyMGDUzLew5crCFekHNM" 
        alt="Mountain Sunrise Embrace"
        referrerPolicy="no-referrer"
        className="absolute inset-0 w-full h-full object-cover opacity-85 scale-102 filter transition-opacity duration-1000"
      />

      {/* Dynamic warm sky gradient overlay */}
      <div 
        className="absolute inset-0 bg-gradient-to-t from-[#ffcbd6]/20 via-[#f7b097]/15 to-[#b876a3]/20 mix-blend-color-dodge opacity-80 pointer-events-none"
      />

      {/* Breathing morning thermal light overlay */}
      <motion.div
        animate={{
          scale: [1, 1.05, 1],
          opacity: [0.25, 0.5, 0.25],
        }}
        transition={{
          duration: 10,
          repeat: Infinity,
          ease: 'easeInOut',
        }}
        className="absolute inset-0 bg-radial-gradient from-[#ffdec4] via-transparent to-transparent pointer-events-none mix-blend-screen"
        style={{
          backgroundPosition: '50% 65%',
        }}
      />

      {/* Floating golden sun dots representing dancing warm dust specks or morning sparks */}
      <div className="absolute inset-0 pointer-events-none overflow-hidden z-10">
        {[...Array(15)].map((_, i) => {
          const delay = i * 1.2;
          const duration = 10 + i * 2.2;
          const left = 5 + (i * 73) % 90;
          return (
            <motion.div
              key={i}
              initial={{ y: '100vh', opacity: 0, scale: Math.random() * 0.7 + 0.3 }}
              animate={{
                y: '-10vh',
                opacity: [0, 0.5, 0.5, 0],
                x: ['0vw', i % 2 === 0 ? '5vw' : '-5vw', '0vw']
              }}
              transition={{
                duration,
                repeat: Infinity,
                delay,
                ease: 'linear'
              }}
              className="absolute w-2.5 h-2.5 rounded-full bg-[#fff0cc] shadow-[0_0_10px_#ffcaa4]"
              style={{
                left: `${left}%`,
                bottom: '0%'
              }}
            />
          );
        })}
      </div>
    </div>
  );
}

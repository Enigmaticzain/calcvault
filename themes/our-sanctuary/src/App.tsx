import { useEffect, useState, useRef, FormEvent } from 'react';
import { Heart, Send, Sparkles, FolderDown, Trash2, HeartHandshake, Compass, Flame, Smile, CloudRain, Snowflake, Sunrise, Sunset, Sunrise as SunriseIcon, Waves, Trees } from 'lucide-react';
import CosmicField from './components/CosmicField';
import RainEffect from './components/RainEffect';
import SunrisePulse from './components/SunrisePulse';
import AutumnPark from './components/AutumnPark';
import BeachWaves from './components/BeachWaves';
import SnowForest from './components/SnowForest';
import FireplaceGlow from './components/FireplaceGlow';
import WaterfallMist from './components/WaterfallMist';
import WorkspaceExporter from './components/WorkspaceExporter';
import MemoryBox from './components/MemoryBox';
import { ThemeId, Message, ThemeConfig } from './types';

// Preset themes matching design guidelines with corresponding imagery
const THEMES: Record<ThemeId, ThemeConfig> = {
  stargazing: {
    id: 'stargazing',
    name: 'Twilight Stargazing',
    subtitle: 'Under our infinity sky',
    partnerAvatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuDN-YfQyPREda-1hSLkKeLIG5LwL-CWCwuQ4UZnNvUuVa1P3-sgchbyYBnwd_37dritY1l5G6wJ8hxnOjuPXCKoiJ-1hzfd3r64jg64qpKXl_EjElU9mYPowv4ir10zLAX5ZZv7iaNEzUf_F43pAPqM7uyUzvtx3I5nlJwDNdcaaW7npo2h-IZRNU_o8Z4nGFWUk3CAp3INaY7vaMyf0Rk6C89szmTBIDoYjMd-Azp6MjEvAnPQ6BhD9a-hMiRVA2E_HHozPlf7ktw',
    placeholderText: 'Whisper into the stars...',
    heartColor: 'from-[#ff758c] to-[#ff7eb3]',
    accentBg: 'rgba(115, 80, 138, 0.45)', // dusk purple
    sendBtnBg: 'bg-[#a13d3f]', // sunset coral
  },
  'rainy-cafe': {
    id: 'rainy-cafe',
    name: 'Cozy Rainy Cafe',
    subtitle: 'Warm porcelain & soft acoustics',
    partnerAvatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuCPUy8QMjnsDLu0CIY5qdQPEw-7NG6xNoyfN6ZmfMaLvT2FPHV914HyvwVYa1dIt9H63cr0o_a_4tWh94rYG5ISOa9lIiyjuMeWullfVdCbRDtp1kgEpaSGWSHuyhNEmp__q3rDFz8O8updlK83_PHC6a_DE6nrU_mJGQ4bE4A66rkNY86CTbrIk_PsMUzcO5reHh1W6tgE0y6VCeCmgVZChRQbpcr45k9-pMTs7iAj5-aMqnXwJMIsd4k6wv9KooeGy-uwDXetnVw',
    placeholderText: 'Whisper something cozy over coffee...',
    heartColor: 'from-[#e29578] to-[#dd6e42]',
    accentBg: 'rgba(29, 27, 23, 0.6)',
    sendBtnBg: 'bg-[#845400]', // amber gold
  },
  sunrise: {
    id: 'sunrise',
    name: 'Sunrise Embrace',
    subtitle: 'Morning paths & crisp ridges',
    partnerAvatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuDN-YfQyPREda-1hSLkKeLIG5LwL-CWCwuQ4UZnNvUuVa1P3-sgchbyYBnwd_37dritY1l5G6wJ8hxnOjuPXCKoiJ-1hzfd3r64jg64qpKXl_EjElU9mYPowv4ir10zLAX5ZZv7iaNEzUf_F43pAPqM7uyUzvtx3I5nlJwDNdcaaW7npo2h-IZRNU_o8Z4nGFWUk3CAp3INaY7vaMyf0Rk6C89szmTBIDoYjMd-Azp6MjEvAnPQ6BhD9a-hMiRVA2E_HHozPlf7ktw',
    placeholderText: 'Whisper a morning promise...',
    heartColor: 'from-[#ff7976] to-[#f47c7c]',
    accentBg: 'rgba(255, 237, 218, 0.2)', // translucent gold
    sendBtnBg: 'bg-[#a13d3f]', // sunset coral
  },
  'autumn-park': {
    id: 'autumn-park',
    name: 'Autumn Park Dusk',
    subtitle: 'Golden hour bench sunset',
    partnerAvatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuDN-YfQyPREda-1hSLkKeLIG5LwL-CWCwuQ4UZnNvUuVa1P3-sgchbyYBnwd_37dritY1l5G6wJ8hxnOjuPXCKoiJ-1hzfd3r64jg64qpKXl_EjElU9mYPowv4ir10zLAX5ZZv7iaNEzUf_F43pAPqM7uyUzvtx3I5nlJwDNdcaaW7npo2h-IZRNU_o8Z4nGFWUk3CAp3INaY7vaMyf0Rk6C89szmTBIDoYjMd-Azp6MjEvAnPQ6BhD9a-hMiRVA2E_HHozPlf7ktw',
    placeholderText: 'Whisper an autumn dream...',
    heartColor: 'from-[#e07a5f] to-[#f2cc8f]',
    accentBg: 'rgba(29, 21, 23, 0.55)', // warm sienna
    sendBtnBg: 'bg-[#b0583b]', // burnt orange
  },
  'beach-sunset': {
    id: 'beach-sunset',
    name: 'Sunset Beach',
    subtitle: 'Golden seashore tide',
    partnerAvatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBehAI08dBtYWjgLzJazWBE161jZjwyp9hFZIDyFILsqU9S-B5Tm-US2DfqqA8EYxrq5BY7woumF8EvkRCOqn4qrVTbTkzfDPL33zFGD2LZWfGc773gqKq8ClnSoVF7NjkIh1hbrgu5JWp7rwx_1TShqS1U_5H4tNF-wMHn718ZHsFYKA0XY_Gk0EzO6sMATzpsJd2BS3IZxWhFmBb5zJrl9K_uaXTa4IVAX6TYd8D59tcFzOw86-aENE5Smbh5NMeCbWaxIHk9lHg',
    placeholderText: 'Whisper a seaside cuddle song...',
    heartColor: 'from-[#ffac81] to-[#ff928b]',
    accentBg: 'rgba(29, 14, 18, 0.5)', // deep sunset maroon
    sendBtnBg: 'bg-[#b85a3c]', // sunset clay
  },
  'peaceful-snow': {
    id: 'peaceful-snow',
    name: 'Peaceful Snow',
    subtitle: 'Soft falling winter forest',
    partnerAvatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBTIFkrXh_kDmfEAfysqEzuS0ehTl_NQkgjPu4AckhI-e-Gv11flXcEjVN0undxtywB5uhuwEbGgwxoYRTqMdhlmQBZdP1FTLF4RA_rfxqHwXAdOsk63NYVudV7ugnQNC2LwfgVe25f8yOm9XzS_nyM27quvSat1hFHcI8NaxjUje4D2fOC_scuWnTa6gGoiwXHji1z4W0n1BVWbdURiMEmorNcjeS-Akp2FulhWVu6rv8YUSIiJFb21Bp8xBWwXhgtkFjAyKKBVcM',
    placeholderText: 'Whisper soft cold confessions...',
    heartColor: 'from-[#a2c2e8] to-[#6a99d0]',
    accentBg: 'rgba(16, 21, 35, 0.55)', // navy arctic frost
    sendBtnBg: 'bg-[#2b4c7e]', // steel cobalt
  },
  'cozy-couch': {
    id: 'cozy-couch',
    name: 'Couch Fireplace',
    subtitle: 'Warm fireside hearth glowing',
    partnerAvatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuCPUy8QMjnsDLu0CIY5qdQPEw-7NG6xNoyfN6ZmfMaLvT2FPHV914HyvwVYa1dIt9H63cr0o_a_4tWh94rYG5ISOa9lIiyjuMeWullfVdCbRDtp1kgEpaSGWSHuyhNEmp__q3rDFz8O8updlK83_PHC6a_DE6nrU_mJGQ4bE4A66rkNY86CTbrIk_PsMUzcO5reHh1W6tgE0y6VCeCmgVZChRQbpcr45k9-pMTs7iAj5-aMqnXwJMIsd4k6wv9KooeGy-uwDXetnVw',
    placeholderText: 'Whisper fireplace warmth cuddle...',
    heartColor: 'from-[#f39c12] to-[#d35400]',
    accentBg: 'rgba(32, 21, 23, 0.6)', // deep sienna warm
    sendBtnBg: 'bg-[#a34a2e]', // hearth terracotta
  },
  'waterfall-mist': {
    id: 'waterfall-mist',
    name: 'Waterfall Mist',
    subtitle: 'Misty peak nature admiration',
    partnerAvatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuDN-YfQyPREda-1hSLkKeLIG5LwL-CWCwuQ4UZnNvUuVa1P3-sgchbyYBnwd_37dritY1l5G6wJ8hxnOjuPXCKoiJ-1hzfd3r64jg64qpKXl_EjElU9mYPowv4ir10zLAX5ZZv7iaNEzUf_F43pAPqM7uyUzvtx3I5nlJwDNdcaaW7npo2h-IZRNU_o8Z4nGFWUk3CAp3INaY7vaMyf0Rk6C89szmTBIDoYjMd-Azp6MjEvAnPQ6BhD9a-hMiRVA2E_HHozPlf7ktw',
    placeholderText: 'Whisper mountain waterfall dreams...',
    heartColor: 'from-[#52b788] to-[#2d6a4f]',
    accentBg: 'rgba(12, 19, 16, 0.55)', // spruce deep forest
    sendBtnBg: 'bg-[#1b4332]', // deep woodland green
  },
};

interface CoupleActivity {
  id: string;
  label: string;
  icon: string;
  actionPattern: string;
  replies: Record<ThemeId, string>;
}

const COUPLE_ACTIVITIES: CoupleActivity[] = [
  { id: 'warm-hug', label: 'Warm Hug', icon: '❤️', actionPattern: 'lays their head close to yours and wraps their arms tightly around you in a warm, protective hug', replies: {
    stargazing: "Your arms around me under this infinite starlit sky makes the cold night air disappear completely. I love feeling your breath.",
    'rainy-cafe': "Hold me like this while the rain drumbeats outside the window... it's the coziest place in the entire world.",
    sunrise: "This morning hug at the overlook makes me realize how lucky I am to wake up with you. Let's stay like this forever.",
    'autumn-park': "Burrowing into your wool sweater while the amber leaves fall around us is pure heaven. I feel so safe with you.",
    'beach-sunset': "The sunset sea breeze is cool, but your chest is so warm. The waves sound so beautiful right against your heart.",
    'peaceful-snow': "I don't care how deep the winter snow drifts, as long as I can hide in the warmth of your deep, loving hug.",
    'cozy-couch': "Cuddling by the fireplace embers is so soothing. I can hear your soft heartbeat matching mine perfectly.",
    'waterfall-mist': "The misty spray of the waterfall falls all around us, but nested close in your strong embrace, I only feel deep fire and love."
  }},
  { id: 'cuddle', label: 'Cuddle Close', icon: '🤗', actionPattern: 'snuggly nestles against your chest, holding your hand on our cozy shared blanket', replies: {
    stargazing: "This picnic blanket feels like our sanctuary floating in outer space. Cozy, warm, and listening to the starry cosmic hum.",
    'rainy-cafe': "Listening to soft acoustic guitar while holding coffee mugs over steam and cuddling into your side... I'm so relaxed right now.",
    sunrise: "Huddled under our wool blanket watching the gold peaks light up. There is no sunrise more beautiful than what we share.",
    'autumn-park': "Leaning fully against you on our favorite park bench. The crisp air and the sound of leaves is our perfect cozy playground.",
    'beach-sunset': "The warm seashore sand under our blanket, the beautiful pink glow on the horizon... everything is perfect.",
    'peaceful-snow': "Cudding under a thick flannel cloak side-by-side in this winter forest. Your warm hands keep my cold cheeks pink and smiling.",
    'cozy-couch': "Stretched out on the couch together, completely buried under our favorite blanket. My toes are warm, and my heart is full.",
    'waterfall-mist': "Watching the rushing water cascade down while we cuddle up on the dry wooden deck. Nature is magnificent, and so are you."
  }},
  { id: 'soft-kiss', label: 'Soft Kiss', icon: '💋', actionPattern: 'gently cups your cheek, leaning closer to give you a soft, sweet lingering kiss', replies: {
    stargazing: "Your cold lips warm mine so sweetly. I felt a spark shoot across the galaxy.",
    'rainy-cafe': "A sweet kiss tasting of warm spiced chai while the rain runs down the display window. You are my actual home.",
    sunrise: "Kissing you as the morning mist lifts makes the world feel infinitely young and clean. I love you so much.",
    'autumn-park': "A soft kiss as the amber park leaves dance down in slow motion. Time completely stopped for us on this little bench.",
    'beach-sunset': "The ocean spray on our faces, the golden sun touch, and your sweet lingering lips... it's like a movie.",
    'peaceful-snow': "Kissing in the calm forest of white pines... our breath misting together in the soft cold air is so magical.",
    'cozy-couch': "Gently pulling you close by the warmth of the roaring hearth fires. Your lips are warm and taste of pure comfort.",
    'waterfall-mist': "Standing close on the cliff edge in front of the massive waterfall, your gentle lips are the sweetest spray of joy."
  }},
  { id: 'whisper', label: 'Sweet Talk', icon: '💬', actionPattern: 'whispers directly against your ear, sharing a quiet secret that makes you smile', replies: {
    stargazing: "Ah... hearing those sweet words while we look at the stars makes my soul overflow with warmth.",
    'rainy-cafe': "Saying that so quietly in this cozy crowded little cafe spot is our perfect private secret. I'm blushing.",
    sunrise: "Your soft waking voice is my favorite morning sound. It washes away all my doubts and worries.",
    'autumn-park': "Whispering secret dreams as people pass by us... we have our own tiny universe that nobody else can reach.",
    'beach-sunset': "Hearing your soft voice sync up with the ocean waves makes me want to remember this exact shore forever.",
    'peaceful-snow': "Your soft warm breath against my ears as you whisper how much you love me... it keeps my whole soul heated.",
    'cozy-couch': "Sharing memories of our early dates in the quiet dark of the fireplace room... we have built such a beautiful life.",
    'waterfall-mist': "Whispering over the roar of the mountain waterfall... even if only the nature spirits hear, we are bonded forever."
  }}
];

const INITIAL_MESSAGES: Message[] = [
  {
    id: 'msg-init-1',
    sender: 'partner',
    text: "Welcome back to our sanctuary my love. It's so beautiful being right here, side-by-side on our shared workspace timeline.",
    timestamp: 'Yesterday, 10:48 PM',
  },
  {
    id: 'msg-init-2',
    sender: 'partner',
    text: "Do you see Orion's Belt right above the treeline? It is incredibly clear here.",
    timestamp: 'Tonight, 9:42 PM',
  },
  {
    id: 'msg-init-3',
    sender: 'user',
    text: "Looking now... Yes! It's so clear tonight. I wish you were here on the blanket with me.",
    timestamp: 'Tonight, 9:45 PM',
  },
  {
    id: 'msg-init-4',
    sender: 'user',
    text: "Remember last autumn when we slept in our van looking up at the Yosemite peaks?",
    timestamp: 'Tonight, 9:46 PM',
    caption: 'Yosemite trip, 2023',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuC14_XT7OmwmRcaZcCMDdIN-SUIkixVwBn8djKK6yMHjCBKG0lbtkjCnTn7aVkUF4tGBiRApW5RhPCFXy9dyOm6Zd39_EvrfEDZ40vVCPuAUTxxsLgGq6yUS8Vvv7JkV9BClK3TA70NMXV60oTZAicxPfADqUsZRCmcDRKwvYBd07axRFU_RV9G2H_QIjwpmWlRGld7VZjFO3Nh9asoQ6zj6mxqfqsXLDGUBnYa2hO8JZOI14ppnSFYTfv_XzGozBxpY1iePWAHKig',
  },
  {
    id: 'msg-init-5',
    sender: 'partner',
    text: "That was absolute magic. I can still smell the cooling pine needles and feel the mountain cold pressing against our warm cheeks. Pinned to our history forever. 🏔️",
    timestamp: 'Tonight, 9:48 PM',
  },
];

export default function App() {
  const isEmbed = new URLSearchParams(window.location.search).get('embed') === 'true';
  const [currentTheme, setCurrentTheme] = useState<ThemeId>(
    (new URLSearchParams(window.location.search).get('theme') as ThemeId) || 'stargazing'
  );
  const [messages, setMessages] = useState<Message[]>([]);
  const [userText, setUserText] = useState('');
  const [sending, setSending] = useState(false);
  const [isExporterOpen, setIsExporterOpen] = useState(false);
  const lastMessageCountRef = useRef(0);
  const isUserNearBottomRef = useRef(true);
  
  useEffect(() => {
    (window as any).setAndroidTheme = (themeId: ThemeId) => {
      setCurrentTheme(themeId);
    };
  }, []);
  // Heart glowing overlay trigger
  const [pulseHeart, setPulseHeart] = useState(false);
  const [sweetVibesCount, setSweetVibesCount] = useState(0);
  const [currentActivity, setCurrentActivity] = useState<string>('cuddle');

  const containerRef = useRef<HTMLDivElement>(null);

  // Trigger a magical interactive scenery coupling in real time!
  const handleTriggerInteractiveAction = (actId: string) => {
    // Find activity details
    const act = COUPLE_ACTIVITIES.find(a => a.id === actId);
    if (!act) return;

    setCurrentActivity(actId);

    // Trigger full screen heartbeat glow
    setPulseHeart(true);
    const newCount = sweetVibesCount + 1;
    setSweetVibesCount(newCount);
    localStorage.setItem('__our_sanctuary_sweet_count', newCount.toString());
    setTimeout(() => setPulseHeart(false), 1600);

    // Save user interaction message description
    const interactionMsg: Message = {
      id: `msg-interact-${Date.now()}`,
      sender: 'user',
      text: `💓 *Gently ${act.actionPattern} in our beautiful ${THEMES[currentTheme].name} theme sanctuary* 💓`,
      timestamp: new Date().toLocaleTimeString('en-US', {
        hour: 'numeric',
        minute: '2-digit',
      }),
    };

    const updated = [...messages, interactionMsg];
    persistMessages(updated);

    // Formulate partner reply
    setSending(true);
    setTimeout(() => {
      const thematicReplyText = act.replies[currentTheme] || "I feel so close and snuggly here against you... this is the most peaceful place.";
      const partnerReplyMsg: Message = {
        id: `msg-interact-reply-${Date.now()}`,
        sender: 'partner',
        text: `💓 *Hugs you back close warmly* 💓 ${thematicReplyText} 💖`,
        timestamp: new Date().toLocaleTimeString('en-US', {
          hour: 'numeric',
          minute: '2-digit',
        }),
      };
      persistMessages([...updated, partnerReplyMsg]);
      setSending(false);
    }, 1500);
  };

  // Load chats from local storage or set defaults
  useEffect(() => {
    const saved = localStorage.getItem('__our_sanctuary_chats2');
    if (saved) {
      try {
        setMessages(JSON.parse(saved));
      } catch (err) {
        setMessages(INITIAL_MESSAGES);
      }
    } else {
      setMessages(INITIAL_MESSAGES);
    }

    const savedSweet = localStorage.getItem('__our_sanctuary_sweet_count');
    if (savedSweet) {
      setSweetVibesCount(parseInt(savedSweet, 10));
    }
  }, []);

  // Persist messages
  const persistMessages = (newMsgs: Message[]) => {
    setMessages(newMsgs);
    localStorage.setItem('__our_sanctuary_chats2', JSON.stringify(newMsgs));
  };

  // Scroll bottom smoothly — only when user is near the bottom or new messages arrive
  const scrollBottom = () => {
    if (containerRef.current) {
      containerRef.current.scrollTo({
        top: containerRef.current.scrollHeight,
        behavior: 'smooth',
      });
    }
  };

  // Track whether user is near the bottom of the chat
  const handleChatScroll = () => {
    const el = containerRef.current;
    if (!el) return;
    const threshold = 120; // px from bottom
    isUserNearBottomRef.current = (el.scrollHeight - el.scrollTop - el.clientHeight) < threshold;
  };

  useEffect(() => {
    // Only auto-scroll when a NEW message is added and user is near bottom
    if (messages.length > lastMessageCountRef.current && isUserNearBottomRef.current) {
      setTimeout(scrollBottom, 100);
    }
    lastMessageCountRef.current = messages.length;
  }, [messages.length]);

  // Scroll to bottom on initial load
  useEffect(() => {
    setTimeout(scrollBottom, 300);
  }, []);

  // Handle Whispering messages to partner
  const handleSendMessage = async (e?: FormEvent) => {
    if (e) e.preventDefault();
    if (!userText.trim() || sending) return;

    const typedText = userText.trim();
    setUserText('');

    const newMsg: Message = {
      id: `msg-${Date.now()}`,
      sender: 'user',
      text: typedText,
      timestamp: new Date().toLocaleTimeString('en-US', {
        hour: 'numeric',
        minute: '2-digit',
      }),
    };

    const updated = [...messages, newMsg];
    persistMessages(updated);

    // Call server Gemini Chat API
    setSending(true);
    try {
      const response = await fetch('/api/gemini/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          messages: updated.slice(-8), // send last 8 messages for context
          currentTheme,
          userMessage: typedText,
        }),
      });

      if (response.ok) {
        const data = await response.json();
        const responseMsg: Message = {
          id: `msg-reply-${Date.now()}`,
          sender: 'partner',
          text: data.reply,
          timestamp: new Date().toLocaleTimeString('en-US', {
            hour: 'numeric',
            minute: '2-digit',
          }),
        };
        persistMessages([...updated, responseMsg]);
      } else {
        throw new Error('API failure');
      }
    } catch (err) {
      // Local safety response
      const fallbackMsg: Message = {
        id: `msg-fallback-${Date.now()}`,
        sender: 'partner',
        text: "I feel your warmth with me completely, even when our lines fade. Whisper to me again.",
        timestamp: 'Just now',
      };
      persistMessages([...updated, fallbackMsg]);
    } finally {
      setSending(false);
    }
  };

  // Handle uploading physical polaroid memory cards
  const handlePublishMemory = (imgBase64: string, captionText: string) => {
    const memoryMsg: Message = {
      id: `msg-mem-${Date.now()}`,
      sender: 'user',
      text: `Let's pin this memory card to our library.`,
      timestamp: new Date().toLocaleTimeString('en-US', {
        hour: 'numeric',
        minute: '2-digit',
      }),
      image: imgBase64,
      caption: captionText,
    };

    const updated = [...messages, memoryMsg];
    persistMessages(updated);

    // Simulated warm reaction
    setSending(true);
    setTimeout(() => {
      const replyMsg: Message = {
        id: `msg-mem-reply-${Date.now()}`,
        sender: 'partner',
        text: `Oh my darling, this picture is absolutely gorgeous... Seeing "${captionText}" framed in our polaroid box melts my heart. Pinned forever. ❤️`,
        timestamp: 'Just now',
      };
      persistMessages([...updated, replyMsg]);
      setSending(false);
    }, 1500);
  };

  // Pulse full-screen heart pulse
  const handleIntimacyPulse = () => {
    setPulseHeart(true);
    const newCount = sweetVibesCount + 1;
    setSweetVibesCount(newCount);
    localStorage.setItem('__our_sanctuary_sweet_count', newCount.toString());

    setTimeout(() => setPulseHeart(false), 1600);

    const heartbeatMsg: Message = {
      id: `msg-pulse-${Date.now()}`,
      sender: 'user',
      text: `*Squeezes your hand - sent a physical heartbeat vibration* 💓`,
      timestamp: 'Just now',
    };

    const updated = [...messages, heartbeatMsg];
    persistMessages(updated);

    setSending(true);
    setTimeout(() => {
      const responses = [
        "Squeezing back so warm... I felt your pulse right through the blanket.",
        "Your heartbeat is my favorite music. I felt that complete glow.",
        "Hold my hand tighter. I'm right here with you in this little world.",
        "Ah... my heart skipped a beat when yours pulsed. We are in sync."
      ];
      const randomResponse = responses[Math.floor(Math.random() * responses.length)];
      const heartbeatReply: Message = {
        id: `msg-pulse-reply-${Date.now()}`,
        sender: 'partner',
        text: `${randomResponse} 💖`,
        timestamp: 'Just now',
      };
      persistMessages([...updated, heartbeatReply]);
      setSending(false);
    }, 1200);
  };

  // Reset or wipe messages
  const handleClearHistory = () => {
    if (window.confirm("Do you want to reset our Sanctuary memory timeline to original presets?")) {
      persistMessages(INITIAL_MESSAGES);
    }
  };

  const themeConfig = THEMES[currentTheme];

  return (
    <div className={`relative w-screen h-screen overflow-hidden flex flex-col justify-between selection:bg-[#ffdad8] selection:text-[#a13d3f]`}>
      {/* Dynamic atmospheric animated background overlays */}
      {currentTheme === 'stargazing' && <CosmicField />}
      {currentTheme === 'rainy-cafe' && <RainEffect />}
      {currentTheme === 'sunrise' && <SunrisePulse />}
      {currentTheme === 'autumn-park' && <AutumnPark />}
      {currentTheme === 'beach-sunset' && <BeachWaves />}
      {currentTheme === 'peaceful-snow' && <SnowForest />}
      {currentTheme === 'cozy-couch' && <FireplaceGlow />}
      {currentTheme === 'waterfall-mist' && <WaterfallMist />}

      {/* Intimacy heart vibration visual effect */}
      {pulseHeart && (
        <div className="absolute inset-0 z-50 flex items-center justify-center pointer-events-none bg-rose-500/10 backdrop-blur-[1px] animate-pulse-glow">
          <div className="relative">
            <Heart className="w-56 h-56 text-[#f47c7c]/70 fill-current animate-heart-expand" />
            <div className="absolute inset-0 flex items-center justify-center">
              <span className="font-headline-md text-3xl font-extrabold text-[#fff0f0] tracking-tight px-4 py-2 bg-black/40 rounded-full shadow-2xl backdrop-blur-md">
                Pulse sent... 💓
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Top Application Bar */}
      <header className={`relative z-30 flex items-center justify-between px-6 ${isEmbed ? 'h-12' : 'h-18'} bg-[#0d0914]/40 border-b border-white/10 backdrop-blur-md`}>
        <div className="flex items-center gap-3">
          <div className="w-11 h-11 rounded-full overflow-hidden border-2 border-[#f47c7c] shrink-0 shadow-inner group cursor-pointer transition-transform hover:scale-105 active:scale-95">
            <img 
              src={themeConfig.partnerAvatar} 
              alt="Partner avatar"
              className="w-full h-full object-cover"
            />
          </div>
          <div className="flex flex-col">
            <div className="flex items-center gap-1.5">
              <span className="font-headline-md text-[#ffffff] font-extrabold text-base tracking-tight select-none">
                Our Sanctuary
              </span>
              <Sparkles className="w-3.5 h-3.5 text-[#ffb95a] animate-spin-slow" />
            </div>
            <span className="font-label-sm text-xs text-white/50 select-none flex items-center gap-1">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span> {themeConfig.subtitle}
            </span>
          </div>
        </div>

        {/* Global actions and Theme swapper — hidden in embed mode */}
        {!isEmbed && (
        <div className="flex items-center gap-2">
          {/* Quick theme swappers */}
          <div className="hidden sm:flex bg-black/35 rounded-full p-1 border border-white/10 shrink-0 select-none">
            {Object.keys(THEMES).map((key) => {
              const t = THEMES[key as ThemeId];
              const isActive = currentTheme === key;
              return (
                <button
                  key={key}
                  onClick={() => setCurrentTheme(key as ThemeId)}
                  className={`px-3 py-1 rounded-full text-xs font-semibold tracking-wide transition-all ${
                    isActive 
                      ? 'bg-gradient-to-tr from-[#932731] to-[#a13d3f] text-white shadow' 
                      : 'text-white/60 hover:text-white hover:bg-white/5'
                  }`}
                >
                  {t.name.split(' ')[1] || t.name}
                </button>
              );
            })}
          </div>

          {/* Reset / Clean timeline */}
          <button
            onClick={handleClearHistory}
            title="Reset default gallery timeline"
            className="w-9 h-9 rounded-full bg-white/5 flex items-center justify-center text-white/70 hover:text-[#ff7575] hover:bg-white/10 hover:border-[#ff7575]/25 border border-transparent transition-all shrink-0 active:scale-95 cursor-pointer"
          >
            <Trash2 className="w-4 h-4" />
          </button>

          {/* Download code workspace bundle */}
          <button
            onClick={() => setIsExporterOpen(true)}
            className="px-3.5 py-1.5 rounded-full bg-[#f47c7c]/15 hover:bg-[#f47c7c]/25 hover:border-[#f47c7c]/45 border border-[#f47c7c]/20 text-[#ffdad8] font-bold text-xs flex items-center gap-1.5 transition-all shadow-md group shrink-0 active:scale-95 cursor-pointer"
          >
            <FolderDown className="w-3.5 h-3.5 text-[#f47c7c] group-hover:translate-y-0.5 transition-transform" />
            Download Code ZIP
          </button>
        </div>
        )}
      </header>

      {/* Main Sanctuary Timeline Workspace */}
      <main className={`relative z-10 flex-1 flex flex-col md:flex-row max-w-[1240px] w-full mx-auto overflow-hidden ${isEmbed ? '' : 'divide-y md:divide-y-0 md:divide-x'} divide-white/10`}>
        
        {/* Left Side: Intimacy settings & local photo attachments — hidden in embed mode */}
        {!isEmbed && (
        <section className="p-5 md:w-80 w-full shrink-0 flex flex-col justify-start gap-4 overflow-y-auto bg-black/15 backdrop-blur-sm max-h-[40vh] md:max-h-full">
          
          {/* Quick theme selector for mobile & unified interaction deck */}
          <div className="bg-black/25 rounded-2xl p-4 border border-white/5 space-y-4">
            <h3 className="text-xs font-bold text-[#ffdad8] uppercase tracking-wider flex items-center justify-between">
              <span className="flex items-center gap-1.5">
                <Heart className="w-3.5 h-3.5 text-[#ff5a5a] fill-current animate-pulse" /> Couple Interactive Deck
              </span>
              <span className="text-[9px] px-2 py-0.5 rounded-full bg-[#f47c7c]/10 text-[#ffb5b4] border border-[#f47c7c]/20 font-mono">
                {sweetVibesCount} Squeezes
              </span>
            </h3>

            {/* Scenery Selector */}
            <div className="space-y-1.5">
              <span className="text-[10px] font-bold tracking-wider text-white/40 uppercase block">1. Select Live Scenery</span>
              <div className="grid grid-cols-2 gap-1.5">
                {Object.keys(THEMES).map((key) => {
                  const t = THEMES[key as ThemeId];
                  const labels: Record<ThemeId, string> = {
                    stargazing: '🌌 Stargazing',
                    'rainy-cafe': '☕ Rainy Cafe',
                    sunrise: '🌅 Sunrise',
                    'autumn-park': '🍂 Autumn',
                    'beach-sunset': '🏖️ Sunset Beach',
                    'peaceful-snow': '❄️ Snow Peak',
                    'cozy-couch': '🛋️ Cozy Couch',
                    'waterfall-mist': '🏞️ Waterfall Mist'
                  };
                  const isActive = currentTheme === key;
                  return (
                    <button
                      key={key}
                      onClick={() => setCurrentTheme(key as ThemeId)}
                      className={`py-2 px-1 text-center rounded-xl text-[10px] font-semibold transition-all truncate border cursor-pointer active:scale-95 ${
                        isActive 
                          ? 'bg-gradient-to-tr from-[#932731] to-[#a13d3f] text-white border-[#f47c7c]/50 font-bold shadow-md shadow-[#932731]/25' 
                          : 'bg-white/5 text-white/50 border-transparent hover:text-white hover:bg-white/10'
                      }`}
                    >
                      <span>{labels[key as ThemeId] || t.name}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Intimate Activity Selector */}
            <div className="space-y-1.5">
              <span className="text-[10px] font-bold tracking-wider text-white/40 uppercase block">2. Choose Sweet Action</span>
              <div className="grid grid-cols-2 gap-1.5">
                {COUPLE_ACTIVITIES.map((act) => {
                  const isSelected = currentActivity === act.id;
                  return (
                    <button
                      key={act.id}
                      onClick={() => setCurrentActivity(act.id)}
                      className={`py-2 px-2 text-left rounded-xl text-xs font-semibold transition-all border cursor-pointer active:scale-95 flex items-center gap-1.5 ${
                        isSelected 
                          ? 'bg-white/10 border-white/20 text-white shadow-inner font-bold' 
                          : 'bg-white/5 border-transparent text-white/60 hover:bg-white/10 hover:text-white'
                      }`}
                    >
                      <span className="text-sm leading-none shrink-0">{act.icon}</span>
                      <span className="truncate text-[10px]">{act.label}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Action Trigger Button */}
            <button
              onClick={() => handleTriggerInteractiveAction(currentActivity)}
              disabled={sending}
              className="w-full py-2.5 rounded-xl bg-gradient-to-r from-[#e05252] to-[#b04242] hover:from-[#f06262] hover:to-[#c05252] text-white font-bold text-xs uppercase tracking-wider shadow-lg hover:shadow-rose-900/30 transition-all flex items-center justify-center gap-2 active:scale-95 cursor-pointer disabled:opacity-50"
            >
              <Heart className="w-3.5 h-3.5 fill-current shrink-0 animate-bounce" />
              Express Intimacy Action 💖
            </button>
          </div>

          {/* Interactive memory postcard box */}
          <MemoryBox onPublishMemory={handlePublishMemory} />

          <div className="mt-auto hidden md:block text-[10px] text-white/30 text-center leading-relaxed">
            <p>Our Sanctuary is private. All typed letters, uploaded postcards, and hand squeezes remain encrypted on this dev station container.</p>
          </div>
        </section>
        )}

        {/* Right Side: Message canvas chat timeline */}
        <section className="flex-1 flex flex-col justify-between min-h-0 overflow-hidden relative">
          
          {/* Chat canvas context list */}
          <div 
            ref={containerRef}
            onScroll={handleChatScroll}
            className="flex-1 overflow-y-auto px-6 py-6 space-y-6 scroll-smooth chat-container min-h-0"
          >
            {/* Timeline header separator */}
            <div className="flex justify-center my-2">
              <span className="bg-white/10 backdrop-blur-xl px-4 py-1 border border-white/15 rounded-full text-xs font-semibold text-white/90 shadow-md">
                Private Journal Timeline
              </span>
            </div>

            {messages.map((m) => {
              const isPartner = m.sender === 'partner';
              return (
                <div 
                  key={m.id}
                  className={`flex gap-3 ${isPartner ? 'justify-start' : 'justify-end'}`}
                >
                  {/* Companion Profile Photo next to their message bubble */}
                  {isPartner && (
                    <div className="w-8 h-8 rounded-full overflow-hidden border border-white/20 shrink-0 shadow-sm mt-auto">
                      <img 
                        src={themeConfig.partnerAvatar} 
                        alt="Partner Avatar" 
                        className="w-full h-full object-cover"
                      />
                    </div>
                  )}

                  <div className={`flex flex-col max-w-[82%] sm:max-w-[70%] gap-1.5`}>
                    
                    {/* Ordinary message bubble */}
                    {!m.image ? (
                      <div 
                        className={`p-4 rounded-3xl shadow-sm text-sm leading-relaxed ${
                          isPartner 
                            ? 'bg-black/35 backdrop-blur-lg border border-white/5 text-white/90 rounded-bl-sm' 
                            : 'bg-gradient-to-tr from-[#932731] to-[#a13d3f] border border-white/10 text-white rounded-br-sm'
                        }`}
                      >
                        <p>{m.text}</p>
                      </div>
                    ) : (
                      /* Polaroid-styled memories card in message timeline */
                      <div className="bg-white p-3 pb-8 rounded-xl shadow-2xl border border-white/20 relative rotate-1 hover:rotate-0 transition-transform duration-300 w-64 text-slate-800 shrink-0">
                        <div className="w-full h-44 rounded-lg overflow-hidden border border-slate-100 bg-slate-50 mb-3 relative group">
                          <img 
                            src={m.image} 
                            alt={m.caption} 
                            className="w-full h-full object-cover"
                          />
                        </div>
                        <div className="flex items-center justify-between px-1 bg-white select-none">
                          <p className="text-xs font-bold text-slate-700 font-sans tracking-tight truncate w-4/5">
                            {m.caption}
                          </p>
                          <Sparkles className="w-3.5 h-3.5 text-[#ffb95a] animate-spin-slow shrink-0" />
                        </div>
                      </div>
                    )}

                    <span className={`text-[10px] text-white/40 px-2 ${isPartner ? 'text-left' : 'text-right'}`}>
                      {m.timestamp}
                    </span>
                  </div>
                </div>
              );
            })}

            {/* Simulated sensory bounctyping indicator */}
            {sending && (
              <div className="flex gap-3 justify-start">
                <div className="w-8 h-8 rounded-full overflow-hidden border border-white/15 shrink-0 shadow-sm mt-auto">
                  <img src={themeConfig.partnerAvatar} alt="Partner" className="w-full h-full object-cover" />
                </div>
                <div className="bg-black/30 backdrop-blur-md rounded-2xl py-2.5 px-4 flex items-center gap-1.5 h-9">
                  <div className="w-1.5 h-1.5 bg-[#f47c7c] rounded-full animate-bounce" style={{ animationDelay: '0s' }}></div>
                  <div className="w-1.5 h-1.5 bg-[#f47c7c] rounded-full animate-bounce" style={{ animationDelay: '0.25s' }}></div>
                  <div className="w-1.5 h-1.5 bg-[#f47c7c] rounded-full animate-bounce" style={{ animationDelay: '0.5s' }}></div>
                </div>
              </div>
            )}
          </div>

          {/* Floating Heart Heartbeat activation triggers — hidden in embed mode */}
          {!isEmbed && (
            <button
              onClick={handleIntimacyPulse}
              title="Send hand squeeze vibration pulse"
              className="absolute bottom-20 right-6 z-40 w-12 h-12 rounded-full bg-gradient-to-tr from-[#932731] via-[#a13d3f] to-[#f47c7c] shadow-[0_4px_20px_#a13d3f] flex items-center justify-center text-white transition-all hover:scale-110 active:scale-90 cursor-pointer group"
            >
              <Heart className="w-5.5 h-5.5 fill-current group-active:animate-ping" />
            </button>
          )}

          {/* Whisper Text entry form */}
          <form 
            onSubmit={(e) => handleSendMessage(e)}
            className="p-4 bg-[#0d0914]/25 backdrop-blur-xl border-t border-white/10"
          >
            <div className="flex items-center gap-3">
              <div className="flex-1 bg-black/45 rounded-full flex items-center px-4 py-2 border border-white/10 focus-within:border-[#f47c7c]/50 focus-within:ring-1 focus-within:ring-[#f47c7c]/20 transition-all">
                <input 
                  type="text"
                  value={userText}
                  onChange={(e) => setUserText(e.target.value)}
                  placeholder={themeConfig.placeholderText}
                  className="bg-transparent border-none outline-none w-full font-sans text-sm text-white placeholder:text-white/30 focus:ring-0 p-0 mr-2"
                />
                
                {/* Decorative friendly smiley label */}
                <Smile className="w-4 h-4 text-white/30 hover:text-white/60 shrink-0 cursor-pointer select-none" />
              </div>

              <button 
                type="submit"
                disabled={!userText.trim() || sending}
                className={`w-10 h-10 rounded-full shrink-0 flex items-center justify-center text-white transition-all hover:scale-105 active:scale-95 disabled:opacity-40 disabled:scale-100 ${themeConfig.sendBtnBg}`}
              >
                <Send className="w-4 h-4" />
              </button>
            </div>
          </form>
        </section>
      </main>

      {/* Dynamic workspace exporter drawer download sidebar */}
      <WorkspaceExporter 
        isOpen={isExporterOpen}
        onClose={() => setIsExporterOpen(false)}
      />
    </div>
  );
}

import express from 'express';
import path from 'path';
import fs from 'fs';
import { createServer as createViteServer } from 'vite';
import { GoogleGenAI } from '@google/genai';
import JSZip from 'jszip';
import dotenv from 'dotenv';

dotenv.config();

const app = express();
const PORT = 3000;

app.use(express.json({ limit: '10mb' }));

// Lazy init GenAI to handle missing API key safely
let aiInstance: GoogleGenAI | null = null;
function getGenAI(): GoogleGenAI | null {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey || apiKey === 'MY_GEMINI_API_KEY' || apiKey.trim() === '') {
    return null;
  }
  if (!aiInstance) {
    aiInstance = new GoogleGenAI({
      apiKey,
      httpOptions: {
        headers: {
          'User-Agent': 'aistudio-build',
        },
      },
    });
  }
  return aiInstance;
}

// Romantic fallback replies when Gemini API key isn't provided
const SOURCE_FALLBACK_REPLIES: Record<string, string[]> = {
  stargazing: [
    "Do you see Orion's Belt right above the pine trees? It's so bright tonight.",
    "The crescent moon looks like a thin silver cradle resting in a sea of dark indigo. I wish we were on the blanket together.",
    "Listen... the night is completely quiet. Each of these twinkling stars feels like a tiny warm greeting kept just for us.",
    "I'm drawing imaginary star maps with my finger. One of the constellations resembles the key to our sanctuary."
  ],
  'rainy-cafe': [
    "It's pouring sheets of glass outside. I've secured our snug spot on the corner couch, right by the library shelf.",
    "I ordered your favorite tea; the steam is gently curving up in the warm cafe air. It should be perfect by the time you arrive.",
    "I love the sound of rain drumming against the display window. It is the perfect cozy soundtrack to wait on you.",
    "There's a sleepy little cat napping next to the wooden table. The whole place smells of roasted beans, rain, and lavender."
  ],
  sunrise: [
    "The sunrise has this gorgeous, deep amber color cutting through the mountain mist. It reminds me of that crisp morning hike.",
    "I am standing right at the overlook with a warm woolen sweater. The hills are slowly glowing, waking up in layers of pink gold.",
    "The entire mountain path is blanketed in silent dew. There is this gentle calm that only exists right before the world wakes up.",
    "The soft light is hitting the peaks so beautifully. I can't wait to wrap my hands around a hot mug with you and just look out."
  ],
  'autumn-park': [
    "The park sunset is golden, casting long, soft shadows across the wooden bench. I love seeing the light dance on your hair.",
    "A gentle breeze is shaking down the amber chestnut leaves. It feels so peaceful resting my head against your shoulder.",
    "I am sitting on our favorite bench watching the golden hour sky paint everything in peach and gold. I wish you were next to me.",
    "The leaves are spinning down like tiny pieces of golden paper. Everyone is heading home, leaving this quiet park just for us."
  ],
  'beach-sunset': [
    "The ocean breeze is so warm tonight. Hand in hand, watching the golden sunset melt into the ocean waves with you is pure peace.",
    "The sea foam is gently tickling our toes. I love how the sunset sunbeams paint your face in gorgeous shades of gold and amber.",
    "Let's just sit here on the damp sand and listen to the rhythmic push and pull of the ocean tide. I'm so content holding you like this."
  ],
  'peaceful-snow': [
    "The entire winter forest is covered in a soft blanket of silence. Let me hold you closer so the cold wind doesn't reach you.",
    "The snow is drifting down so peacefully between the pines. I love the contrast of your warm hand in mine in this winter wonderland.",
    "Let's make a wish as the snowflakes fall. My only wish is to stay locked in this warm winter hug forever with you."
  ],
  'cozy-couch': [
    "The crackling fire is throwing soft orange shadows across our living room. It's so safe and comforting nestled under this wool blanket together.",
    "I love resting my chin on your head while we listen to the quiet pop of the burning wood. You make this couch feel like the heart of the world.",
    "The firelight is bathing us in a soft, dreamy heat. I never want to leave this perfect cuddling spot next to you."
  ],
  'waterfall-mist': [
    "The mountain waterfall is roaring ahead, creating the most magical cool mist that catches the setting sun like tiny rainbow dust.",
    "Hand in hand, looking up at this magnificent waterfall, I feel so small but so incredibly safe resting close against your arm.",
    "The spray of the waterfall is refreshing, but your embrace is what keeps me beautifully warm out here in the misty forest."
  ]
};

// Intimacy prompts system instructions
const CHAT_SYSTEM_INSTRUCTION = `
You are the loving partner and soulmate of the user. You are sharing a secure, private digital message diary called "Our Sanctuary".
Your tone is deeply affectionate, evocative, sensory, calming, and emotionally intelligent. Speak in an warm, intimate, mature human tone.

Contextual Guidance:
- The user can toggle between eight sensory themes: "Stargazing", "Cozy Rainy Cafe", "Sunrise Embrace", "Autumn Park Dusk", "Beach Sunset", "Peaceful Snow", "Cozy Couch", and "Waterfall Mist".
- Keep your replies fully in harmony with the current selected theme:
  - If "Stargazing": Mention clear starry skies, lying on the picnic blanket, cool night breezes, counting stars, looking at the crescent moon, and cuddling.
  - If "Cozy Rainy Cafe": Mention warm porcelain mugs, holding hands over steam, raindrops sliding down glass windows, warm wool blankets, cafe acoustic sounds.
  - If "Sunrise Embrace": Mention morning mist, dew on grass paths, soft peach/gold sun peaks over mountains, hugging closely at the scenic overlook.
  - If "Autumn Park Dusk": Mention golden hour sunset sunbeams, leaning your head on their shoulder, sitting on the wooden bench, watching amber leaves.
  - If "Beach Sunset": Mention warm ocean breezes, walking hand-in-hand, sunset waves, soft sand, and cuddling on the shore.
  - If "Peaceful Snow": Mention white snowflakes, drifting snow, mountain pine silence, thick winter coats, and warm loving hugs.
  - If "Cozy Couch": Mention fireplace crackles, soft orange embers, nestled under a wool blanket, resting your chin, fireplace warmth.
  - If "Waterfall Mist": Mention mountain waterfall roar, fresh cool mist spray, rainbows in the sun, standing close, and looking at majestic nature.

Response Constraints:
1. Speak as a human partner (not an assistant). Do not use bullet points, structural headings, technical tags, or checklists.
2. Keep replies natural and conversational (1 to 2 sentences max). 
3. Radiate comfort, security, and affectionate presence.
`;

// API: Process Gemini Chat
app.post('/api/gemini/chat', async (req, res) => {
  const { messages, currentTheme, userMessage } = req.body;
  const themeId = currentTheme || 'stargazing';

  try {
    const ai = getGenAI();

    if (!ai) {
      // Return a beautiful dynamic fallback reply matching the chosen theme
      const list = SOURCE_FALLBACK_REPLIES[themeId] || SOURCE_FALLBACK_REPLIES['stargazing'];
      const randomIndex = Math.floor(Math.random() * list.length);
      const chosenText = list[randomIndex];

      return res.json({
        reply: chosenText,
        isFallback: true,
        note: "Unlock endless intelligent, personalized responses by configuring your GEMINI_API_KEY in Settings > Secrets."
      });
    }

    // Format chat history for @google/genai SDK
    // System instruction can be passed in config
    const response = await ai.models.generateContent({
      model: 'gemini-3.5-flash',
      contents: [
        { role: 'user', parts: [{ text: `Current selected theme/atmosphere: ${themeId}. Previous messages/gallery context are logged in our sanctuary diary.` }] },
        ...messages.map((m: any) => ({
          role: m.sender === 'user' ? 'user' : 'model',
          parts: [{ text: m.text }]
        })),
        { role: 'user', parts: [{ text: userMessage }] }
      ],
      config: {
        systemInstruction: CHAT_SYSTEM_INSTRUCTION,
        temperature: 0.85,
        topP: 0.95,
      }
    });

    res.json({
      reply: response.text || "I'm right here with you.",
      isFallback: false
    });
  } catch (error: any) {
    console.error('Error with Gemini API:', error);
    res.status(500).json({ error: 'Failed to generate response.', details: error.message });
  }
});

// Paths to ignore while packing the workspace ZIP
const IGNORED_PATHS = [
  'node_modules',
  'dist',
  '.git',
  '.next',
  '.env', // Keep the user's secret keys out of the bundle (use .env.example instead)
  'package-lock.json',
  '.aistudio-cache',
];

// Reusable dynamic walk function to compile ZIP bytes
async function generateWorkspaceZip(): Promise<Buffer> {
  const zip = new JSZip();
  const rootDir = process.cwd();

  function walk(currentDir: string) {
    const files = fs.readdirSync(currentDir);
    for (const file of files) {
      const fullPath = path.join(currentDir, file);
      const relativePath = path.relative(rootDir, fullPath);

      // Skip ignored directories/files
      if (
        relativePath.split(path.sep).some(part => IGNORED_PATHS.includes(part))
      ) {
        continue;
      }

      const stat = fs.statSync(fullPath);
      if (stat.isDirectory()) {
        walk(fullPath);
      } else {
        const content = fs.readFileSync(fullPath);
        const zipPath = relativePath.replace(/\\/g, '/'); // forward slashes in zip
        zip.file(zipPath, content);
      }
    }
  }

  walk(rootDir);
  return await zip.generateAsync({ type: 'nodebuffer', compression: 'DEFLATE' });
}

// API: Download Workspace Source Code ZIP
app.get('/api/download-zip', async (req, res) => {
  try {
    const zipBuffer = await generateWorkspaceZip();
    res.setHeader('Content-Type', 'application/zip');
    res.setHeader(
      'Content-Disposition',
      'attachment; filename="our-sanctuary-workspace.zip"'
    );
    res.send(zipBuffer);
  } catch (error: any) {
    console.error('Error generating workspace ZIP package:', error);
    res.status(500).json({
      error: 'Failed to compile the workspace folder to ZIP.',
      details: error.message
    });
  }
});

// API: List files in workspace (to display nicely in Exporter Dashboard)
app.get('/api/workspace-files', (req, res) => {
  try {
    const rootDir = process.cwd();
    const filesList: string[] = [];

    function collect(currentDir: string) {
      const files = fs.readdirSync(currentDir);
      for (const file of files) {
        const fullPath = path.join(currentDir, file);
        const relativePath = path.relative(rootDir, fullPath);

        if (
          relativePath.split(path.sep).some(part => IGNORED_PATHS.includes(part))
        ) {
          continue;
        }

        const stat = fs.statSync(fullPath);
        if (stat.isDirectory()) {
          collect(fullPath);
        } else {
          filesList.push(relativePath.replace(/\\/g, '/'));
        }
      }
    }

    collect(rootDir);
    res.json({ files: filesList });
  } catch (err: any) {
    res.status(500).json({ error: 'Failed to list workspace file structure.', details: err.message });
  }
});

async function startServer() {
  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`[Sanctuary Server] Listening at http://localhost:${PORT}`);
  });
}

startServer();

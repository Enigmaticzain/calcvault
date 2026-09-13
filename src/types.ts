export type ThemeId = 'stargazing' | 'rainy-cafe' | 'sunrise' | 'autumn-park' | 'beach-sunset' | 'peaceful-snow' | 'cozy-couch' | 'waterfall-mist';

export interface Message {
  id: string;
  sender: 'user' | 'partner';
  text: string;
  timestamp: string;
  image?: string;
  caption?: string;
  isPinned?: boolean;
}

export interface Memory {
  id: string;
  image: string;
  title: string;
  date: string;
}

export interface ThemeConfig {
  id: ThemeId;
  name: string;
  subtitle: string;
  partnerAvatar: string;
  placeholderText: string;
  heartColor: string;
  accentBg: string;
  sendBtnBg: string;
}

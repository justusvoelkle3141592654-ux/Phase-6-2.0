export const de = {
  appName: 'Gero',
  tagline: 'Vokabeltrainer',
  nav: {
    home: 'Start',
    upload: 'Hochladen',
    packages: 'Pakete',
    learn: 'Lernen',
    settings: 'Einstellungen',
  },
  home: {
    title: 'Heute',
    empty: 'Noch keine Vokabeln. Lade ein Foto aus deinem Vokabelheft hoch oder lege ein Paket an.',
  },
  upload: {
    title: 'Vokabeln hochladen',
    empty: 'Fotografiere Seiten aus deinem Vokabelheft. Die Vokabeln werden automatisch erkannt.',
  },
  packages: {
    title: 'Vokabelpakete',
    empty: 'Noch keine Pakete.',
  },
  learn: {
    title: 'Lernen',
    empty: 'Heute ist nichts fällig.',
  },
  settings: {
    title: 'Einstellungen',
    language: 'Sprache der Oberfläche',
  },
  common: {
    loading: 'Lädt …',
    comingSoon: 'Kommt in einem der nächsten Schritte.',
  },
};

/** Shape every translation must match. */
export type Messages = typeof de;

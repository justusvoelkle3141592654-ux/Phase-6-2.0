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
  auth: {
    loginTitle: 'Anmelden',
    registerTitle: 'Account anlegen',
    loginIntro: 'Melde dich mit deinem Account an.',
    registerIntro: 'Den Registrierungscode bekommst du von der Person, die den Server betreibt.',
    email: 'E-Mail',
    password: 'Passwort',
    passwordHint: 'Mindestens 8 Zeichen.',
    registrationCode: 'Registrierungscode',
    login: 'Anmelden',
    register: 'Account anlegen',
    toRegister: 'Noch keinen Account? Registrieren',
    toLogin: 'Schon registriert? Anmelden',
    errors: {
      invalid_credentials: 'E-Mail oder Passwort stimmt nicht.',
      invalid_registration_code: 'Der Registrierungscode stimmt nicht.',
      email_taken: 'Für diese E-Mail gibt es schon einen Account.',
      validation_error: 'Bitte prüfe deine Eingaben.',
      rate_limited: 'Zu viele Versuche. Bitte warte eine Minute.',
      wrong_password: 'Das aktuelle Passwort stimmt nicht.',
      network_error: 'Der Server ist nicht erreichbar.',
      unknown: 'Etwas ist schiefgelaufen. Bitte versuche es noch einmal.',
    },
  },
  account: {
    title: 'Account',
    signedInAs: 'Angemeldet als',
    name: 'Name',
    save: 'Speichern',
    saved: 'Gespeichert.',
    logout: 'Abmelden',
    changePassword: 'Passwort ändern',
    currentPassword: 'Aktuelles Passwort',
    newPassword: 'Neues Passwort',
    passwordChanged: 'Passwort geändert. Andere Geräte wurden abgemeldet.',
  },
  common: {
    retry: 'Nochmal versuchen',
    loading: 'Lädt …',
    comingSoon: 'Kommt in einem der nächsten Schritte.',
  },
};

/** Shape every translation must match. */
export type Messages = typeof de;

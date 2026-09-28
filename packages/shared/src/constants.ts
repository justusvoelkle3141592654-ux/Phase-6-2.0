/** Name of the app as shown in the UI and API. */
export const APP_NAME = 'Vokabeltrainer';

/** Supported UI languages. */
export const UI_LANGUAGES = ['de', 'en'] as const;
export type UiLanguage = (typeof UI_LANGUAGES)[number];

/** Number of learning stages until a vocabulary item counts as learned. */
export const STAGE_COUNT = 6;

/** Name of the browser session cookie. */
export const SESSION_COOKIE = 'vokabeltrainer_session';

/** Sessions expire after this many days without use. */
export const SESSION_DAYS = 90;

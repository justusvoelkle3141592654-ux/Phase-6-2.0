import type { Messages } from './de';

export const en: Messages = {
  appName: 'Gero',
  tagline: 'Vocabulary trainer',
  nav: {
    home: 'Home',
    upload: 'Upload',
    packages: 'Decks',
    learn: 'Learn',
    settings: 'Settings',
  },
  home: {
    title: 'Today',
    empty: 'No vocabulary yet. Upload a photo from your vocabulary notebook or create a deck.',
  },
  upload: {
    title: 'Upload vocabulary',
    empty:
      'Take photos of pages from your vocabulary notebook. The words are recognised automatically.',
  },
  packages: {
    title: 'Vocabulary decks',
    empty: 'No decks yet.',
  },
  learn: {
    title: 'Learn',
    empty: 'Nothing is due today.',
  },
  settings: {
    title: 'Settings',
    language: 'Interface language',
  },
  auth: {
    loginTitle: 'Sign in',
    registerTitle: 'Create account',
    loginIntro: 'Sign in with your account.',
    registerIntro: 'You get the registration code from the person who runs the server.',
    email: 'Email',
    password: 'Password',
    passwordHint: 'At least 8 characters.',
    registrationCode: 'Registration code',
    login: 'Sign in',
    register: 'Create account',
    toRegister: 'No account yet? Register',
    toLogin: 'Already registered? Sign in',
    errors: {
      invalid_credentials: 'Email or password is incorrect.',
      invalid_registration_code: 'The registration code is incorrect.',
      email_taken: 'An account with this email already exists.',
      validation_error: 'Please check your input.',
      rate_limited: 'Too many attempts. Please wait a minute.',
      wrong_password: 'The current password is incorrect.',
      network_error: 'The server cannot be reached.',
      unknown: 'Something went wrong. Please try again.',
    },
  },
  account: {
    title: 'Account',
    signedInAs: 'Signed in as',
    name: 'Name',
    save: 'Save',
    saved: 'Saved.',
    logout: 'Sign out',
    changePassword: 'Change password',
    currentPassword: 'Current password',
    newPassword: 'New password',
    passwordChanged: 'Password changed. Other devices were signed out.',
  },
  common: {
    retry: 'Try again',
    loading: 'Loading …',
    comingSoon: 'Coming in one of the next steps.',
  },
};

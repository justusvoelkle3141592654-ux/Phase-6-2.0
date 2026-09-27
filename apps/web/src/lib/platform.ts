import { Capacitor } from '@capacitor/core';
import { Preferences } from '@capacitor/preferences';

/** True inside the Android app (Capacitor), false in the browser. */
export const isApp = Capacitor.isNativePlatform();

let serverUrl: string | null = null;
let token: string | null = null;

/** Loads server address and token from the app's storage before the first render. */
export async function initPlatform(): Promise<void> {
  if (!isApp) return;
  serverUrl = (await Preferences.get({ key: 'serverUrl' })).value;
  token = (await Preferences.get({ key: 'token' })).value;
}

export function getServerUrl(): string | null {
  return serverUrl;
}

export async function setServerUrl(url: string): Promise<void> {
  serverUrl = url;
  await Preferences.set({ key: 'serverUrl', value: url });
  await setToken(null);
}

export function getToken(): string | null {
  return token;
}

export async function setToken(value: string | null): Promise<void> {
  token = value;
  if (!isApp) return;
  if (value) await Preferences.set({ key: 'token', value });
  else await Preferences.remove({ key: 'token' });
}

/** "http://192.168.1.20:3000/" → "http://192.168.1.20:3000"; adds http:// when missing. */
export function normalizeServerUrl(input: string): string {
  const trimmed = input.trim().replace(/\/+$/, '');
  return /^https?:\/\//i.test(trimmed) ? trimmed : `http://${trimmed}`;
}

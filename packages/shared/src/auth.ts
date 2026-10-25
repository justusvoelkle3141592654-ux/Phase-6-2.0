import { z } from 'zod';
import { UI_LANGUAGES } from './constants';

export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 256;

const email = z.string().trim().toLowerCase().max(254).pipe(z.email());

const password = z.string().min(PASSWORD_MIN_LENGTH).max(PASSWORD_MAX_LENGTH);

/** "web" gets a session cookie, "app" (Android) gets a bearer token in the response. */
export const authClientSchema = z.enum(['web', 'app']).default('web');

const timezone = z
  .string()
  .max(64)
  .refine((tz) => {
    try {
      new Intl.DateTimeFormat('en', { timeZone: tz });
      return true;
    } catch {
      return false;
    }
  }, 'invalid_timezone');

export const registerSchema = z.object({
  email,
  password,
  registrationCode: z.string().trim().min(1).max(100).optional(),
  uiLanguage: z.enum(UI_LANGUAGES).optional(),
  timezone: timezone.optional(),
  client: authClientSchema,
});
export type RegisterInput = z.input<typeof registerSchema>;

export const loginSchema = z.object({
  email,
  password: z.string().min(1).max(PASSWORD_MAX_LENGTH),
  client: authClientSchema,
});
export type LoginInput = z.input<typeof loginSchema>;

export const updateProfileSchema = z
  .object({
    name: z.string().trim().max(80),
    uiLanguage: z.enum(UI_LANGUAGES),
    timezone,
  })
  .partial();
export type UpdateProfileInput = z.input<typeof updateProfileSchema>;

export const changePasswordSchema = z.object({
  currentPassword: z.string().min(1).max(PASSWORD_MAX_LENGTH),
  newPassword: password,
});
export type ChangePasswordInput = z.input<typeof changePasswordSchema>;

export const deleteAccountSchema = z.object({
  password: z.string().min(1).max(PASSWORD_MAX_LENGTH),
});
export type DeleteAccountInput = z.input<typeof deleteAccountSchema>;

export const USER_ROLES = ['admin', 'user'] as const;
export type UserRole = (typeof USER_ROLES)[number];

/** Superuser account, created on every start if missing. */
export const SUPERUSER_EMAIL = 'admin@wordflow.com';
export const SUPERUSER_DEFAULT_PASSWORD = 'admin';

/** Superuser: create an account. */
export const adminCreateUserSchema = z.object({
  email,
  name: z.string().trim().max(80).default(''),
  password,
});
export type AdminCreateUserInput = z.input<typeof adminCreateUserSchema>;

/** Superuser: change an account; a new password signs the account out everywhere. */
export const adminUpdateUserSchema = z
  .object({
    email,
    name: z.string().trim().max(80),
    password,
  })
  .partial();
export type AdminUpdateUserInput = z.input<typeof adminUpdateUserSchema>;

export interface AdminUserDto {
  id: number;
  email: string;
  name: string;
  role: UserRole;
  createdAt: string;
}

/**
 * Superuser: GitHub source of the update button. The repository may be given as
 * `owner/name` or as a GitHub URL (`https://github.com/owner/name(.git)`).
 */
export const updateSourceSchema = z.object({
  repo: z
    .string()
    .trim()
    .transform((v) =>
      v
        .replace(/^(https?:\/\/)?(www\.)?github\.com\//i, '')
        .replace(/\.git$/i, '')
        .replace(/\/+$/, ''),
    )
    .pipe(z.string().regex(/^[A-Za-z0-9-]+\/[A-Za-z0-9._-]+$/)),
  branch: z
    .string()
    .trim()
    .min(1)
    .max(200)
    .regex(/^[A-Za-z0-9._\/-]+$/)
    .refine((v) => !v.includes('..') && !v.startsWith('/') && !v.endsWith('/')),
  /** GitHub read token: left out = keep, empty = remove. */
  token: z
    .string()
    .trim()
    .max(255)
    .regex(/^[A-Za-z0-9_]*$/)
    .optional(),
});
export type UpdateSourceInput = z.input<typeof updateSourceSchema>;
export type UpdateSource = Omit<z.output<typeof updateSourceSchema>, 'token'>;

export interface UpdateStatusDto {
  /** GitHub repository (owner/name) and branch the update installs from. */
  repo: string;
  branch: string;
  /** A GitHub token is stored (the token itself is never sent). */
  hasToken: boolean;
  /** Commit the installed code came from; null if never updated via the app. */
  current: string | null;
  latest: string | null;
  available: boolean;
  /** An update is running right now. */
  running: boolean;
  /** Last lines of the update log. */
  log: string;
  error?: string;
}

export interface UserDto {
  id: number;
  email: string;
  name: string;
  role: UserRole;
  uiLanguage: (typeof UI_LANGUAGES)[number];
  timezone: string;
  /**
   * Always true. Only for older web and app versions, which show their setup
   * wizard while this is not true.
   */
  setupCompleted: true;
}

export interface AuthResponse {
  user: UserDto;
  /** Only set for `client: "app"`. */
  token?: string;
}

/** Error codes returned as `{ error: code }`. */
export type ApiErrorCode =
  | 'validation_error'
  | 'unauthorized'
  | 'invalid_credentials'
  | 'invalid_registration_code'
  | 'email_taken'
  | 'registration_disabled'
  | 'forbidden'
  | 'invalid_backup'
  | 'wrong_password'
  | 'forbidden_origin'
  | 'rate_limited'
  | 'not_found'
  | 'internal_error';

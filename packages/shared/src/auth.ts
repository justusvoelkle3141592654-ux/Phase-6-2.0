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

export interface UserDto {
  id: number;
  email: string;
  name: string;
  uiLanguage: (typeof UI_LANGUAGES)[number];
  timezone: string;
  setupCompleted: boolean;
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
  | 'wrong_password'
  | 'forbidden_origin'
  | 'rate_limited'
  | 'not_found'
  | 'internal_error';

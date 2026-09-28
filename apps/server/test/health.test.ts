import { describe, expect, it } from 'vitest';
import { testApp } from './helpers';

describe('GET /api/health', () => {
  it('antwortet mit Status ok', async () => {
    const app = await testApp();
    const res = await app.inject({ method: 'GET', url: '/api/health' });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toMatchObject({ status: 'ok', name: 'Vokabeltrainer' });
    await app.close();
  });

  it('liefert 404 als JSON für unbekannte API-Routen', async () => {
    const app = await testApp();
    const res = await app.inject({ method: 'GET', url: '/api/gibt-es-nicht' });
    expect(res.statusCode).toBe(404);
    expect(res.json()).toEqual({ error: 'not_found' });
    await app.close();
  });
});

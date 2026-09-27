import http from 'node:http';
import type { AddressInfo } from 'node:net';

export interface MockRequest {
  method: string;
  url: string;
  headers: http.IncomingHttpHeaders;
  body: any;
}

type Handler = (req: MockRequest, res: http.ServerResponse) => void | Promise<void>;

/** Tiny HTTP server that records requests; handlers are keyed by "METHOD /path". */
export async function mockServer(handlers: Record<string, Handler>) {
  const requests: MockRequest[] = [];
  const server = http.createServer(async (req, res) => {
    const chunks: Buffer[] = [];
    for await (const c of req) chunks.push(c as Buffer);
    const raw = Buffer.concat(chunks).toString('utf8');
    const url = req.url ?? '';
    const record: MockRequest = {
      method: req.method ?? 'GET',
      url,
      headers: req.headers,
      body: raw ? JSON.parse(raw) : undefined,
    };
    requests.push(record);
    const handler = handlers[`${record.method} ${url.split('?')[0]}`];
    if (!handler) {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: { message: 'not found' } }));
      return;
    }
    await handler(record, res);
  });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address() as AddressInfo;
  return {
    url: `http://127.0.0.1:${port}`,
    requests,
    close: () => new Promise<void>((resolve) => server.close(() => resolve())),
  };
}

export function json(res: http.ServerResponse, status: number, body: unknown) {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
}

/** Writes `parts` one by one with a small pause, like a streaming model. */
export async function stream(
  res: http.ServerResponse,
  contentType: string,
  parts: string[],
  delayMs = 5,
) {
  res.writeHead(200, { 'Content-Type': contentType });
  for (const part of parts) {
    res.write(part);
    await new Promise((r) => setTimeout(r, delayMs));
  }
  res.end();
}

export const sse = (events: unknown[]) => [
  ...events.map((e) => `data: ${JSON.stringify(e)}\n\n`),
  'data: [DONE]\n\n',
];

/** OpenAI-style streaming reply. */
export function openAiStream(text: string, usage?: number) {
  const words = text.split(/(?<= )/);
  return sse([
    ...words.map((w) => ({ choices: [{ delta: { content: w } }] })),
    ...(usage ? [{ choices: [], usage: { completion_tokens: usage } }] : []),
  ]);
}

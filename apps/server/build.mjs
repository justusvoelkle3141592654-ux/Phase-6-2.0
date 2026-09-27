// Bundles the server into dist/index.js. Workspace packages (@gero/shared) are
// inlined; all npm dependencies stay external and are loaded from node_modules.
import { build } from 'esbuild';
import { readFileSync, rmSync, cpSync, existsSync } from 'node:fs';

const pkg = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8'));
const external = Object.keys(pkg.dependencies ?? {}).filter((name) => !name.startsWith('@gero/'));

rmSync('dist', { recursive: true, force: true });
await build({
  entryPoints: ['src/index.ts'],
  outfile: 'dist/index.js',
  bundle: true,
  platform: 'node',
  format: 'esm',
  target: 'node22',
  sourcemap: true,
  external,
  banner: {
    // Allow bundled CommonJS helpers to use require().
    js: "import { createRequire as __cr } from 'node:module'; const require = __cr(import.meta.url);",
  },
});
if (existsSync('drizzle')) cpSync('drizzle', 'dist/drizzle', { recursive: true });
console.log('Server gebaut: dist/index.js');

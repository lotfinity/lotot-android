import { readFile, writeFile, unlink } from 'node:fs/promises';
import { resolve } from 'node:path';

const outDir = resolve(import.meta.dirname, '../../androbd/src/main/assets/lotot');
const htmlPath = resolve(outDir, 'index.html');
const cssPath = resolve(outDir, 'lotot.css');
const [html, css] = await Promise.all([
  readFile(htmlPath, 'utf8'),
  readFile(cssPath, 'utf8'),
]);

let bundled = html.replace(
  '<script type="module" crossorigin src="./lotot.js"></script>',
  '<script defer src="./lotot.js"></script>',
);
bundled = bundled.replace(
  '<link rel="stylesheet" crossorigin href="./lotot.css">',
  `<style>${css}</style>`,
);
if (bundled === html || bundled.includes('lotot.css')) {
  throw new Error('Expected Vite script/style tags were not transformed.');
}
await writeFile(htmlPath, bundled);
await unlink(cssPath);

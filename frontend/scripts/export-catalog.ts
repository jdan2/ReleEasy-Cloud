/*
 * Exporta el catálogo de plantillas (core/templates/template-catalog.ts) a JSON para la API, que lo
 * sirve en GET /api/templates y valida las opciones con las mismas reglas (D-25).
 *
 * Uso: npm run export:catalog (vuelve a ejecutarlo cada vez que cambies el catálogo).
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { TEMPLATES } from '../src/app/core/templates/template-catalog';

const output = process.argv.at(-1);
if (!output?.endsWith('.json')) {
  throw new Error('Indica la ruta del JSON de salida');
}
mkdirSync(dirname(output), { recursive: true });
writeFileSync(output, JSON.stringify(TEMPLATES, null, 2) + '\n');
console.log(`Catálogo exportado (${TEMPLATES.length} plantillas) en ${output}`);

import { defineConfig } from 'prisma/config';
import { config } from 'dotenv';
import { resolve } from 'node:path';
import { existsSync } from 'node:fs';
import { dirname } from 'node:path';
import { createRequire } from 'node:module';

config({ path: resolve(process.cwd(), '../../.env') });
if (process.platform === 'win32') {
  const require = createRequire(import.meta.url);
  const engines = dirname(require.resolve('@prisma/engines/package.json'));
  const queryEngine = resolve(engines, 'query_engine-windows.dll.node');
  const schemaEngine = resolve(engines, 'schema-engine-windows.exe');
  if (existsSync(queryEngine)) process.env.PRISMA_QUERY_ENGINE_LIBRARY ||= queryEngine;
  if (existsSync(schemaEngine)) process.env.PRISMA_SCHEMA_ENGINE_BINARY ||= schemaEngine;
}
export default defineConfig({
  schema: 'prisma/schema.prisma',
  migrations: { path: 'prisma/migrations' },
  datasource: { url: process.env.DATABASE_URL || '' },
});

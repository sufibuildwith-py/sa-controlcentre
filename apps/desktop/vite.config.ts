/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite';
import { readFileSync } from 'node:fs';
import react from '@vitejs/plugin-react';
export default defineConfig(({ command, mode }) => {
  const env = { ...loadEnv(mode, process.cwd(), ''), ...process.env };
  if (command === 'build' && mode !== 'demo' && mode !== 'desktop') {
    if (env.VITE_APP_MODE === 'demo') throw new Error('Use an explicit demo build for demo credentials.');
    const url = new URL(env.VITE_API_URL || 'https://api.saproduction.in/api/v1');
    if (url.protocol !== 'https:' || url.username || url.password) throw new Error('Production API must use HTTPS without embedded credentials.');
    const config = JSON.parse(readFileSync(new URL('./src-tauri/tauri.conf.json', import.meta.url), 'utf8'));
    const connect = config.app.security.csp.split(';').find((part: string) => part.trim().startsWith('connect-src '));
    if (!connect?.split(/\s+/).includes(url.origin) || connect.includes('*')) throw new Error('Add the exact production API origin to the packaged Tauri CSP before building.');
  }
  return {plugins:[react()],clearScreen:false,server:{port:1420,strictPort:true,watch:{ignored:['**/src-tauri/**']}},test:{environment:'jsdom',setupFiles:['./src/test/setup.ts'],css:true,include:['src/**/*.test.{ts,tsx}']}};
});

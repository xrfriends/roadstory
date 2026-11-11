import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { loadEnv } from 'vite';

export default defineConfig(({ mode }) => {
  const environment = loadEnv(mode, '.', 'VITE_');
  const port = environment.VITE_DEV_LIBRARY_SYNC_API_PORT ?? '8080';
  const proxy = mode === 'development' && environment.VITE_DEV_LIBRARY_SYNC === 'true' &&
    /^[1-9][0-9]{0,4}$/.test(port) && Number(port) <= 65535
    ? { '/v1/users/me/library': { target: `http://127.0.0.1:${port}` } }
    : undefined;
  return {
    plugins: [react()],
    server: { proxy },
    test: { environment: 'jsdom', globals: true, setupFiles: './tests/setup.ts' },
  };
});

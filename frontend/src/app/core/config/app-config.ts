import { InjectionToken } from '@angular/core';

/**
 * Configuración en tiempo de ejecución del portal, leída de `config.json` al arrancar.
 *
 * No va incrustada en el build: el mismo artefacto sirve para cualquier entorno, basta con
 * cambiar `config.json` en el servidor donde se publica.
 */
export interface AppConfig {
  /** URL base de la API de la plataforma, sin barra final (p. ej. `http://localhost:8080`). */
  readonly apiUrl: string;
}

export const APP_CONFIG = new InjectionToken<AppConfig>('APP_CONFIG');

/** Valida y normaliza el contenido de `config.json`. */
export function parseAppConfig(raw: unknown): AppConfig {
  const apiUrl = (raw as { apiUrl?: unknown } | null)?.apiUrl;
  if (typeof apiUrl !== 'string' || apiUrl.trim() === '') {
    throw new Error('config.json debe contener "apiUrl" (texto no vacío)');
  }
  return { apiUrl: apiUrl.trim().replace(/\/+$/, '') };
}

/** Descarga `config.json` (sin caché, para que un cambio se note al recargar). */
export async function loadAppConfig(url = 'config.json'): Promise<AppConfig> {
  const response = await fetch(url, { cache: 'no-store' });
  if (!response.ok) {
    throw new Error(`No se pudo cargar ${url} (HTTP ${response.status})`);
  }
  return parseAppConfig(await response.json());
}

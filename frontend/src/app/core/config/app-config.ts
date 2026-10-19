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
  /**
   * Modo demo: si es `true`, el portal no llama a la API y responde con datos simulados en memoria
   * (ver `core/mock`). Sirve para enseñar el portal sin arrancar el backend.
   */
  readonly mockApi: boolean;
  /**
   * Nombre corto (slug) de la GitHub App de ReleEasy, el que aparece en `github.com/apps/<slug>`. Con él,
   * "Instalar en GitHub" lleva a GitHub de verdad. Si es `null`, la instalación se simula (modo demo o
   * API con `RELEASY_GITHUB_MODE=mock`).
   */
  readonly githubAppSlug: string | null;
  /** Client ID de la GitHub App: permite autorizar el usuario sin reinstalar la app (D-26). */
  readonly githubClientId: string | null;
}

export const APP_CONFIG = new InjectionToken<AppConfig>('APP_CONFIG');

/** Valida y normaliza el contenido de `config.json`. */
export function parseAppConfig(raw: unknown): AppConfig {
  const { apiUrl, mockApi, githubAppSlug, githubClientId } = (raw ?? {}) as Record<string, unknown>;
  if (typeof apiUrl !== 'string' || apiUrl.trim() === '') {
    throw new Error('config.json debe contener "apiUrl" (texto no vacío)');
  }
  return {
    apiUrl: apiUrl.trim().replace(/\/+$/, ''),
    mockApi: mockApi === true,
    githubAppSlug: optionalText(githubAppSlug),
    githubClientId: optionalText(githubClientId),
  };
}

function optionalText(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value.trim() : null;
}

/** Descarga `config.json` (sin caché, para que un cambio se note al recargar). */
export async function loadAppConfig(url = 'config.json'): Promise<AppConfig> {
  const response = await fetch(url, { cache: 'no-store' });
  if (!response.ok) {
    throw new Error(`No se pudo cargar ${url} (HTTP ${response.status})`);
  }
  return parseAppConfig(await response.json());
}

/*
 * Ida y vuelta a GitHub para instalar o autorizar la GitHub App (D-26).
 *
 * Antes de salir hacia GitHub se genera un `state` aleatorio y se guarda en la pestaña. GitHub lo
 * devuelve tal cual en la URL de vuelta, y la página de vuelta solo continúa si coincide. Así nadie
 * puede colar al cliente una vuelta de GitHub que él no inició (CSRF).
 */

const STATE_KEY = 'releasy.github-state';

/** Instalar la app: el cliente elige cuenta y repositorios, y autoriza en la misma pantalla. */
export function githubInstallUrl(appSlug: string): string {
  return `https://github.com/apps/${encodeURIComponent(appSlug)}/installations/new?state=${newState()}`;
}

/** Solo autorizar el usuario, para quien ya tenía la app instalada. */
export function githubAuthorizeUrl(clientId: string): string {
  return `https://github.com/login/oauth/authorize?client_id=${encodeURIComponent(clientId)}&state=${newState()}`;
}

/** Comprueba el `state` devuelto por GitHub. Solo vale una vez. */
export function consumeGithubState(received: string | null): boolean {
  const expected = sessionStorage.getItem(STATE_KEY);
  sessionStorage.removeItem(STATE_KEY);
  return expected !== null && received === expected;
}

function newState(): string {
  const state = crypto.randomUUID();
  sessionStorage.setItem(STATE_KEY, state);
  return state;
}

import { Release, Tenant } from '../core/api/models';

/** Intervalo mientras hay algo en curso (release PENDING/RUNNING o cuenta CREATING). */
export const ACTIVE_POLL_MS = 3_000;
/**
 * Intervalo en reposo. Sirve para que aparezcan solos los releases nuevos que dispara un webhook
 * (ver decisión D-14).
 */
export const IDLE_POLL_MS = 15_000;

export function hasReleaseInProgress(releases: readonly Release[]): boolean {
  return releases.some((r) => r.status === 'PENDING' || r.status === 'RUNNING');
}

export function hasAccountInCreation(tenants: readonly Tenant[]): boolean {
  return tenants.some((t) => t.status === 'CREATING');
}

export function releasePollInterval(releases: readonly Release[]): number {
  return hasReleaseInProgress(releases) ? ACTIVE_POLL_MS : IDLE_POLL_MS;
}

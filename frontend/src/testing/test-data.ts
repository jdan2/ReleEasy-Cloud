import { App, Release, ReleaseStatus, Session } from '../app/core/api/models';
import { AppConfig } from '../app/core/config/app-config';

/** Datos de prueba compartidos por los specs. */
export const TEST_CONFIG: AppConfig = { apiUrl: 'http://api.test', mockApi: false };

export function aSession(role: 'ADMIN' | 'CLIENT' = 'CLIENT'): Session {
  return {
    token: 'token-123',
    user: { id: 'u-1', email: 'it@acme.es', name: 'Acme', role, tenantId: role === 'CLIENT' ? 't-1' : null },
  };
}

export function anApp(overrides: Partial<App> = {}): App {
  return {
    id: 'a-1',
    tenantId: 't-1',
    name: 'tienda',
    templateId: 'angular-static',
    repository: 'acme/tienda',
    branch: 'master',
    options: { front_alarms: false, front_rum: false, waf: false, custom_domain: false, domain_name: '', notification_email: '' },
    usage: { pageViewsPerMonth: 10_000, pageWeightMb: 1 },
    url: 'https://tienda-acme.apps.releasy.local',
    createdAt: '2026-10-17T10:00:00Z',
    updatedAt: '2026-10-17T10:00:00Z',
    ...overrides,
  };
}

export function aRelease(status: ReleaseStatus, overrides: Partial<Release> = {}): Release {
  return {
    id: 'r-20261017100000-abc123',
    appId: 'a-1',
    commitSha: '9f2c1e0b7a6d5c4b3a291807f6e5d4c3b2a19087',
    commitMessage: 'Merge pull request #7',
    status,
    url: null,
    errorMessage: null,
    createdAt: '2026-10-17T10:00:00Z',
    updatedAt: '2026-10-17T10:00:05Z',
    ...overrides,
  };
}

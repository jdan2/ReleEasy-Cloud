import { App, Release, ReleaseStatus, Tenant } from '../app/core/api/models';
import { AppConfig } from '../app/core/config/app-config';

/** Datos de prueba compartidos por los specs. */
export const TEST_CONFIG: AppConfig = { apiUrl: 'http://api.test' };

export function aTenant(overrides: Partial<Tenant> = {}): Tenant {
  return {
    id: 't-1',
    name: 'Acme',
    email: 'it@acme.es',
    status: 'ACTIVE',
    accountId: '123456789012',
    roleArn: 'arn:aws:iam::123456789012:role/OrganizationAccountAccessRole',
    failureReason: null,
    createdAt: '2026-10-17T10:00:00Z',
    updatedAt: '2026-10-17T10:00:00Z',
    ...overrides,
  };
}

export function anApp(overrides: Partial<App> = {}): App {
  return {
    id: 'a-1',
    tenantId: 't-1',
    name: 'tienda',
    repository: 'acme/tienda',
    branch: 'master',
    plan: 'BASIC',
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

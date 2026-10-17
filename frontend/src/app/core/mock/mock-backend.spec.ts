import { MockApiError, MockBackend } from './mock-backend';

describe('MockBackend (modo demo)', () => {
  beforeEach(() => vi.useFakeTimers({ now: new Date('2026-10-17T10:00:00Z') }));
  afterEach(() => vi.useRealTimers());

  it('la cuenta de un cliente nuevo pasa de CREATING a ACTIVE', () => {
    const backend = new MockBackend();
    const tenant = backend.createTenant({ name: 'Acme', email: 'it@acme.es' });
    expect(tenant.status).toBe('CREATING');

    vi.advanceTimersByTime(10_000);
    const active = backend.getTenant(tenant.id);
    expect(active.status).toBe('ACTIVE');
    expect(active.accountId).toHaveLength(12);
  });

  it('no deja registrar apps si la cuenta no está activa', () => {
    const backend = new MockBackend();
    const tenant = backend.createTenant({ name: 'Acme', email: 'it@acme.es' });

    expect(() => backend.createApp(tenant.id, { name: 'web', repository: 'acme/web', branch: 'master', plan: 'BASIC' }))
      .toThrowError(MockApiError);
  });

  it('un merge simulado genera un release que avanza hasta SUCCEEDED y fija la URL', () => {
    const backend = new MockBackend();
    const tenant = backend.createTenant({ name: 'Acme', email: 'it@acme.es' });
    vi.advanceTimersByTime(10_000);
    const app = backend.createApp(tenant.id, { name: 'web', repository: 'Acme/Web', branch: 'master', plan: 'BASIC' });
    expect(app.repository).toBe('acme/web');
    expect(app.url).toContain('apps.releasy.local');

    expect(backend.listReleases(app.id)).toHaveLength(0);
    vi.advanceTimersByTime(4_000);
    expect(backend.listReleases(app.id)[0].status).toBe('PENDING');
    vi.advanceTimersByTime(2_000);
    expect(backend.listReleases(app.id)[0].status).toBe('RUNNING');
    vi.advanceTimersByTime(12_000);
    expect(backend.listReleases(app.id)[0].status).toBe('SUCCEEDED');
    expect(backend.getApp(app.id).url).toMatch(/cloudfront\.net$/);
  });

  it('incluye datos de ejemplo para que el portal no aparezca vacío', () => {
    const backend = new MockBackend();
    expect(backend.listTenants().length).toBeGreaterThan(0);
    expect(backend.listReleases('a-demo00001').map((r) => r.status)).toContain('FAILED');
  });
});

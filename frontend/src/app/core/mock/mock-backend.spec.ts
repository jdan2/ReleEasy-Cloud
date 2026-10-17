import { CreateAppRequest } from '../api/models';
import { defaultOptions, findTemplate } from '../templates/template-catalog';
import { DEMO_ADMIN, DEMO_CLIENT, MockApiError, MockBackend } from './mock-backend';

describe('MockBackend (modo demo)', () => {
  let backend: MockBackend;

  beforeEach(() => {
    localStorage.clear();
    vi.useFakeTimers({ now: new Date('2026-10-17T10:00:00Z') });
    backend = new MockBackend();
  });

  afterEach(() => vi.useRealTimers());

  function newClient(): string {
    return backend.register({ companyName: 'Acme Comercio', email: 'it@acme.es', password: 'secreta123' }).token;
  }

  function appRequest(overrides: Partial<CreateAppRequest> = {}): CreateAppRequest {
    const template = findTemplate('angular-static')!;
    return {
      name: 'web',
      templateId: 'angular-static',
      repository: 'acme-comercio/web-corporativa',
      branch: 'master',
      options: { ...defaultOptions(template), notification_email: 'ops@acme.es' },
      usage: { pageViewsPerMonth: 1000, pageWeightMb: 1 },
      ...overrides,
    };
  }

  function expectError(fn: () => unknown, status: number): void {
    try {
      fn();
    } catch (e) {
      expect(e).toBeInstanceOf(MockApiError);
      expect((e as MockApiError).status).toBe(status);
      return;
    }
    throw new Error('Se esperaba un error');
  }

  it('registra un cliente con sus conexiones pendientes e inicia sesión', () => {
    const session = backend.register({ companyName: 'Acme', email: 'IT@acme.es', password: 'secreta123' });

    expect(session.user.role).toBe('CLIENT');
    const tenant = backend.myTenant(session.token);
    expect(tenant.aws.status).toBe('NOT_CONNECTED');
    expect(tenant.aws.externalId).toMatch(/^rle-[0-9a-f]{24}$/);
    expect(tenant.github.status).toBe('NOT_CONNECTED');
    expect(backend.login({ email: 'it@acme.es', password: 'secreta123' }).user.id).toBe(session.user.id);
    expectError(() => backend.login({ email: 'it@acme.es', password: 'mal' }), 401);
    expectError(() => backend.register({ companyName: 'Otra', email: 'it@acme.es', password: 'secreta123' }), 409);
  });

  it('conecta la cuenta AWS validando el ARN del rol', () => {
    const token = newClient();
    expectError(() => backend.connectAws(token, 'no-es-un-arn'), 400);

    const tenant = backend.connectAws(token, 'arn:aws:iam::123456789012:role/ReleEasyDeployRole');
    expect(tenant.aws.status).toBe('CONNECTED');
    expect(tenant.aws.accountId).toBe('123456789012');
  });

  it('no deja crear apps sin AWS conectada ni con un repo ajeno', () => {
    const token = newClient();
    backend.connectGithub(token);
    expectError(() => backend.createApp(token, appRequest()), 409);

    backend.connectAws(token, 'arn:aws:iam::123456789012:role/ReleEasyDeployRole');
    expectError(() => backend.createApp(token, appRequest({ repository: 'otra/empresa' })), 400);
    expectError(() => backend.createApp(token, appRequest({ branch: 'no-existe' })), 400);
  });

  it('valida las opciones de la plantilla al crear la app', () => {
    const token = newClient();
    backend.connectGithub(token);
    backend.connectAws(token, 'arn:aws:iam::123456789012:role/ReleEasyDeployRole');
    const request = appRequest();

    expectError(() => backend.createApp(token, { ...request, options: { ...request.options, notification_email: '' } }), 400);
  });

  it('una app nueva despliega su primer release y la URL pasa a CloudFront', () => {
    const token = newClient();
    backend.connectGithub(token);
    backend.connectAws(token, 'arn:aws:iam::123456789012:role/ReleEasyDeployRole');
    const app = backend.createApp(token, appRequest());
    expect(app.url).toContain('apps.releasy.local');

    expect(backend.listReleases(token, app.id)).toHaveLength(0);
    vi.advanceTimersByTime(4_000);
    expect(backend.listReleases(token, app.id)[0].status).toBe('PENDING');
    vi.advanceTimersByTime(2_000);
    expect(backend.listReleases(token, app.id)[0].status).toBe('RUNNING');
    vi.advanceTimersByTime(12_000);
    expect(backend.listReleases(token, app.id)[0].status).toBe('SUCCEEDED');
    expect(backend.getApp(token, app.id).url).toMatch(/cloudfront\.net$/);
  });

  it('un cliente no ve las apps de otro; el administrador ve todas', () => {
    const demo = backend.login(DEMO_CLIENT).token;
    const admin = backend.login(DEMO_ADMIN).token;
    const other = newClient();
    const demoApp = backend.myApps(demo)[0];

    expectError(() => backend.getApp(other, demoApp.id), 404);
    expect(backend.getApp(admin, demoApp.id).id).toBe(demoApp.id);
    expectError(() => backend.listTenants(demo), 403);
    expect(backend.listTenants(admin).length).toBeGreaterThanOrEqual(3);
  });

  it('guarda el estado en localStorage y se puede restablecer', () => {
    newClient();
    expect(new MockBackend().login({ email: 'it@acme.es', password: 'secreta123' })).toBeTruthy();

    backend.reset();
    expectError(() => new MockBackend().login({ email: 'it@acme.es', password: 'secreta123' }), 401);
  });
});

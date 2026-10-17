import { Injectable } from '@angular/core';
import { App, AppPlan, CreateAppRequest, CreateTenantRequest, Release, ReleaseStatus, Tenant } from '../api/models';

/** Error de negocio simulado; el interceptor lo convierte en una respuesta HTTP con Problem Details. */
export class MockApiError extends Error {
  constructor(
    readonly status: number,
    detail: string,
  ) {
    super(detail);
  }
}

/** Tiempos de la simulación (ms). */
const ACCOUNT_CREATION_MS = 8_000;
const RELEASE_QUEUE_MS = 2_000;
const RELEASE_RUN_MS = 12_000;
const FIRST_PUSH_DELAY_MS = 4_000;

interface MockTenant {
  data: Tenant;
  activeAt: number;
  fails: boolean;
}

interface MockRelease {
  data: Omit<Release, 'status' | 'url' | 'errorMessage' | 'updatedAt'>;
  visibleAt: number;
  runningAt: number;
  finishedAt: number;
  fails: boolean;
}

const DEMO_COMMITS = [
  'Merge pull request #12 from ferreteria/catalogo-nuevo',
  'Merge pull request #13 from ferreteria/fix-carrito',
  'Merge pull request #14 from ferreteria/pagos [fail]',
  'Merge pull request #15 from ferreteria/pagos-v2',
];

/**
 * Simula la API de la plataforma en memoria, con las mismas reglas que el backend en modo mock:
 *
 * - las cuentas AWS pasan de CREATING a ACTIVE a los pocos segundos (un email con `+fail` falla);
 * - solo se registran apps en clientes ACTIVE y con un nombre no repetido;
 * - los releases avanzan PENDING → RUNNING → SUCCEEDED, o FAILED si el commit contiene `[fail]`.
 *
 * El estado se calcula al leer, según la hora actual: no hay temporizadores. Los datos se pierden
 * al recargar la página.
 */
@Injectable({ providedIn: 'root' })
export class MockBackend {
  private readonly tenants: MockTenant[] = [];
  private readonly apps: App[] = [];
  private readonly releases: MockRelease[] = [];
  private pushCounter = 0;

  constructor() {
    this.seed();
  }

  listTenants(): Tenant[] {
    return this.tenants.map((t) => this.tenantView(t)).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }

  getTenant(id: string): Tenant {
    return this.tenantView(this.findTenant(id));
  }

  createTenant(request: CreateTenantRequest): Tenant {
    if (!request.name?.trim() || !request.email?.includes('@')) {
      throw new MockApiError(400, 'Nombre y email válidos son obligatorios');
    }
    const now = Date.now();
    const tenant: MockTenant = {
      data: {
        id: randomId('t-'),
        name: request.name.trim(),
        email: request.email.trim(),
        status: 'CREATING',
        accountId: null,
        roleArn: null,
        failureReason: null,
        createdAt: iso(now),
        updatedAt: iso(now),
      },
      activeAt: now + ACCOUNT_CREATION_MS,
      fails: request.email.includes('+fail'),
    };
    this.tenants.push(tenant);
    return this.tenantView(tenant);
  }

  listApps(tenantId: string): App[] {
    this.findTenant(tenantId);
    return this.apps.filter((a) => a.tenantId === tenantId).map((a) => this.appView(a));
  }

  getApp(id: string): App {
    return this.appView(this.findApp(id));
  }

  createApp(tenantId: string, request: CreateAppRequest): App {
    const tenant = this.tenantView(this.findTenant(tenantId));
    if (tenant.status !== 'ACTIVE') {
      throw new MockApiError(409, `La cuenta AWS del cliente ${tenantId} no está activa (${tenant.status})`);
    }
    if (this.apps.some((a) => a.tenantId === tenantId && a.name === request.name)) {
      throw new MockApiError(409, `El cliente ya tiene una aplicación llamada '${request.name}'`);
    }
    const now = Date.now();
    const app: App = {
      id: randomId('a-'),
      tenantId,
      name: request.name,
      repository: request.repository.toLowerCase(),
      branch: request.branch || 'master',
      plan: request.plan,
      url: `https://${request.name}-${slug(tenant.name)}.apps.releasy.local`,
      createdAt: iso(now),
      updatedAt: iso(now),
    };
    this.apps.push(app);
    // Como si el cliente hiciera su primer merge a la rama poco después del alta.
    this.simulatePush(app.id, FIRST_PUSH_DELAY_MS);
    return this.appView(app);
  }

  listReleases(appId: string): Release[] {
    this.findApp(appId);
    const now = Date.now();
    return this.releases
      .filter((r) => r.data.appId === appId && r.visibleAt <= now)
      .map((r) => this.releaseView(r, now))
      .sort((a, b) => b.id.localeCompare(a.id));
  }

  /** Simula el webhook de un merge a la rama de la aplicación. */
  simulatePush(appId: string, delayMs = 0): void {
    const start = Date.now() + delayMs;
    const message = DEMO_COMMITS[this.pushCounter++ % DEMO_COMMITS.length];
    this.releases.push({
      data: {
        id: `r-${compactTimestamp(start)}-${randomHex(6)}`,
        appId,
        commitSha: randomHex(40),
        commitMessage: message,
        createdAt: iso(start),
      },
      visibleAt: start,
      runningAt: start + RELEASE_QUEUE_MS,
      finishedAt: start + RELEASE_QUEUE_MS + RELEASE_RUN_MS,
      fails: message.includes('[fail]'),
    });
  }

  private tenantView(t: MockTenant): Tenant {
    if (Date.now() < t.activeAt) {
      return { ...t.data };
    }
    const updatedAt = iso(t.activeAt);
    if (t.fails) {
      return { ...t.data, status: 'FAILED', failureReason: 'EMAIL_ALREADY_EXISTS (simulado)', updatedAt };
    }
    const accountId = fakeAccountId(t.data.id);
    return {
      ...t.data,
      status: 'ACTIVE',
      accountId,
      roleArn: `arn:aws:iam::${accountId}:role/OrganizationAccountAccessRole`,
      updatedAt,
    };
  }

  /** Tras el primer release correcto, la URL provisional se sustituye por la "de CloudFront". */
  private appView(app: App): App {
    const now = Date.now();
    const deployed = this.releases.some((r) => r.data.appId === app.id && !r.fails && r.finishedAt <= now);
    return deployed ? { ...app, url: fakeCloudFrontUrl(app.id) } : { ...app };
  }

  private releaseView(r: MockRelease, now: number): Release {
    let status: ReleaseStatus = 'PENDING';
    let updatedAt = r.visibleAt;
    if (now >= r.finishedAt) {
      status = r.fails ? 'FAILED' : 'SUCCEEDED';
      updatedAt = r.finishedAt;
    } else if (now >= r.runningAt) {
      status = 'RUNNING';
      updatedAt = r.runningAt;
    }
    return {
      ...r.data,
      status,
      url: status === 'SUCCEEDED' ? fakeCloudFrontUrl(r.data.appId) : null,
      errorMessage:
        status === 'FAILED' ? 'Fallo simulado en el build: ./mvnw -B verify terminó con errores en 3 tests' : null,
      updatedAt: iso(updatedAt),
    };
  }

  private findTenant(id: string): MockTenant {
    const tenant = this.tenants.find((t) => t.data.id === id);
    if (!tenant) {
      throw new MockApiError(404, `Cliente no encontrado: ${id}`);
    }
    return tenant;
  }

  private findApp(id: string): App {
    const app = this.apps.find((a) => a.id === id);
    if (!app) {
      throw new MockApiError(404, `Aplicación no encontrada: ${id}`);
    }
    return app;
  }

  /** Datos iniciales para que el portal no aparezca vacío. */
  private seed(): void {
    const day = 24 * 60 * 60 * 1000;
    const now = Date.now();
    const addTenant = (id: string, name: string, email: string, createdAt: number, fails = false): void => {
      this.tenants.push({
        data: {
          id,
          name,
          email,
          status: 'CREATING',
          accountId: null,
          roleArn: null,
          failureReason: null,
          createdAt: iso(createdAt),
          updatedAt: iso(createdAt),
        },
        activeAt: createdAt + ACCOUNT_CREATION_MS,
        fails,
      });
    };
    addTenant('t-demo00001', 'Ferretería López', 'aws@ferreteria-lopez.es', now - 3 * day);
    addTenant('t-demo00002', 'Clínica Dental Sonrisa', 'it+fail@sonrisa.es', now - 2 * day, true);
    addTenant('t-demo00003', 'Panadería Hnos. Ruiz', 'cloud@panaderia-ruiz.es', now - 1000);

    const addApp = (id: string, name: string, repository: string, plan: AppPlan, createdAt: number): void => {
      this.apps.push({
        id,
        tenantId: 't-demo00001',
        name,
        repository,
        branch: 'master',
        plan,
        url: `https://${name}-ferreteria-lopez.apps.releasy.local`,
        createdAt: iso(createdAt),
        updatedAt: iso(createdAt),
      });
    };
    addApp('a-demo00001', 'tienda', 'ferreteria-lopez/tienda-online', 'STANDARD', now - 3 * day + 3600_000);
    addApp('a-demo00002', 'intranet', 'ferreteria-lopez/intranet', 'BASIC', now - day);

    const pastRelease = (appId: string, start: number, message: string, fails: boolean): void => {
      this.releases.push({
        data: {
          id: `r-${compactTimestamp(start)}-${randomHex(6)}`,
          appId,
          commitSha: randomHex(40),
          commitMessage: message,
          createdAt: iso(start),
        },
        visibleAt: start,
        runningAt: start + 20_000,
        finishedAt: start + 6 * 60_000,
        fails,
      });
    };
    pastRelease('a-demo00001', now - 3 * day + 2 * 3600_000, 'Merge pull request #1 from ferreteria/primera-version', false);
    pastRelease('a-demo00001', now - 2 * day, 'Merge pull request #7 from ferreteria/stock [fail]', true);
    pastRelease('a-demo00001', now - day, 'Merge pull request #9 from ferreteria/stock-fix', false);
  }
}

function iso(ms: number): string {
  return new Date(ms).toISOString();
}

function compactTimestamp(ms: number): string {
  return iso(ms).replace(/[-:T]/g, '').slice(0, 14);
}

function randomHex(length: number): string {
  let out = '';
  while (out.length < length) {
    out += Math.floor(Math.random() * 16).toString(16);
  }
  return out;
}

function randomId(prefix: string): string {
  return prefix + randomHex(10);
}

function slug(text: string): string {
  return text
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-+|-+$)/g, '')
    .slice(0, 20)
    .replace(/-+$/, '');
}

function hash(text: string): number {
  let h = 0;
  for (const ch of text) {
    h = (Math.imul(31, h) + ch.charCodeAt(0)) | 0;
  }
  return Math.abs(h);
}

function fakeAccountId(seed: string): string {
  return String(hash(seed) % 1_000_000_000_000).padStart(12, '0');
}

function fakeCloudFrontUrl(appId: string): string {
  return `https://d${hash(appId).toString(16).padStart(8, '0')}${hash(appId + 'cf').toString(16).slice(0, 5)}.cloudfront.net`;
}

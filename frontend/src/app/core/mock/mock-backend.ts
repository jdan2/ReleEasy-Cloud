import { Injectable } from '@angular/core';
import {
  App,
  CreateAppRequest,
  GithubRepository,
  LoginRequest,
  RegisterRequest,
  Release,
  ReleaseStatus,
  Session,
  Tenant,
  User,
} from '../api/models';
import { TEMPLATES, findTemplate, validateOptions } from '../templates/template-catalog';
import { Template } from '../templates/template.model';

/** Error de negocio simulado; el interceptor lo convierte en una respuesta HTTP con Problem Details. */
export class MockApiError extends Error {
  constructor(
    readonly status: number,
    detail: string,
  ) {
    super(detail);
  }
}

/** Credenciales de demostración (se muestran en la pantalla de login en modo demo). */
export const DEMO_ADMIN = { email: 'admin', password: 'admin' };
export const DEMO_CLIENT = { email: 'demo@ferreteria-lopez.es', password: 'demo1234' };

/** Cuenta AWS (ficticia) de la plataforma en la que confía el rol del cliente. */
export const PLATFORM_ACCOUNT_ID = '111122223333';

const STORAGE_KEY = 'releasy.mock-state.v2';
const RELEASE_QUEUE_MS = 2_000;
const RELEASE_RUN_MS = 12_000;
const FIRST_PUSH_DELAY_MS = 4_000;
const APP_NAME = /^[a-z][a-z0-9-]{2,19}$/;
const ROLE_ARN = /^arn:aws:iam::(\d{12}):role\/[\w+=,.@/-]{1,64}$/;

interface MockUser extends User {
  password: string;
}

type MockTenant = Omit<Tenant, 'appCount'>;

interface MockRelease {
  data: Omit<Release, 'status' | 'url' | 'errorMessage' | 'updatedAt'>;
  visibleAt: number;
  runningAt: number;
  finishedAt: number;
  fails: boolean;
}

interface MockState {
  users: MockUser[];
  tenants: MockTenant[];
  apps: App[];
  releases: MockRelease[];
  pushCounter: number;
}

const DEMO_COMMITS = [
  'Merge pull request #12 from equipo/catalogo-nuevo',
  'Merge pull request #13 from equipo/fix-carrito',
  'Merge pull request #14 from equipo/pagos [fail]',
  'Merge pull request #15 from equipo/pagos-v2',
];

/**
 * Simula la API de la plataforma en el navegador (modo demo, `mockApi: true`).
 *
 * Aplica las mismas reglas que tendrá el backend: roles, conexión con la cuenta AWS del cliente
 * mediante un rol con External ID, instalación de la GitHub App, validación de las opciones de la
 * plantilla y avance de los releases (PENDING → RUNNING → SUCCEEDED/FAILED). El estado se guarda en
 * `localStorage`, así que sobrevive a una recarga de la página. Los estados que dependen del tiempo
 * se calculan al leer, sin temporizadores.
 */
@Injectable({ providedIn: 'root' })
export class MockBackend {
  private state: MockState = loadState() ?? seedState();

  // ---------- Autenticación ----------

  login(request: LoginRequest): Session {
    const email = request.email?.trim().toLowerCase();
    const user = this.state.users.find((u) => u.email === email);
    if (!user || user.password !== request.password) {
      throw new MockApiError(401, 'Email o contraseña incorrectos');
    }
    return session(user);
  }

  register(request: RegisterRequest): Session {
    const email = request.email?.trim().toLowerCase() ?? '';
    const companyName = request.companyName?.trim() ?? '';
    if (companyName.length < 2 || !email.includes('@') || (request.password ?? '').length < 8) {
      throw new MockApiError(400, 'Empresa, email válido y contraseña de al menos 8 caracteres son obligatorios');
    }
    if (this.state.users.some((u) => u.email === email)) {
      throw new MockApiError(409, 'Ya existe una cuenta con ese email');
    }
    const tenant = newTenant(companyName, email);
    const user: MockUser = {
      id: randomId('u-'),
      email,
      name: companyName,
      role: 'CLIENT',
      tenantId: tenant.id,
      password: request.password,
    };
    this.state.tenants.push(tenant);
    this.state.users.push(user);
    this.save();
    return session(user);
  }

  // ---------- Cliente (/api/me) ----------

  myTenant(token: string | null): Tenant {
    return this.tenantView(this.clientTenant(token));
  }

  connectAws(token: string | null, roleArn: string): Tenant {
    const tenant = this.clientTenant(token);
    const match = ROLE_ARN.exec(roleArn?.trim() ?? '');
    if (!match) {
      throw new MockApiError(400, 'El ARN del rol no es válido. Formato: arn:aws:iam::<12 dígitos>:role/<nombre>');
    }
    if (roleArn.toLowerCase().includes('fail')) {
      throw new MockApiError(
        400,
        'AWS devolvió AccessDenied al asumir el rol (simulado). Comprueba que el stack de CloudFormation terminó ' +
          'y que la política de confianza exige tu External ID.',
      );
    }
    tenant.aws = { ...tenant.aws, status: 'CONNECTED', roleArn: roleArn.trim(), accountId: match[1], connectedAt: nowIso() };
    this.save();
    return this.tenantView(tenant);
  }

  disconnectAws(token: string | null): Tenant {
    const tenant = this.clientTenant(token);
    tenant.aws = { ...tenant.aws, status: 'NOT_CONNECTED', roleArn: null, accountId: null, connectedAt: null };
    this.save();
    return this.tenantView(tenant);
  }

  connectGithub(token: string | null): Tenant {
    const tenant = this.clientTenant(token);
    tenant.github = {
      status: 'CONNECTED',
      accountLogin: slug(tenant.name),
      installationId: 40_000_000 + Math.floor(Math.random() * 9_000_000),
      connectedAt: nowIso(),
    };
    this.save();
    return this.tenantView(tenant);
  }

  disconnectGithub(token: string | null): Tenant {
    const tenant = this.clientTenant(token);
    tenant.github = { status: 'NOT_CONNECTED', accountLogin: null, installationId: null, connectedAt: null };
    this.save();
    return this.tenantView(tenant);
  }

  myRepositories(token: string | null): GithubRepository[] {
    const tenant = this.clientTenant(token);
    if (tenant.github.status !== 'CONNECTED' || !tenant.github.accountLogin) {
      throw new MockApiError(409, 'Conecta primero tu cuenta de GitHub');
    }
    return repositoriesFor(tenant.github.accountLogin);
  }

  myApps(token: string | null): App[] {
    const tenant = this.clientTenant(token);
    return this.appsOf(tenant.id);
  }

  createApp(token: string | null, request: CreateAppRequest): App {
    const tenant = this.clientTenant(token);
    if (tenant.aws.status !== 'CONNECTED') {
      throw new MockApiError(409, 'Conecta primero tu cuenta de AWS');
    }
    const template = findTemplate(request.templateId);
    if (!template) {
      throw new MockApiError(400, `Plantilla desconocida: ${request.templateId}`);
    }
    if (!APP_NAME.test(request.name ?? '')) {
      throw new MockApiError(400, 'Nombre: 3-20 caracteres en minúsculas, dígitos o guiones; empieza por letra');
    }
    if (this.state.apps.some((a) => a.tenantId === tenant.id && a.name === request.name)) {
      throw new MockApiError(409, `Ya tienes una aplicación llamada '${request.name}'`);
    }
    const repository = this.myRepositories(token).find((r) => r.fullName === request.repository);
    if (!repository) {
      throw new MockApiError(400, `El repositorio ${request.repository} no está en tu instalación de GitHub`);
    }
    if (!repository.branches.includes(request.branch)) {
      throw new MockApiError(400, `La rama ${request.branch} no existe en ${request.repository}`);
    }
    const errors = validateOptions(template, request.options ?? {});
    if (errors.length > 0) {
      throw new MockApiError(400, errors.join(' '));
    }
    const now = nowIso();
    const app: App = {
      id: randomId('a-'),
      tenantId: tenant.id,
      name: request.name,
      templateId: template.id,
      repository: repository.fullName,
      branch: request.branch,
      options: structuredClone(request.options),
      usage: structuredClone(request.usage ?? {}),
      url: `https://${request.name}-${slug(tenant.name)}.apps.releasy.local`,
      createdAt: now,
      updatedAt: now,
    };
    this.state.apps.push(app);
    // Como si el cliente hiciera su primer merge a la rama poco después del alta.
    this.addPush(app.id, Date.now() + FIRST_PUSH_DELAY_MS);
    this.save();
    return this.appView(app);
  }

  // ---------- Catálogo ----------

  templates(): Template[] {
    return structuredClone([...TEMPLATES]);
  }

  template(id: string): Template {
    const template = findTemplate(id);
    if (!template) {
      throw new MockApiError(404, `Plantilla no encontrada: ${id}`);
    }
    return structuredClone(template);
  }

  // ---------- Administrador ----------

  listTenants(token: string | null): Tenant[] {
    this.requireAdmin(token);
    return this.state.tenants.map((t) => this.tenantView(t)).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }

  getTenant(token: string | null, id: string): Tenant {
    this.requireAdmin(token);
    return this.tenantView(this.findTenant(id));
  }

  listApps(token: string | null, tenantId: string): App[] {
    this.requireAdmin(token);
    return this.appsOf(this.findTenant(tenantId).id);
  }

  // ---------- Aplicaciones (propietario o administrador) ----------

  getApp(token: string | null, id: string): App {
    return this.appView(this.accessibleApp(token, id));
  }

  listReleases(token: string | null, appId: string): Release[] {
    const app = this.accessibleApp(token, appId);
    const now = Date.now();
    return this.state.releases
      .filter((r) => r.data.appId === app.id && r.visibleAt <= now)
      .map((r) => releaseView(r, now))
      .sort((a, b) => b.id.localeCompare(a.id));
  }

  /** Simula el webhook de GitHub tras un merge a la rama de la aplicación. */
  simulatePush(appId: string): void {
    this.addPush(appId, Date.now());
    this.save();
  }

  /** Vuelve a los datos de ejemplo iniciales. */
  reset(): void {
    this.state = seedState();
    this.save();
  }

  // ---------- Internos ----------

  private addPush(appId: string, start: number): void {
    const message = DEMO_COMMITS[this.state.pushCounter++ % DEMO_COMMITS.length];
    this.state.releases.push(newRelease(appId, start, message, RELEASE_QUEUE_MS, RELEASE_RUN_MS));
  }

  private userFor(token: string | null): MockUser {
    const user = this.state.users.find((u) => token === tokenFor(u));
    if (!user) {
      throw new MockApiError(401, 'Sesión no válida o caducada');
    }
    return user;
  }

  private requireAdmin(token: string | null): void {
    if (this.userFor(token).role !== 'ADMIN') {
      throw new MockApiError(403, 'Solo el administrador puede hacer esto');
    }
  }

  private clientTenant(token: string | null): MockTenant {
    const user = this.userFor(token);
    if (user.role !== 'CLIENT' || !user.tenantId) {
      throw new MockApiError(403, 'Esta operación es solo para clientes');
    }
    return this.findTenant(user.tenantId);
  }

  private accessibleApp(token: string | null, id: string): App {
    const user = this.userFor(token);
    const app = this.state.apps.find((a) => a.id === id);
    if (!app || (user.role !== 'ADMIN' && app.tenantId !== user.tenantId)) {
      throw new MockApiError(404, `Aplicación no encontrada: ${id}`);
    }
    return app;
  }

  private findTenant(id: string): MockTenant {
    const tenant = this.state.tenants.find((t) => t.id === id);
    if (!tenant) {
      throw new MockApiError(404, `Cliente no encontrado: ${id}`);
    }
    return tenant;
  }

  private appsOf(tenantId: string): App[] {
    return this.state.apps
      .filter((a) => a.tenantId === tenantId)
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
      .map((a) => this.appView(a));
  }

  private tenantView(tenant: MockTenant): Tenant {
    return structuredClone({ ...tenant, appCount: this.state.apps.filter((a) => a.tenantId === tenant.id).length });
  }

  /** Tras el primer release correcto, la URL provisional se sustituye por la "de CloudFront". */
  private appView(app: App): App {
    const now = Date.now();
    const deployed = this.state.releases.some((r) => r.data.appId === app.id && !r.fails && r.finishedAt <= now);
    return structuredClone(deployed ? { ...app, url: fakeCloudFrontUrl(app.id) } : app);
  }

  private save(): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.state));
    } catch {
      // Sin localStorage los datos solo duran hasta recargar.
    }
  }
}

// ---------- Funciones auxiliares ----------

function session(user: MockUser): Session {
  const { password: _password, ...publicUser } = user;
  return { token: tokenFor(user), user: publicUser };
}

function tokenFor(user: MockUser): string {
  return `mock-token-${user.id}`;
}

function releaseView(r: MockRelease, now: number): Release {
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
    errorMessage: status === 'FAILED' ? 'Fallo simulado en el build: ./mvnw -B verify terminó con 3 tests fallidos' : null,
    updatedAt: iso(updatedAt),
  };
}

function newRelease(appId: string, start: number, message: string, queueMs: number, runMs: number): MockRelease {
  return {
    data: {
      id: `r-${compactTimestamp(start)}-${randomHex(6)}`,
      appId,
      commitSha: randomHex(40),
      commitMessage: message,
      createdAt: iso(start),
    },
    visibleAt: start,
    runningAt: start + queueMs,
    finishedAt: start + queueMs + runMs,
    fails: message.includes('[fail]'),
  };
}

function newTenant(name: string, email: string, createdAt = Date.now()): MockTenant {
  return {
    id: randomId('t-'),
    name,
    email,
    createdAt: iso(createdAt),
    aws: {
      status: 'NOT_CONNECTED',
      externalId: `rle-${randomHex(24)}`,
      platformAccountId: PLATFORM_ACCOUNT_ID,
      roleArn: null,
      accountId: null,
      connectedAt: null,
    },
    github: { status: 'NOT_CONNECTED', accountLogin: null, installationId: null, connectedAt: null },
  };
}

function repositoriesFor(login: string): GithubRepository[] {
  return [
    { name: 'tienda-online', description: 'Tienda online (Angular + Spring Boot)', branches: ['master', 'develop'] },
    { name: 'web-corporativa', description: 'Web corporativa en Angular', branches: ['master'] },
    { name: 'api-pedidos', description: 'API de pedidos en Spring Boot', branches: ['master', 'develop', 'release/2.0'] },
    { name: 'intranet', description: 'Intranet para empleados', branches: ['main', 'master'] },
  ].map((r) => ({
    fullName: `${login}/${r.name}`,
    description: r.description,
    private: r.name !== 'web-corporativa',
    defaultBranch: r.branches[0],
    branches: r.branches,
  }));
}

/** Datos iniciales: el administrador, un cliente ya configurado y otro recién registrado. */
function seedState(): MockState {
  const day = 24 * 60 * 60 * 1000;
  const now = Date.now();
  const state: MockState = { users: [], tenants: [], apps: [], releases: [], pushCounter: 0 };

  state.users.push({ id: 'u-admin', email: DEMO_ADMIN.email, name: 'Administrador', role: 'ADMIN', tenantId: null, password: DEMO_ADMIN.password });

  const ferreteria = newTenant('Ferretería López', DEMO_CLIENT.email, now - 5 * day);
  ferreteria.aws = {
    ...ferreteria.aws,
    status: 'CONNECTED',
    roleArn: 'arn:aws:iam::444455556666:role/ReleEasyDeployRole',
    accountId: '444455556666',
    connectedAt: iso(now - 5 * day + 3600_000),
  };
  ferreteria.github = {
    status: 'CONNECTED',
    accountLogin: 'ferreteria-lopez',
    installationId: 41_234_567,
    connectedAt: iso(now - 5 * day + 2 * 3600_000),
  };
  state.tenants.push(ferreteria);
  state.users.push({
    id: 'u-demo-client',
    email: DEMO_CLIENT.email,
    name: 'Ferretería López',
    role: 'CLIENT',
    tenantId: ferreteria.id,
    password: DEMO_CLIENT.password,
  });

  const clinica = newTenant('Clínica Dental Sonrisa', 'it@clinica-sonrisa.es', now - day);
  state.tenants.push(clinica);
  state.users.push({ id: 'u-clinica', email: clinica.email, name: clinica.name, role: 'CLIENT', tenantId: clinica.id, password: 'demo1234' });

  const shop = seedApp(ferreteria.id, 'tienda', 'angular-springboot', 'ferreteria-lopez/tienda-online', now - 4 * day, {
    front_alarms: true, front_rum: true, waf: true, custom_domain: false, domain_name: '',
    api_exposure: 'alb', task_size: '0.5-1', min_tasks: 1, max_tasks: 3, cpu_target: 70,
    log_retention_days: '30', backend_alarms: true, container_insights: false, xray_tracing: true,
    sqs_queue: true, sqs_visibility_timeout: 60, env_vars: { CATALOGO_PAGINA: '24' },
    notification_email: 'ops@ferreteria-lopez.es',
  }, { pageViewsPerMonth: 120_000, pageWeightMb: 1.8, apiRequestsPerMonth: 1_500_000, responseSizeKb: 12, sqsMessagesPerMonth: 50_000 });
  const web = seedApp(ferreteria.id, 'web', 'angular-static', 'ferreteria-lopez/web-corporativa', now - 2 * day, {
    front_alarms: false, front_rum: false, waf: false, custom_domain: true, domain_name: 'www.ferreteria-lopez.es',
    notification_email: '',
  }, { pageViewsPerMonth: 15_000, pageWeightMb: 1.2 });
  state.apps.push(shop, web);

  const past = (appId: string, start: number, message: string): void => {
    state.releases.push(newRelease(appId, start, message, 20_000, 6 * 60_000));
  };
  past(shop.id, now - 4 * day + 3600_000, 'Merge pull request #1 from equipo/primera-version');
  past(shop.id, now - 3 * day, 'Merge pull request #7 from equipo/stock [fail]');
  past(shop.id, now - day, 'Merge pull request #9 from equipo/stock-fix');
  past(web.id, now - 2 * day + 3600_000, 'Merge pull request #2 from equipo/landing');
  return state;
}

function seedApp(
  tenantId: string,
  name: string,
  templateId: App['templateId'],
  repository: string,
  createdAt: number,
  options: App['options'],
  usage: App['usage'],
): App {
  return {
    id: randomId('a-'),
    tenantId,
    name,
    templateId,
    repository,
    branch: 'master',
    options,
    usage,
    url: `https://${name}-ferreteria-lopez.apps.releasy.local`,
    createdAt: iso(createdAt),
    updatedAt: iso(createdAt),
  };
}

function loadState(): MockState | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as MockState) : null;
  } catch {
    return null;
  }
}

function nowIso(): string {
  return iso(Date.now());
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
  return (
    text
      .normalize('NFD')
      .replace(/\p{M}/gu, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-+|-+$)/g, '')
      .slice(0, 20)
      .replace(/-+$/, '') || 'cliente'
  );
}

function hash(text: string): number {
  let h = 0;
  for (const ch of text) {
    h = (Math.imul(31, h) + ch.charCodeAt(0)) | 0;
  }
  return Math.abs(h);
}

function fakeCloudFrontUrl(appId: string): string {
  return `https://d${hash(appId).toString(16).padStart(8, '0')}${hash(appId + 'cf').toString(16).slice(0, 5)}.cloudfront.net`;
}

import { HttpErrorResponse, HttpInterceptorFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, delay, dematerialize, materialize, of, throwError } from 'rxjs';
import { ConnectAwsRequest, CreateAppRequest, LoginRequest, RegisterRequest } from '../api/models';
import { APP_CONFIG } from '../config/app-config';
import { MockApiError, MockBackend } from './mock-backend';

/** Latencia simulada, para que se vean los estados de carga. */
const LATENCY_MS = 250;

/**
 * Con `mockApi: true` en config.json, responde a las peticiones de la API con {@link MockBackend}
 * en lugar de enviarlas por la red.
 */
export const mockApiInterceptor: HttpInterceptorFn = (request, next) => {
  const config = inject(APP_CONFIG);
  if (!config.mockApi || !request.url.startsWith(`${config.apiUrl}/api/`)) {
    return next(request);
  }
  const backend = inject(MockBackend);
  const path = request.url.slice(config.apiUrl.length);
  return respond(() => route(backend, request, path), request.url);
};

type Handler = (backend: MockBackend, token: string | null, body: unknown, params: string[]) => unknown;

/** Tabla de rutas simuladas: método, patrón de la ruta y manejador. */
const ROUTES: readonly [string, RegExp, Handler][] = [
  ['POST', /^\/api\/auth\/login$/, (b, _t, body) => b.login(body as LoginRequest)],
  ['POST', /^\/api\/auth\/register$/, (b, _t, body) => b.register(body as RegisterRequest)],

  ['GET', /^\/api\/me\/tenant$/, (b, t) => b.myTenant(t)],
  ['POST', /^\/api\/me\/aws-connection$/, (b, t, body) => b.connectAws(t, (body as ConnectAwsRequest).roleArn)],
  ['DELETE', /^\/api\/me\/aws-connection$/, (b, t) => b.disconnectAws(t)],
  ['POST', /^\/api\/me\/github-connection$/, (b, t) => b.connectGithub(t)],
  ['DELETE', /^\/api\/me\/github-connection$/, (b, t) => b.disconnectGithub(t)],
  ['GET', /^\/api\/me\/github\/repositories$/, (b, t) => b.myRepositories(t)],
  ['GET', /^\/api\/me\/apps$/, (b, t) => b.myApps(t)],
  ['POST', /^\/api\/me\/apps$/, (b, t, body) => b.createApp(t, body as CreateAppRequest)],

  ['GET', /^\/api\/templates$/, (b) => b.templates()],
  ['GET', /^\/api\/templates\/([^/]+)$/, (b, _t, _body, [id]) => b.template(id)],

  ['GET', /^\/api\/tenants$/, (b, t) => b.listTenants(t)],
  ['GET', /^\/api\/tenants\/([^/]+)$/, (b, t, _body, [id]) => b.getTenant(t, id)],
  ['GET', /^\/api\/tenants\/([^/]+)\/apps$/, (b, t, _body, [id]) => b.listApps(t, id)],

  ['GET', /^\/api\/apps\/([^/]+)$/, (b, t, _body, [id]) => b.getApp(t, id)],
  ['GET', /^\/api\/apps\/([^/]+)\/releases$/, (b, t, _body, [id]) => b.listReleases(t, id)],
];

function route(backend: MockBackend, request: HttpRequest<unknown>, path: string): unknown {
  const token = request.headers.get('Authorization')?.replace(/^Bearer /, '') ?? null;
  for (const [method, pattern, handler] of ROUTES) {
    const match = request.method === method ? pattern.exec(path) : null;
    if (match) {
      return handler(backend, token, request.body, match.slice(1).map(decodeURIComponent));
    }
  }
  throw new MockApiError(404, `Ruta no simulada: ${request.method} ${path}`);
}

function respond(handler: () => unknown, url: string): Observable<HttpResponse<unknown>> {
  let result: Observable<HttpResponse<unknown>>;
  try {
    result = of(new HttpResponse({ status: 200, body: handler(), url }));
  } catch (e) {
    const status = e instanceof MockApiError ? e.status : 500;
    const detail = e instanceof Error ? e.message : 'Error simulado';
    result = throwError(() => new HttpErrorResponse({ status, error: { status, detail }, url }));
  }
  // materialize/dematerialize: aplica la latencia también a los errores.
  return result.pipe(materialize(), delay(LATENCY_MS), dematerialize());
}

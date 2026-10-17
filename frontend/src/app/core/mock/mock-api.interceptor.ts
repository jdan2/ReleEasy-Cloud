import { HttpErrorResponse, HttpInterceptorFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, delay, dematerialize, materialize, of, throwError } from 'rxjs';
import { CreateAppRequest, CreateTenantRequest } from '../api/models';
import { APP_CONFIG } from '../config/app-config';
import { MockApiError, MockBackend } from './mock-backend';

/** Latencia simulada, para que se vean los estados de carga. */
const LATENCY_MS = 250;

/**
 * Con `mockApi: true` en config.json, responde a las peticiones de la API con {@link MockBackend}
 * en lugar de enviarlas por la red. Cualquier usuario y contraseña sirve para entrar.
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

function route(backend: MockBackend, request: HttpRequest<unknown>, path: string): unknown {
  const get = request.method === 'GET';
  const post = request.method === 'POST';
  let match: RegExpMatchArray | null;

  if (path === '/api/tenants') {
    if (get) return backend.listTenants();
    if (post) return backend.createTenant(request.body as CreateTenantRequest);
  }
  if ((match = path.match(/^\/api\/tenants\/([^/]+)$/)) && get) {
    return backend.getTenant(decodeURIComponent(match[1]));
  }
  if ((match = path.match(/^\/api\/tenants\/([^/]+)\/apps$/))) {
    const tenantId = decodeURIComponent(match[1]);
    if (get) return backend.listApps(tenantId);
    if (post) return backend.createApp(tenantId, request.body as CreateAppRequest);
  }
  if ((match = path.match(/^\/api\/apps\/([^/]+)$/)) && get) {
    return backend.getApp(decodeURIComponent(match[1]));
  }
  if ((match = path.match(/^\/api\/apps\/([^/]+)\/releases$/)) && get) {
    return backend.listReleases(decodeURIComponent(match[1]));
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

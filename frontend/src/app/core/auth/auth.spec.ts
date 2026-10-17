import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { TEST_CONFIG, aSession } from '../../../testing/test-data';
import { APP_CONFIG } from '../config/app-config';
import { roleGuard } from './auth.guard';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('Sesión y autorización', () => {
  let auth: AuthService;
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        { provide: APP_CONFIG, useValue: TEST_CONFIG },
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('guarda la sesión y la recupera de sessionStorage', () => {
    auth.start(aSession('CLIENT'));

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.role()).toBe('CLIENT');
    expect(auth.homeUrl()).toBe('/portal');
    expect(JSON.parse(sessionStorage.getItem('releasy.session')!).token).toBe('token-123');
  });

  it('el administrador tiene su propia página de inicio', () => {
    auth.start(aSession('ADMIN'));
    expect(auth.homeUrl()).toBe('/admin');
  });

  it('el interceptor añade el token solo a las peticiones de la API', () => {
    auth.start(aSession());

    http.get(`${TEST_CONFIG.apiUrl}/api/me/apps`).subscribe();
    http.get('https://otro-dominio.test/datos').subscribe();

    expect(backend.expectOne(`${TEST_CONFIG.apiUrl}/api/me/apps`).request.headers.get('Authorization')).toBe(
      'Bearer token-123',
    );
    expect(backend.expectOne('https://otro-dominio.test/datos').request.headers.has('Authorization')).toBe(false);
  });

  it('un 401 con la sesión abierta la cierra y vuelve al login', () => {
    auth.start(aSession());
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    http.get(`${TEST_CONFIG.apiUrl}/api/me/apps`).subscribe({ error: () => undefined });
    backend.expectOne(`${TEST_CONFIG.apiUrl}/api/me/apps`).flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('roleGuard deja pasar al rol correcto y redirige a los demás a su inicio', () => {
    const check = (role: 'ADMIN' | 'CLIENT'): boolean | UrlTree =>
      TestBed.runInInjectionContext(
        () => roleGuard(role)({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot) as boolean | UrlTree,
      );

    expect((check('CLIENT') as UrlTree).toString()).toBe('/login');

    auth.start(aSession('CLIENT'));
    expect(check('CLIENT')).toBe(true);
    expect((check('ADMIN') as UrlTree).toString()).toBe('/portal');
  });
});

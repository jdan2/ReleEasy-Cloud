import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TEST_CONFIG } from '../../../testing/test-data';
import { APP_CONFIG } from '../config/app-config';
import { authInterceptor } from './auth.interceptor';
import { AuthService, encodeBasic } from './auth.service';

describe('Autenticación', () => {
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

  function login(status: number): boolean | undefined {
    let result: boolean | undefined;
    auth.login('admin', 'secreta').subscribe((ok) => (result = ok));
    const request = backend.expectOne(`${TEST_CONFIG.apiUrl}/api/tenants`);
    expect(request.request.headers.get('Authorization')).toBe(`Basic ${btoa('admin:secreta')}`);
    if (status === 200) {
      request.flush([]);
    } else {
      request.flush(null, { status, statusText: 'Unauthorized' });
    }
    return result;
  }

  it('abre la sesión si la API acepta las credenciales', () => {
    expect(login(200)).toBe(true);
    expect(auth.isAuthenticated()).toBe(true);
    expect(sessionStorage.getItem('releasy.auth')).toBe(btoa('admin:secreta'));
  });

  it('no abre la sesión si la API responde 401', () => {
    expect(login(401)).toBe(false);
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('el interceptor añade la cabecera solo a las peticiones de la API', () => {
    login(200);

    http.get(`${TEST_CONFIG.apiUrl}/api/apps/a-1`).subscribe();
    http.get('https://otro-dominio.test/datos').subscribe();

    expect(backend.expectOne(`${TEST_CONFIG.apiUrl}/api/apps/a-1`).request.headers.get('Authorization')).toBe(
      `Basic ${btoa('admin:secreta')}`,
    );
    expect(backend.expectOne('https://otro-dominio.test/datos').request.headers.has('Authorization')).toBe(false);
  });

  it('un 401 con la sesión abierta la cierra y vuelve al login', () => {
    login(200);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    http.get(`${TEST_CONFIG.apiUrl}/api/tenants`).subscribe({ error: () => undefined });
    backend.expectOne(`${TEST_CONFIG.apiUrl}/api/tenants`).flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('codifica en Base64 credenciales con caracteres no ASCII', () => {
    expect(atob(encodeBasic('admin', 'contraseña'))).toBe('admin:contraseÃ±a');
  });
});

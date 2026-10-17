import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { authInterceptor } from '../../core/auth/auth.interceptor';
import { APP_CONFIG } from '../../core/config/app-config';
import { mockApiInterceptor } from '../../core/mock/mock-api.interceptor';
import { DEMO_CLIENT, MockBackend } from '../../core/mock/mock-backend';
import { NewAppPage } from './new-app.page';

/** Prueba de integración del formulario de alta contra el backend simulado (modo demo). */
describe('NewAppPage (modo demo)', () => {
  let fixture: ComponentFixture<NewAppPage>;

  async function settle(): Promise<void> {
    await vi.advanceTimersByTimeAsync(300); // latencia simulada de la API
    fixture.detectChanges();
  }

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function select(id: string, value: string): void {
    const element = el().querySelector<HTMLSelectElement>(`#${id}`)!;
    element.value = value;
    element.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function totalText(): string {
    return el().querySelector('aside')!.textContent ?? '';
  }

  beforeEach(async () => {
    localStorage.clear();
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      imports: [NewAppPage],
      providers: [
        { provide: APP_CONFIG, useValue: { apiUrl: 'http://api.test', mockApi: true } },
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor, mockApiInterceptor])),
      ],
    });
    TestBed.inject(AuthService).start(TestBed.inject(MockBackend).login(DEMO_CLIENT));
    fixture = TestBed.createComponent(NewAppPage);
    fixture.componentRef.setInput('templateId', 'springboot-api');
    fixture.detectChanges();
    await settle();
    await settle(); // repositorios de GitHub
  });

  afterEach(() => vi.useRealTimers());

  it('construye el formulario a partir de la plantilla y muestra el coste', () => {
    expect(el().textContent).toContain('Nueva aplicación · Spring Boot');
    expect(el().textContent).toContain('Exposición de la API');
    expect(el().textContent).toContain('Mensajería');
    expect(totalText()).toContain('Total estimado');
    expect(el().querySelectorAll('#app-repo option').length).toBeGreaterThan(1);
  });

  it('muestra campos dependientes y recalcula el coste al activar opciones', async () => {
    expect(el().querySelector('#opt-sqs_visibility_timeout')).toBeNull();
    const before = totalText();

    const sqsToggle = [...el().querySelectorAll('label')].find((l) => l.textContent?.includes('Amazon SQS'))!;
    sqsToggle.querySelector('input')!.click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(el().querySelector('#opt-sqs_visibility_timeout')).not.toBeNull();
    expect(el().querySelector('#usage-sqsMessagesPerMonth')).not.toBeNull();
    expect(totalText()).not.toEqual(before);
  });

  it('crea la app y navega a su detalle', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const name = el().querySelector<HTMLInputElement>('#app-name')!;
    name.value = 'pedidos';
    name.dispatchEvent(new Event('input'));
    select('app-repo', 'ferreteria-lopez/api-pedidos');
    select('app-branch', 'develop');
    const email = el().querySelector<HTMLInputElement>('#opt-notification_email')!;
    email.value = 'ops@ferreteria-lopez.es';
    email.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    el().querySelector<HTMLButtonElement>('aside button.btn-primary')!.click();
    await settle();

    expect(navigate).toHaveBeenCalledWith(['/portal/apps', expect.stringMatching(/^a-/)]);
    const created = TestBed.inject(MockBackend).myApps(TestBed.inject(AuthService).authorizationHeader()!.slice(7));
    expect(created.find((a) => a.name === 'pedidos')).toMatchObject({ branch: 'develop', templateId: 'springboot-api' });
  });

  it('enseña los errores de validación al intentar crear sin datos', () => {
    el().querySelector<HTMLButtonElement>('aside button.btn-primary')!.click();
    fixture.detectChanges();

    expect(el().querySelector('aside [role="alert"]')!.textContent).toContain('Nombre');
  });
});

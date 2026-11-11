import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Release } from '../../core/api/models';
import { APP_CONFIG } from '../../core/config/app-config';
import { ACTIVE_POLL_MS, IDLE_POLL_MS } from '../../shared/polling-policy';
import { TEST_CONFIG, aRelease, anApp } from '../../../testing/test-data';
import { AppDetailPage } from './app-detail.page';

describe('AppDetailPage', () => {
  const appUrl = `${TEST_CONFIG.apiUrl}/api/apps/a-1`;
  let fixture: ComponentFixture<AppDetailPage>;
  let backend: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      imports: [AppDetailPage],
      providers: [
        { provide: APP_CONFIG, useValue: TEST_CONFIG },
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AppDetailPage);
    fixture.componentRef.setInput('appId', 'a-1');
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    vi.useRealTimers();
  });

  function respond(releases: Release[], url = 'https://d111.cloudfront.net'): void {
    backend.expectOne(appUrl).flush(anApp({ url }));
    backend.expectOne(`${appUrl}/releases`).flush(releases);
    fixture.detectChanges();
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('muestra el historial con estado, SHA corto, mensaje y error', () => {
    respond([
      aRelease('FAILED', { id: 'r-2', errorMessage: 'terraform apply falló' }),
      aRelease('SUCCEEDED', { id: 'r-1' }),
    ]);

    expect(text()).toContain('FAILED');
    expect(text()).toContain('SUCCEEDED');
    expect(text()).toContain('9f2c1e0');
    expect(text()).toContain('Merge pull request #7');
    expect(text()).toContain('terraform apply falló');
    expect(text()).toContain('https://d111.cloudfront.net');
  });

  it(`refresca cada ${ACTIVE_POLL_MS} ms mientras hay un release en curso`, () => {
    respond([aRelease('RUNNING')]);
    expect(text()).toContain('Despliegue en curso');

    vi.advanceTimersByTime(ACTIVE_POLL_MS);
    respond([aRelease('SUCCEEDED')]);

    expect(text()).not.toContain('Despliegue en curso');
    vi.advanceTimersByTime(ACTIVE_POLL_MS);
    backend.expectNone(appUrl);
    vi.advanceTimersByTime(IDLE_POLL_MS - ACTIVE_POLL_MS);
    respond([aRelease('SUCCEEDED')]);
  });

  function redeployButton(): HTMLButtonElement {
    const buttons = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'));
    return buttons.find((b) => /Redesplegar|Redesplegando/.test(b.textContent ?? '')) as HTMLButtonElement;
  }

  it('Redesplegar lanza el despliegue, se deshabilita mientras tanto y refresca los releases', () => {
    respond([aRelease('FAILED', { id: 'r-1' })]);
    expect(redeployButton().disabled).toBe(false);

    redeployButton().click();
    fixture.detectChanges();
    const request = backend.expectOne({ method: 'POST', url: `${appUrl}/releases` });
    expect(redeployButton().disabled).toBe(true);
    expect(redeployButton().textContent).toContain('Redesplegando');

    request.flush(aRelease('PENDING', { id: 'r-2' }));
    fixture.detectChanges();
    expect(redeployButton().disabled).toBe(false);
    expect(text()).toContain('Despliegue lanzado');
    respond([aRelease('PENDING', { id: 'r-2' }), aRelease('FAILED', { id: 'r-1' })]);
    expect(text()).toContain('PENDING');
  });

  it('Redesplegar muestra el error de la API y reactiva el botón sin refrescar', () => {
    respond([aRelease('SUCCEEDED')]);

    redeployButton().click();
    backend
      .expectOne({ method: 'POST', url: `${appUrl}/releases` })
      .flush(
        { status: 409, detail: 'El último commit ya tiene un release en curso o desplegado correctamente.' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    expect(text()).toContain('ya tiene un release en curso');
    expect(redeployButton().disabled).toBe(false);
    backend.expectNone(appUrl);
  });

  it('indica que la URL es provisional hasta el primer despliegue correcto', () => {
    respond([], 'https://tienda-acme.apps.releasy.local');

    expect(text()).toContain('Provisional');
    expect(text()).toContain('Aún no hay releases');
  });
});

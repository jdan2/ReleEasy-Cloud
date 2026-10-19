import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ReleasyApi } from '../../core/api/releasy-api.service';
import { GithubCallbackPage } from './github-callback.page';
import { githubInstallUrl } from './github-flow';

describe('GithubCallbackPage', () => {
  /** Simula la salida hacia GitHub y devuelve el `state` que GitHub enviaría de vuelta. */
  function startedState(): string {
    return new URL(githubInstallUrl('releasy-cloud')).searchParams.get('state')!;
  }

  function create(queryParams: Record<string, string>, connectGithub = vi.fn().mockReturnValue(of({}))) {
    TestBed.configureTestingModule({
      imports: [GithubCallbackPage],
      providers: [
        provideRouter([]),
        { provide: ReleasyApi, useValue: { connectGithub } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } },
      ],
    });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(GithubCallbackPage);
    fixture.detectChanges();
    return { fixture, connectGithub, navigate, text: () => (fixture.nativeElement as HTMLElement).textContent ?? '' };
  }

  beforeEach(() => sessionStorage.clear());

  it('envía el código y la instalación a la API y vuelve a la pantalla de GitHub', () => {
    const state = startedState();
    const { connectGithub, navigate } = create({ code: 'abc', installation_id: '12345678', setup_action: 'install', state });

    expect(connectGithub).toHaveBeenCalledWith({ code: 'abc', installationId: 12345678 });
    expect(navigate).toHaveBeenCalledWith(['/portal/github']);
  });

  it('sin installation_id (solo autorización) envía el código', () => {
    const { connectGithub } = create({ code: 'abc', state: startedState() });

    expect(connectGithub).toHaveBeenCalledWith({ code: 'abc', installationId: null });
  });

  it('rechaza una vuelta con un state que no inició esta pestaña', () => {
    startedState();
    const { connectGithub, text } = create({ code: 'abc', installation_id: '1', state: 'otro' });

    expect(connectGithub).not.toHaveBeenCalled();
    expect(text()).toContain('no corresponde');
  });

  it('avisa si la instalación está pendiente de aprobación en la organización', () => {
    const { connectGithub, text } = create({ setup_action: 'request', state: startedState() });

    expect(connectGithub).not.toHaveBeenCalled();
    expect(text()).toContain('pendiente');
  });

  it('muestra un error si falta el código o la API lo rechaza', () => {
    expect(create({ installation_id: '1', state: startedState() }).text()).toContain('no ha devuelto');

    TestBed.resetTestingModule();
    const rejected = vi.fn().mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { detail: 'Esta instalación de GitHub ya está vinculada a otro cliente' } })),
    );
    const { fixture, navigate, text } = create({ code: 'abc', state: startedState() }, rejected);
    fixture.detectChanges();

    expect(navigate).not.toHaveBeenCalled();
    expect(text()).toContain('ya está vinculada');
  });
});

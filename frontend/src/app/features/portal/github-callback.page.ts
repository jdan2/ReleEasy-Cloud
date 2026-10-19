import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ReleasyApi } from '../../core/api/releasy-api.service';
import { describeHttpError } from '../../shared/http-error';
import { consumeGithubState } from './github-flow';

/**
 * Página a la que GitHub devuelve al cliente después de instalar o autorizar la GitHub App (la
 * "Callback URL" de la app). Comprueba el `state` (ver `github-flow.ts`), envía a la API el `code` OAuth
 * y el `installation_id`, y vuelve a la pantalla de GitHub.
 *
 * Si la instalación la tiene que aprobar un administrador de la organización, GitHub vuelve con
 * `setup_action=request`.
 */
@Component({
  selector: 'app-github-callback-page',
  imports: [RouterLink],
  template: `
    <div class="flex max-w-3xl flex-col gap-6">
      <h1 class="text-2xl font-semibold tracking-tight">Conectando GitHub</h1>
      @if (pendingApproval()) {
        <section class="card text-sm text-slate-600">
          <p>
            La instalación está pendiente de que un administrador de tu organización de GitHub la apruebe. Cuando lo haga,
            vuelve a "Conecta GitHub" e instálala de nuevo.
          </p>
          <a routerLink="/portal/github" class="btn-secondary mt-4">Volver</a>
        </section>
      } @else if (error(); as message) {
        <p class="alert-error" role="alert">{{ message }}</p>
        <a routerLink="/portal/github" class="link text-sm">← Volver a GitHub</a>
      } @else {
        <p class="text-sm text-slate-500">Guardando la instalación…</p>
      }
    </div>
  `,
})
export class GithubCallbackPage implements OnInit {
  private readonly api = inject(ReleasyApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly error = signal<string | null>(null);
  protected readonly pendingApproval = signal(false);

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    if (!consumeGithubState(params.get('state'))) {
      this.error.set('La respuesta de GitHub no corresponde a una conexión iniciada desde esta pestaña. Vuelve a intentarlo.');
      return;
    }
    if (params.get('setup_action') === 'request') {
      this.pendingApproval.set(true);
      return;
    }
    const code = params.get('code');
    if (!code) {
      this.error.set('GitHub no ha devuelto la autorización. Vuelve a intentarlo.');
      return;
    }
    const installationId = Number(params.get('installation_id'));
    const request = { code, installationId: Number.isSafeInteger(installationId) && installationId > 0 ? installationId : null };
    this.api.connectGithub(request).subscribe({
      next: () => void this.router.navigate(['/portal/github']),
      error: (e: unknown) => this.error.set(describeHttpError(e)),
    });
  }
}

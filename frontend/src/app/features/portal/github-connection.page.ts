import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { GithubRepository, Tenant } from '../../core/api/models';
import { ReleasyApi } from '../../core/api/releasy-api.service';
import { APP_CONFIG } from '../../core/config/app-config';
import { describeHttpError } from '../../shared/http-error';
import { StatusBadge } from '../../shared/status-badge';
import { githubAuthorizeUrl, githubInstallUrl } from './github-flow';

/** Código OAuth simulado, que la API en modo mock (o el modo demo) acepta. */
function simulatedCode(): string {
  return `simulado-${crypto.randomUUID()}`;
}

/**
 * Conexión con GitHub mediante la GitHub App de ReleEasy: el cliente la instala en su cuenta u
 * organización y elige qué repositorios comparte. El webhook de push queda configurado por la app.
 *
 * Con `githubAppSlug` en config.json, "Instalar en GitHub" lleva a GitHub: el cliente instala la app y
 * autoriza a ReleEasy con su usuario, y GitHub le devuelve a `/portal/github/callback` (ver
 * `github-callback.page.ts`). Sin él, la instalación se simula.
 */
@Component({
  selector: 'app-github-connection-page',
  imports: [RouterLink, DatePipe, StatusBadge],
  template: `
    <div class="flex max-w-3xl flex-col gap-6">
      <a routerLink="/portal" class="link text-sm">← Inicio</a>
      <div>
        <h1 class="text-2xl font-semibold tracking-tight">Conecta GitHub</h1>
        <p class="mt-1 text-sm text-slate-500">
          Instala la GitHub App de ReleEasy y elige qué repositorios quieres desplegar. Cada merge a la rama configurada
          lanzará un despliegue automático.
        </p>
      </div>

      @if (error(); as message) {
        <p class="alert-error" role="alert">{{ message }}</p>
      }

      @if (tenant(); as t) {
        @if (t.github.status === 'CONNECTED') {
          <section class="card">
            <div class="flex items-center justify-between gap-4">
              <h2 class="card-title">Instalada en <span class="font-mono">{{ t.github.accountLogin }}</span></h2>
              <app-status-badge [status]="t.github.status" />
            </div>
            <p class="mt-1 text-xs text-slate-500">
              Instalación #{{ t.github.installationId }}
              @if (t.github.authorizedBy) {
                · autorizada por <span class="font-mono">{{ '@' + t.github.authorizedBy }}</span>
              }
              · {{ t.github.connectedAt | date: 'dd/MM/yyyy HH:mm' }}
            </p>

            <h3 class="mt-6 text-sm font-medium text-slate-700">Repositorios compartidos</h3>
            <ul class="mt-2 divide-y divide-slate-100 rounded-lg border border-slate-200">
              @for (repo of repositories(); track repo.fullName) {
                <li class="flex items-center justify-between gap-4 px-4 py-3 text-sm">
                  <div>
                    <div class="font-mono text-slate-800">{{ repo.fullName }}</div>
                    <div class="text-xs text-slate-500">{{ repo.description }}</div>
                  </div>
                  <span class="text-xs text-slate-500">{{ repo.private ? 'Privado' : 'Público' }} · {{ repo.branches.length }} ramas</span>
                </li>
              }
            </ul>
            <div class="mt-6 flex gap-3">
              <a routerLink="/portal/templates" class="btn-primary">Crear una aplicación</a>
              <button type="button" class="btn-secondary" [disabled]="saving()" (click)="disconnect()">Desconectar</button>
            </div>
            @if (githubAppSlug) {
              <p class="form-hint mt-2">
                En GitHub verás ReleEasy en <span class="font-medium">Settings → Applications</span>, en "Installed GitHub
                Apps" y "Authorized GitHub Apps". Al desconectar aquí, la app sigue instalada: desde ahí puedes
                desinstalarla o revocar la autorización.
              </p>
            }
          </section>
        } @else {
          <section class="card">
            <h2 class="card-title">Instala la GitHub App</h2>
            <ul class="mt-3 list-disc space-y-1 pl-5 text-sm text-slate-600">
              <li>GitHub te pedirá elegir los repositorios y autorizar a ReleEasy con tu usuario.</li>
              <li>Permiso de solo lectura sobre el código de los repositorios que elijas.</li>
              <li>Recibe los eventos <code class="font-mono">push</code> para lanzar los despliegues.</li>
              <li>Puedes cambiar los repositorios o desinstalarla desde GitHub en cualquier momento.</li>
            </ul>
            <button type="button" class="btn-primary mt-6" [disabled]="saving()" (click)="connect()">
              {{ saving() ? 'Conectando…' : 'Instalar en GitHub' }}
            </button>
            @if (githubClientId) {
              <p class="form-hint mt-2">
                ¿Ya la tenías instalada?
                <button type="button" class="link" (click)="authorizeOnly()">Autoriza tu usuario de GitHub</button>
              </p>
            }
            @if (mockApi) {
              <p class="form-hint mt-2">Modo demo: la instalación se simula y se comparten 4 repositorios de ejemplo.</p>
            } @else if (!githubAppSlug) {
              <p class="form-hint mt-2">
                No hay GitHub App configurada (<code class="font-mono">githubAppSlug</code> en config.json): la instalación se
                simula y la API devuelve repositorios de ejemplo.
              </p>
            }
          </section>
        }
      } @else if (!error()) {
        <p class="text-sm text-slate-500">Cargando…</p>
      }
    </div>
  `,
})
export class GithubConnectionPage implements OnInit {
  private readonly api = inject(ReleasyApi);

  protected readonly mockApi = inject(APP_CONFIG).mockApi;
  protected readonly githubAppSlug = inject(APP_CONFIG).githubAppSlug;
  protected readonly githubClientId = inject(APP_CONFIG).githubClientId;
  protected readonly tenant = signal<Tenant | null>(null);
  protected readonly repositories = signal<GithubRepository[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);

  ngOnInit(): void {
    this.api.myTenant().subscribe({
      next: (tenant) => this.show(tenant),
      error: (e: unknown) => this.error.set(describeHttpError(e)),
    });
  }

  protected connect(): void {
    if (this.githubAppSlug) {
      window.location.assign(githubInstallUrl(this.githubAppSlug));
      return;
    }
    this.run(this.api.connectGithub({ code: simulatedCode() }));
  }

  protected authorizeOnly(): void {
    if (this.githubClientId) {
      window.location.assign(githubAuthorizeUrl(this.githubClientId));
    }
  }

  protected disconnect(): void {
    this.run(this.api.disconnectGithub());
  }

  private run(request: Observable<Tenant>): void {
    this.saving.set(true);
    this.error.set(null);
    request.subscribe({
      next: (tenant) => {
        this.saving.set(false);
        this.show(tenant);
      },
      error: (e: unknown) => {
        this.saving.set(false);
        this.error.set(describeHttpError(e));
      },
    });
  }

  private show(tenant: Tenant): void {
    this.tenant.set(tenant);
    this.repositories.set([]);
    if (tenant.github.status === 'CONNECTED') {
      this.api.myRepositories().subscribe({
        next: (repos) => this.repositories.set(repos),
        error: (e: unknown) => this.error.set(describeHttpError(e)),
      });
    }
  }
}

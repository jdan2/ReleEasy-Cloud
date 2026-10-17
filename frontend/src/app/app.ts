import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { APP_CONFIG } from './core/config/app-config';
import { MockBackend } from './core/mock/mock-backend';

interface NavLink {
  path: string;
  label: string;
  exact: boolean;
}

const CLIENT_NAV: NavLink[] = [
  { path: '/portal', label: 'Mis aplicaciones', exact: true },
  { path: '/portal/templates', label: 'Plantillas', exact: false },
  { path: '/portal/aws', label: 'Cuenta AWS', exact: false },
  { path: '/portal/github', label: 'GitHub', exact: false },
];
const ADMIN_NAV: NavLink[] = [{ path: '/admin', label: 'Clientes', exact: false }];

/** Estructura común: cabecera con navegación según el rol y el contenido de la ruta. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="min-h-full">
      @if (mockApi) {
        <div class="flex flex-wrap items-center justify-center gap-x-3 bg-amber-100 px-4 py-1.5 text-center text-xs text-amber-900">
          <span>
            <strong>Modo demo:</strong> datos simulados en tu navegador, sin conexión con la API
            (<code class="font-mono">"mockApi": false</code> en <code class="font-mono">config.json</code> para desactivarlo).
          </span>
          <button type="button" class="underline hover:no-underline" (click)="resetDemo()">Restablecer datos</button>
        </div>
      }
      <header class="border-b border-slate-200 bg-white">
        <div class="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
          <a routerLink="/" class="flex shrink-0 items-center gap-2">
            <span class="grid size-8 place-items-center rounded-lg bg-indigo-600 text-sm font-bold text-white">R</span>
            <span class="text-lg font-semibold tracking-tight">ReleEasy <span class="text-indigo-600">Cloud</span></span>
          </a>
          @if (auth.user(); as user) {
            <nav class="flex items-center gap-5 overflow-x-auto text-sm">
              @for (link of nav(); track link.path) {
                <a
                  [routerLink]="link.path"
                  routerLinkActive="text-indigo-600"
                  [routerLinkActiveOptions]="{ exact: link.exact }"
                  class="font-medium whitespace-nowrap text-slate-600 hover:text-slate-900"
                  >{{ link.label }}</a
                >
              }
              <span class="hidden text-xs text-slate-400 md:inline">
                {{ user.role === 'ADMIN' ? 'Administrador' : user.name }}
              </span>
              <button type="button" class="btn-secondary whitespace-nowrap" (click)="logout()">Salir</button>
            </nav>
          }
        </div>
      </header>
      <main class="mx-auto max-w-6xl px-4 py-8 sm:px-6">
        <router-outlet />
      </main>
    </div>
  `,
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly mockApi = inject(APP_CONFIG).mockApi;
  private readonly mockBackend = inject(MockBackend);
  private readonly router = inject(Router);

  protected readonly nav = computed(() => (this.auth.role() === 'ADMIN' ? ADMIN_NAV : CLIENT_NAV));

  protected logout(): void {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }

  protected resetDemo(): void {
    this.mockBackend.reset();
    this.logout();
  }
}

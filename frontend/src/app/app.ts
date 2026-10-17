import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';

/** Estructura común: cabecera con navegación y cierre de sesión, y el contenido de la ruta. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="min-h-full">
      <header class="border-b border-slate-200 bg-white">
        <div class="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6">
          <a routerLink="/" class="flex items-center gap-2">
            <span class="grid size-8 place-items-center rounded-lg bg-indigo-600 text-sm font-bold text-white">R</span>
            <span class="text-lg font-semibold tracking-tight">ReleEasy <span class="text-indigo-600">Cloud</span></span>
          </a>
          @if (auth.isAuthenticated()) {
            <nav class="flex items-center gap-6 text-sm">
              <a
                routerLink="/tenants"
                routerLinkActive="text-indigo-600"
                class="font-medium text-slate-600 hover:text-slate-900"
                >Clientes</a
              >
              <button type="button" class="btn-secondary" (click)="logout()">Cerrar sesión</button>
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
  private readonly router = inject(Router);

  protected logout(): void {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }
}

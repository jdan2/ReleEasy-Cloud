import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ReleasyApi } from '../../core/api/releasy-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { APP_CONFIG } from '../../core/config/app-config';
import { DEMO_ADMIN, DEMO_CLIENT } from '../../core/mock/mock-backend';
import { describeHttpError } from '../../shared/http-error';

/** Inicio de sesión común: el rol del usuario decide a qué vista entra. */
@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="mx-auto mt-10 max-w-sm">
      <h1 class="text-2xl font-semibold tracking-tight">Inicia sesión</h1>
      <p class="mt-1 text-sm text-slate-500">
        ¿Aún no tienes cuenta? <a routerLink="/register" class="link">Regístrate gratis</a>
      </p>

      <form class="card mt-6 space-y-4" [formGroup]="form" (ngSubmit)="submit()">
        <div>
          <label class="form-label" for="email">Email</label>
          <input id="email" class="form-input" formControlName="email" autocomplete="username" />
        </div>
        <div>
          <label class="form-label" for="password">Contraseña</label>
          <input id="password" type="password" class="form-input" formControlName="password" autocomplete="current-password" />
        </div>
        @if (error(); as message) {
          <p class="alert-error" role="alert">{{ message }}</p>
        }
        <button type="submit" class="btn-primary w-full" [disabled]="form.invalid || submitting()">
          {{ submitting() ? 'Entrando…' : 'Entrar' }}
        </button>
      </form>

      @if (mockApi) {
        <div class="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
          <p class="font-medium">Usuarios de demostración</p>
          <div class="mt-2 flex flex-col gap-2">
            <button type="button" class="btn-secondary justify-between" (click)="fill(demoClient)">
              <span>Cliente</span><span class="font-mono text-xs">{{ demoClient.email }} / {{ demoClient.password }}</span>
            </button>
            <button type="button" class="btn-secondary justify-between" (click)="fill(demoAdmin)">
              <span>Administrador</span><span class="font-mono text-xs">{{ demoAdmin.email }} / {{ demoAdmin.password }}</span>
            </button>
          </div>
        </div>
      }
    </div>
  `,
})
export class LoginPage {
  private readonly api = inject(ReleasyApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly mockApi = inject(APP_CONFIG).mockApi;
  protected readonly demoClient = DEMO_CLIENT;
  protected readonly demoAdmin = DEMO_ADMIN;
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', Validators.required],
    password: ['', Validators.required],
  });

  protected fill(credentials: { email: string; password: string }): void {
    this.form.setValue(credentials);
  }

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.api.login(this.form.getRawValue()).subscribe({
      next: (session) => {
        this.auth.start(session);
        void this.router.navigateByUrl(this.auth.homeUrl());
      },
      error: (e: unknown) => {
        this.submitting.set(false);
        this.error.set(describeHttpError(e));
      },
    });
  }
}

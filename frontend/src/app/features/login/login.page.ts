import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { describeHttpError } from '../../shared/http-error';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule],
  template: `
    <div class="mx-auto mt-12 max-w-sm">
      <h1 class="text-2xl font-semibold tracking-tight">Acceso de administrador</h1>
      <p class="mt-1 text-sm text-slate-500">Usuario y contraseña configurados en la API.</p>

      <form class="card mt-6 space-y-4" [formGroup]="form" (ngSubmit)="submit()">
        <div>
          <label class="form-label" for="username">Usuario</label>
          <input id="username" class="form-input" formControlName="username" autocomplete="username" />
        </div>
        <div>
          <label class="form-label" for="password">Contraseña</label>
          <input
            id="password"
            type="password"
            class="form-input"
            formControlName="password"
            autocomplete="current-password"
          />
        </div>
        @if (error(); as message) {
          <p class="alert-error" role="alert">{{ message }}</p>
        }
        <button type="submit" class="btn-primary w-full" [disabled]="form.invalid || submitting()">
          {{ submitting() ? 'Comprobando…' : 'Entrar' }}
        </button>
      </form>
    </div>
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group({
    username: ['admin', Validators.required],
    password: ['', Validators.required],
  });

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    const { username, password } = this.form.getRawValue();
    this.submitting.set(true);
    this.error.set(null);
    this.auth.login(username, password).subscribe({
      next: (ok) => {
        this.submitting.set(false);
        if (ok) {
          void this.router.navigate(['/tenants']);
        } else {
          this.error.set('Usuario o contraseña incorrectos');
        }
      },
      error: (e: unknown) => {
        this.submitting.set(false);
        this.error.set(describeHttpError(e));
      },
    });
  }
}

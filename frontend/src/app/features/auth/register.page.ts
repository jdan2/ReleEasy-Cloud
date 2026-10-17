import { Component, inject, signal } from '@angular/core';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ReleasyApi } from '../../core/api/releasy-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { describeHttpError } from '../../shared/http-error';

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const { password, confirm } = group.value as { password: string; confirm: string };
  return password === confirm ? null : { mismatch: true };
}

/** Alta de un cliente nuevo (autoservicio). */
@Component({
  selector: 'app-register-page',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="mx-auto mt-10 max-w-md">
      <h1 class="text-2xl font-semibold tracking-tight">Crea tu cuenta</h1>
      <p class="mt-1 text-sm text-slate-500">
        Despliega tus aplicaciones en tu propia cuenta de AWS sin gestionar infraestructura.
        ¿Ya tienes cuenta? <a routerLink="/login" class="link">Inicia sesión</a>
      </p>

      <form class="card mt-6 space-y-4" [formGroup]="form" (ngSubmit)="submit()">
        <div>
          <label class="form-label" for="company">Empresa</label>
          <input id="company" class="form-input" formControlName="companyName" placeholder="Ferretería López" autocomplete="organization" />
          @if (show('companyName')) {
            <p class="form-error">Indica el nombre de tu empresa.</p>
          }
        </div>
        <div>
          <label class="form-label" for="email">Email</label>
          <input id="email" type="email" class="form-input" formControlName="email" autocomplete="email" />
          @if (show('email')) {
            <p class="form-error">Introduce un email válido.</p>
          }
        </div>
        <div class="grid gap-4 sm:grid-cols-2">
          <div>
            <label class="form-label" for="password">Contraseña</label>
            <input id="password" type="password" class="form-input" formControlName="password" autocomplete="new-password" />
            @if (show('password')) {
              <p class="form-error">Mínimo 8 caracteres.</p>
            }
          </div>
          <div>
            <label class="form-label" for="confirm">Repite la contraseña</label>
            <input id="confirm" type="password" class="form-input" formControlName="confirm" autocomplete="new-password" />
            @if (form.hasError('mismatch') && form.controls.confirm.touched) {
              <p class="form-error">Las contraseñas no coinciden.</p>
            }
          </div>
        </div>
        @if (error(); as message) {
          <p class="alert-error" role="alert">{{ message }}</p>
        }
        <button type="submit" class="btn-primary w-full" [disabled]="submitting()">
          {{ submitting() ? 'Creando cuenta…' : 'Crear cuenta' }}
        </button>
      </form>
    </div>
  `,
})
export class RegisterPage {
  private readonly api = inject(ReleasyApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      companyName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(50)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirm: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );

  protected show(field: 'companyName' | 'email' | 'password'): boolean {
    const control = this.form.controls[field];
    return control.invalid && control.touched;
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { companyName, email, password } = this.form.getRawValue();
    this.submitting.set(true);
    this.error.set(null);
    this.api.register({ companyName, email, password }).subscribe({
      next: (session) => {
        this.auth.start(session);
        void this.router.navigate(['/portal']);
      },
      error: (e: unknown) => {
        this.submitting.set(false);
        this.error.set(describeHttpError(e));
      },
    });
  }
}

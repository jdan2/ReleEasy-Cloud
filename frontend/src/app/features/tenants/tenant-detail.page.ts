import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { App, AppPlan, Tenant } from '../../core/api/models';
import { ReleasyApi } from '../../core/api/releasy-api.service';
import { describeHttpError } from '../../shared/http-error';
import { Poller } from '../../shared/poller';
import { ACTIVE_POLL_MS } from '../../shared/polling-policy';
import { StatusBadge } from '../../shared/status-badge';

/** Las mismas reglas que valida la API (`CreateAppRequest`). */
const APP_NAME_PATTERN = /^[a-z][a-z0-9-]{2,19}$/;
const REPOSITORY_PATTERN = /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/;

/** Detalle de un cliente: su cuenta AWS, sus aplicaciones y el alta de una nueva. */
@Component({
  selector: 'app-tenant-detail-page',
  imports: [ReactiveFormsModule, RouterLink, StatusBadge],
  templateUrl: './tenant-detail.page.html',
})
export class TenantDetailPage implements OnInit {
  /** Parámetro de ruta `:tenantId`. */
  readonly tenantId = input.required<string>();

  private readonly api = inject(ReleasyApi);
  private readonly poller = new Poller(() => this.load(), inject(DestroyRef));

  protected readonly plans: readonly { value: AppPlan; label: string }[] = [
    { value: 'BASIC', label: 'BASIC · 0,25 vCPU, 512 MiB, 1 tarea' },
    { value: 'STANDARD', label: 'STANDARD · 0,5 vCPU, 1 GiB, 2 tareas' },
  ];

  protected readonly tenant = signal<Tenant | null>(null);
  protected readonly apps = signal<App[]>([]);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);
  protected readonly canRegisterApps = computed(() => this.tenant()?.status === 'ACTIVE');

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.pattern(APP_NAME_PATTERN)]],
    repository: ['', [Validators.required, Validators.pattern(REPOSITORY_PATTERN)]],
    branch: ['master', [Validators.required, Validators.maxLength(100)]],
    plan: ['BASIC' as AppPlan, Validators.required],
  });

  ngOnInit(): void {
    this.load();
  }

  protected showError(field: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[field];
    return control.invalid && control.touched;
  }

  protected create(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.saveError.set(null);
    this.api.createApp(this.tenantId(), this.form.getRawValue()).subscribe({
      next: (app) => {
        this.apps.update((list) => [...list, app]);
        this.form.reset();
        this.saving.set(false);
      },
      error: (e: unknown) => {
        this.saveError.set(describeHttpError(e));
        this.saving.set(false);
      },
    });
  }

  private load(): void {
    forkJoin({ tenant: this.api.getTenant(this.tenantId()), apps: this.api.listApps(this.tenantId()) }).subscribe({
      next: ({ tenant, apps }) => {
        this.tenant.set(tenant);
        this.apps.set(apps);
        this.loadError.set(null);
        if (tenant.status === 'CREATING') {
          this.poller.schedule(ACTIVE_POLL_MS);
        }
      },
      error: (e: unknown) => this.loadError.set(describeHttpError(e)),
    });
  }
}

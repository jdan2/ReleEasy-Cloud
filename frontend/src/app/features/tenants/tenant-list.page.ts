import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Tenant } from '../../core/api/models';
import { ReleasyApi } from '../../core/api/releasy-api.service';
import { describeHttpError } from '../../shared/http-error';
import { Poller } from '../../shared/poller';
import { ACTIVE_POLL_MS, hasAccountInCreation } from '../../shared/polling-policy';
import { StatusBadge } from '../../shared/status-badge';

/** Lista de clientes y alta de uno nuevo. Refresca sola mientras haya cuentas AWS en creación. */
@Component({
  selector: 'app-tenant-list-page',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, StatusBadge],
  templateUrl: './tenant-list.page.html',
})
export class TenantListPage implements OnInit {
  private readonly api = inject(ReleasyApi);
  private readonly poller = new Poller(() => this.load(), inject(DestroyRef));

  protected readonly tenants = signal<Tenant[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(50)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(64)]],
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
    this.api.createTenant(this.form.getRawValue()).subscribe({
      next: (tenant) => {
        this.tenants.update((list) => [tenant, ...list]);
        this.form.reset();
        this.saving.set(false);
        this.poller.schedule(ACTIVE_POLL_MS);
      },
      error: (e: unknown) => {
        this.saveError.set(describeHttpError(e));
        this.saving.set(false);
      },
    });
  }

  private load(): void {
    this.api.listTenants().subscribe({
      next: (tenants) => {
        this.tenants.set(tenants);
        this.loadError.set(null);
        this.loading.set(false);
        if (hasAccountInCreation(tenants)) {
          this.poller.schedule(ACTIVE_POLL_MS);
        }
      },
      error: (e: unknown) => {
        this.loadError.set(describeHttpError(e));
        this.loading.set(false);
      },
    });
  }
}

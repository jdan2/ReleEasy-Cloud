import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Tenant } from '../../core/api/models';
import { ReleasyApi } from '../../core/api/releasy-api.service';
import { describeHttpError } from '../../shared/http-error';
import { StatusBadge } from '../../shared/status-badge';

/** Vista del administrador: todos los clientes y el estado de sus conexiones. */
@Component({
  selector: 'app-admin-tenants-page',
  imports: [RouterLink, DatePipe, StatusBadge],
  template: `
    <div class="flex flex-col gap-6">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight">Clientes</h1>
        <p class="mt-1 text-sm text-slate-500">
          Los clientes se registran solos. Cada uno conecta su propia cuenta de AWS y su GitHub.
        </p>
      </div>

      <section class="card overflow-hidden p-0" aria-label="Lista de clientes">
        @if (loading()) {
          <p class="p-6 text-sm text-slate-500">Cargando clientes…</p>
        } @else if (error(); as message) {
          <p class="alert-error m-6" role="alert">{{ message }}</p>
        } @else if (tenants().length === 0) {
          <p class="p-6 text-sm text-slate-500">Todavía no se ha registrado ningún cliente.</p>
        } @else {
          <div class="overflow-x-auto">
            <table class="data-table">
              <thead class="bg-slate-50">
                <tr>
                  <th scope="col">Cliente</th>
                  <th scope="col">Cuenta AWS</th>
                  <th scope="col">GitHub</th>
                  <th scope="col">Apps</th>
                  <th scope="col">Alta</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                @for (t of tenants(); track t.id) {
                  <tr>
                    <td>
                      <a class="link" [routerLink]="['/admin/tenants', t.id]">{{ t.name }}</a>
                      <div class="text-xs text-slate-500">{{ t.email }}</div>
                    </td>
                    <td>
                      <app-status-badge [status]="t.aws.status" />
                      @if (t.aws.accountId) {
                        <div class="mt-1 font-mono text-xs text-slate-500">{{ t.aws.accountId }}</div>
                      }
                    </td>
                    <td>
                      <app-status-badge [status]="t.github.status" />
                      @if (t.github.accountLogin) {
                        <div class="mt-1 font-mono text-xs text-slate-500">{{ t.github.accountLogin }}</div>
                      }
                    </td>
                    <td class="tabular-nums">{{ t.appCount }}</td>
                    <td class="text-xs whitespace-nowrap text-slate-500">{{ t.createdAt | date: 'dd/MM/yyyy' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
    </div>
  `,
})
export class AdminTenantsPage implements OnInit {
  private readonly api = inject(ReleasyApi);

  protected readonly tenants = signal<Tenant[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.listTenants().subscribe({
      next: (tenants) => {
        this.tenants.set(tenants);
        this.loading.set(false);
      },
      error: (e: unknown) => {
        this.error.set(describeHttpError(e));
        this.loading.set(false);
      },
    });
  }
}

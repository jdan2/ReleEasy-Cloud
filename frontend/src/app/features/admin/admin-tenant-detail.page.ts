import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { App, Tenant } from '../../core/api/models';
import { ReleasyApi } from '../../core/api/releasy-api.service';
import { estimateMonthlyCost } from '../../core/pricing/cost-estimator';
import { findTemplate, templateName } from '../../core/templates/template-catalog';
import { describeHttpError } from '../../shared/http-error';
import { StatusBadge } from '../../shared/status-badge';

/** Vista del administrador de un cliente: conexiones y aplicaciones (solo lectura). */
@Component({
  selector: 'app-admin-tenant-detail-page',
  imports: [RouterLink, DatePipe, CurrencyPipe, StatusBadge],
  template: `
    <div class="flex flex-col gap-6">
      <a routerLink="/admin" class="link text-sm">← Clientes</a>
      @if (error(); as message) {
        <p class="alert-error" role="alert">{{ message }}</p>
      }
      @if (tenant(); as t) {
        <div>
          <h1 class="text-2xl font-semibold tracking-tight">{{ t.name }}</h1>
          <p class="mt-1 text-sm text-slate-500">{{ t.email }} · alta {{ t.createdAt | date: 'dd/MM/yyyy' }}</p>
        </div>

        <div class="grid gap-4 sm:grid-cols-2">
          <section class="card">
            <div class="flex items-center justify-between gap-2">
              <h2 class="card-title">Cuenta AWS</h2>
              <app-status-badge [status]="t.aws.status" />
            </div>
            <dl class="mt-4 space-y-2 text-sm">
              <div><dt class="text-slate-500">Cuenta</dt><dd class="font-mono">{{ t.aws.accountId ?? '—' }}</dd></div>
              <div><dt class="text-slate-500">Rol</dt><dd class="font-mono text-xs break-all">{{ t.aws.roleArn ?? '—' }}</dd></div>
            </dl>
          </section>
          <section class="card">
            <div class="flex items-center justify-between gap-2">
              <h2 class="card-title">GitHub</h2>
              <app-status-badge [status]="t.github.status" />
            </div>
            <dl class="mt-4 space-y-2 text-sm">
              <div><dt class="text-slate-500">Cuenta</dt><dd class="font-mono">{{ t.github.accountLogin ?? '—' }}</dd></div>
              <div><dt class="text-slate-500">Instalación</dt><dd class="font-mono">{{ t.github.installationId ?? '—' }}</dd></div>
            </dl>
          </section>
        </div>

        <section class="card overflow-hidden p-0" aria-labelledby="apps-title">
          <div class="flex items-center justify-between border-b border-slate-200 px-6 py-4">
            <h2 id="apps-title" class="card-title">Aplicaciones</h2>
            <span class="text-sm text-slate-500">
              Total estimado: <strong class="text-slate-800">{{ totalMonthly() | currency: 'USD' : 'symbol' : '1.0-0' }}</strong>/mes
            </span>
          </div>
          @if (apps().length === 0) {
            <p class="p-6 text-sm text-slate-500">Este cliente aún no tiene aplicaciones.</p>
          } @else {
            <div class="overflow-x-auto">
              <table class="data-table">
                <thead class="bg-slate-50">
                  <tr>
                    <th scope="col">Aplicación</th>
                    <th scope="col">Plantilla</th>
                    <th scope="col">Repositorio</th>
                    <th scope="col">Coste estimado</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-100">
                  @for (app of apps(); track app.id) {
                    <tr>
                      <td><a class="link" [routerLink]="['/admin/apps', app.id]">{{ app.name }}</a></td>
                      <td>{{ templateName(app.templateId) }}</td>
                      <td class="font-mono text-xs">{{ app.repository }} · {{ app.branch }}</td>
                      <td class="tabular-nums">{{ monthly(app) | currency: 'USD' : 'symbol' : '1.0-0' }}/mes</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </section>
      } @else if (!error()) {
        <p class="text-sm text-slate-500">Cargando…</p>
      }
    </div>
  `,
})
export class AdminTenantDetailPage implements OnInit {
  /** Parámetro de ruta `:tenantId`. */
  readonly tenantId = input.required<string>();

  private readonly api = inject(ReleasyApi);

  protected readonly tenant = signal<Tenant | null>(null);
  protected readonly apps = signal<App[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly totalMonthly = computed(() => this.apps().reduce((sum, app) => sum + this.monthly(app), 0));
  protected readonly templateName = templateName;

  ngOnInit(): void {
    forkJoin({ tenant: this.api.getTenant(this.tenantId()), apps: this.api.listApps(this.tenantId()) }).subscribe({
      next: ({ tenant, apps }) => {
        this.tenant.set(tenant);
        this.apps.set(apps);
      },
      error: (e: unknown) => this.error.set(describeHttpError(e)),
    });
  }

  protected monthly(app: App): number {
    const template = findTemplate(app.templateId);
    return template ? estimateMonthlyCost(template, app.options, app.usage).totalMonthlyUsd : 0;
  }
}

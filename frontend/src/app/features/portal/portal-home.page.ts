import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { App, Tenant } from '../../core/api/models';
import { ReleasyApi } from '../../core/api/releasy-api.service';
import { estimateMonthlyCost } from '../../core/pricing/cost-estimator';
import { findTemplate, templateName } from '../../core/templates/template-catalog';
import { describeHttpError } from '../../shared/http-error';

interface AppSummary {
  app: App;
  templateName: string;
  monthlyUsd: number | null;
}

/** Inicio del cliente: pasos de configuración pendientes y sus aplicaciones con su coste estimado. */
@Component({
  selector: 'app-portal-home-page',
  imports: [RouterLink, CurrencyPipe],
  template: `
    <div class="flex flex-col gap-8">
      @if (error(); as message) {
        <p class="alert-error" role="alert">{{ message }}</p>
      }
      @if (tenant(); as t) {
        <div>
          <h1 class="text-2xl font-semibold tracking-tight">Hola, {{ t.name }}</h1>
          <p class="mt-1 text-sm text-slate-500">Despliega tus aplicaciones en tu cuenta de AWS con cada merge.</p>
        </div>

        @if (!ready()) {
          <section class="card" aria-labelledby="setup-title">
            <h2 id="setup-title" class="card-title">Prepara tu cuenta</h2>
            <ol class="mt-4 space-y-3">
              <li class="flex items-center justify-between gap-4">
                <span class="flex items-center gap-3 text-sm">
                  <span [class]="stepClass(t.aws.status === 'CONNECTED')">1</span> Conecta tu cuenta de AWS
                </span>
                @if (t.aws.status === 'CONNECTED') {
                  <span class="text-sm text-emerald-700">Hecho ✓</span>
                } @else {
                  <a routerLink="/portal/aws" class="btn-primary">Conectar AWS</a>
                }
              </li>
              <li class="flex items-center justify-between gap-4">
                <span class="flex items-center gap-3 text-sm">
                  <span [class]="stepClass(t.github.status === 'CONNECTED')">2</span> Conecta GitHub
                </span>
                @if (t.github.status === 'CONNECTED') {
                  <span class="text-sm text-emerald-700">Hecho ✓</span>
                } @else {
                  <a routerLink="/portal/github" class="btn-primary">Conectar GitHub</a>
                }
              </li>
              <li class="flex items-center gap-3 text-sm">
                <span [class]="stepClass(false)">3</span> Elige una plantilla y asígnale un repositorio
              </li>
            </ol>
          </section>
        }

        <section aria-labelledby="apps-title" class="flex flex-col gap-4">
          <div class="flex flex-wrap items-end justify-between gap-2">
            <div>
              <h2 id="apps-title" class="text-lg font-semibold">Mis aplicaciones</h2>
              @if (apps().length > 0) {
                <p class="text-sm text-slate-500">
                  Coste mensual estimado total:
                  <strong class="text-slate-800">{{ totalMonthly() | currency: 'USD' : 'symbol' : '1.0-0' }}</strong>
                </p>
              }
            </div>
            <a routerLink="/portal/templates" class="btn-primary">+ Nueva aplicación</a>
          </div>

          @if (apps().length === 0) {
            <div class="card text-sm text-slate-500">
              Aún no tienes aplicaciones. <a routerLink="/portal/templates" class="link">Explora las plantillas</a> para
              crear la primera.
            </div>
          } @else {
            <ul class="grid gap-4 sm:grid-cols-2">
              @for (s of apps(); track s.app.id) {
                <li>
                  <a [routerLink]="['/portal/apps', s.app.id]" class="card block transition hover:border-indigo-300 hover:shadow">
                    <div class="flex items-center justify-between gap-2">
                      <span class="font-semibold text-slate-900">{{ s.app.name }}</span>
                      <span class="rounded bg-indigo-50 px-2 py-0.5 text-xs font-medium text-indigo-700">{{ s.templateName }}</span>
                    </div>
                    <p class="mt-2 font-mono text-xs text-slate-600">{{ s.app.repository }} · {{ s.app.branch }}</p>
                    <p class="mt-1 truncate text-xs text-indigo-600">{{ s.app.url }}</p>
                    @if (s.monthlyUsd !== null) {
                      <p class="mt-3 text-sm text-slate-700">
                        ~{{ s.monthlyUsd | currency: 'USD' : 'symbol' : '1.0-0' }}<span class="text-slate-500">/mes</span>
                      </p>
                    }
                  </a>
                </li>
              }
            </ul>
          }
        </section>
      } @else if (!error()) {
        <p class="text-sm text-slate-500">Cargando…</p>
      }
    </div>
  `,
})
export class PortalHomePage implements OnInit {
  private readonly api = inject(ReleasyApi);

  protected readonly tenant = signal<Tenant | null>(null);
  protected readonly apps = signal<AppSummary[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly ready = computed(
    () => this.tenant()?.aws.status === 'CONNECTED' && this.tenant()?.github.status === 'CONNECTED',
  );
  protected readonly totalMonthly = computed(() => this.apps().reduce((sum, s) => sum + (s.monthlyUsd ?? 0), 0));

  ngOnInit(): void {
    forkJoin({ tenant: this.api.myTenant(), apps: this.api.myApps() }).subscribe({
      next: ({ tenant, apps }) => {
        this.tenant.set(tenant);
        this.apps.set(apps.map(summarize));
      },
      error: (e: unknown) => this.error.set(describeHttpError(e)),
    });
  }

  protected stepClass(done: boolean): string {
    const base = 'grid size-7 place-items-center rounded-full text-xs font-semibold';
    return done ? `${base} bg-emerald-100 text-emerald-700` : `${base} bg-slate-100 text-slate-600`;
  }
}

function summarize(app: App): AppSummary {
  const template = findTemplate(app.templateId);
  return {
    app,
    templateName: templateName(app.templateId),
    monthlyUsd: template ? estimateMonthlyCost(template, app.options, app.usage).totalMonthlyUsd : null,
  };
}

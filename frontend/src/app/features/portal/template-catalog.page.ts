import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ReleasyApi } from '../../core/api/releasy-api.service';
import { estimateMonthlyCost } from '../../core/pricing/cost-estimator';
import { defaultOptions, defaultUsage } from '../../core/templates/template-catalog';
import { Template } from '../../core/templates/template.model';
import { describeHttpError } from '../../shared/http-error';

interface CatalogEntry {
  template: Template;
  /** Coste mensual con las opciones por defecto y uso bajo. */
  fromUsd: number;
}

/** Catálogo de plantillas que el cliente puede desplegar. */
@Component({
  selector: 'app-template-catalog-page',
  imports: [RouterLink, CurrencyPipe],
  template: `
    <div class="flex flex-col gap-6">
      <div>
        <a routerLink="/portal" class="link text-sm">← Inicio</a>
        <h1 class="mt-4 text-2xl font-semibold tracking-tight">Plantillas</h1>
        <p class="mt-1 text-sm text-slate-500">
          Elige qué tipo de aplicación quieres desplegar. Después podrás añadirle componentes como observabilidad, colas o
          un API Gateway, y ver cuánto costará.
        </p>
      </div>

      @if (error(); as message) {
        <p class="alert-error" role="alert">{{ message }}</p>
      }

      <ul class="grid gap-4 md:grid-cols-3">
        @for (entry of entries(); track entry.template.id) {
          @let t = entry.template;
          <li class="card flex flex-col">
            <h2 class="text-lg font-semibold">{{ t.name }}</h2>
            <p class="text-sm text-indigo-700">{{ t.tagline }}</p>
            <p class="mt-3 text-sm text-slate-600">{{ t.description }}</p>
            <div class="mt-4 flex flex-wrap gap-1.5">
              @for (service of t.awsServices; track service) {
                <span class="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-600">{{ service }}</span>
              }
            </div>
            <details class="mt-4 text-sm">
              <summary class="cursor-pointer text-slate-600">Qué debe tener tu repositorio</summary>
              <ul class="mt-2 list-disc space-y-1 pl-5 text-xs text-slate-600">
                @for (rule of t.repoContract; track rule) {
                  <li>{{ rule }}</li>
                }
              </ul>
            </details>
            <div class="mt-auto pt-6">
              <p class="text-sm text-slate-500">
                Desde <strong class="text-slate-900">{{ entry.fromUsd | currency: 'USD' : 'symbol' : '1.0-0' }}</strong>/mes
              </p>
              <a [routerLink]="['/portal/templates', t.id, 'new']" class="btn-primary mt-3 w-full">Usar plantilla</a>
            </div>
          </li>
        }
      </ul>
    </div>
  `,
})
export class TemplateCatalogPage implements OnInit {
  private readonly api = inject(ReleasyApi);

  protected readonly entries = signal<CatalogEntry[]>([]);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.templates().subscribe({
      next: (templates) => this.entries.set(templates.map(toEntry)),
      error: (e: unknown) => this.error.set(describeHttpError(e)),
    });
  }
}

function toEntry(template: Template): CatalogEntry {
  const low = template.usagePresets.find((p) => p.id === 'low')?.values ?? {};
  const estimate = estimateMonthlyCost(template, defaultOptions(template), { ...defaultUsage(template), ...low });
  return { template, fromUsd: estimate.totalMonthlyUsd };
}

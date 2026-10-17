import { CurrencyPipe } from '@angular/common';
import { Component, input, signal } from '@angular/core';
import { PRICES_REGION, PRICES_REVIEWED_AT } from '../core/pricing/aws-prices';
import { CostEstimate } from '../core/pricing/cost-estimator';

/** Resumen y desglose del coste mensual estimado de una aplicación. */
@Component({
  selector: 'app-cost-breakdown',
  imports: [CurrencyPipe],
  template: `
    @let e = estimate();
    <div class="flex items-baseline justify-between gap-2">
      <span class="text-sm text-slate-600">Total estimado</span>
      <span class="text-2xl font-semibold tracking-tight">
        {{ e.totalMonthlyUsd | currency: 'USD' : 'symbol' : '1.0-0' }}<span class="text-sm font-normal text-slate-500">/mes</span>
      </span>
    </div>
    <dl class="mt-3 space-y-1.5 text-sm">
      <div class="flex justify-between gap-2">
        <dt class="text-slate-600">Infraestructura AWS <span class="text-xs text-slate-400">(la factura AWS a tu cuenta)</span></dt>
        <dd class="font-medium">{{ e.awsMonthlyUsd | currency: 'USD' }}</dd>
      </div>
      <div class="flex justify-between gap-2">
        <dt class="text-slate-600">Cuota ReleEasy <span class="text-xs text-slate-400">(simulada)</span></dt>
        <dd class="font-medium">{{ e.platformFeeUsd | currency: 'USD' }}</dd>
      </div>
      @if (e.awsMaxMonthlyUsd > e.awsMonthlyUsd) {
        <div class="flex justify-between gap-2 text-xs text-slate-500">
          <dt>AWS si escala al máximo todo el mes</dt>
          <dd>hasta {{ e.awsMaxMonthlyUsd | currency: 'USD' }}</dd>
        </div>
      }
    </dl>

    <button type="button" class="link mt-3 text-xs" (click)="expanded.set(!expanded())" [attr.aria-expanded]="expanded()">
      {{ expanded() ? 'Ocultar desglose' : 'Ver desglose por servicio' }}
    </button>
    @if (expanded()) {
      <ul class="mt-3 divide-y divide-slate-100 border-t border-slate-100 text-sm">
        @for (line of e.lines; track line.service + line.detail) {
          <li class="flex justify-between gap-3 py-2">
            <div>
              <div class="font-medium text-slate-800">{{ line.service }}</div>
              <div class="text-xs text-slate-500">{{ line.detail }}</div>
            </div>
            <div class="shrink-0 text-right tabular-nums">
              {{ line.monthlyUsd < 0.01 ? '< $0.01' : (line.monthlyUsd | currency: 'USD') }}
            </div>
          </li>
        }
      </ul>
      <ul class="mt-2 list-disc space-y-0.5 pl-4 text-xs text-slate-500">
        @for (a of e.assumptions; track a) {
          <li>{{ a }}</li>
        }
        <li>Precios de referencia {{ region }}, revisados en {{ reviewedAt }}.</li>
      </ul>
    }
  `,
})
export class CostBreakdown {
  readonly estimate = input.required<CostEstimate>();
  protected readonly expanded = signal(false);
  protected readonly region = PRICES_REGION;
  protected readonly reviewedAt = PRICES_REVIEWED_AT;
}

import { Component, computed, input } from '@angular/core';
import { ReleaseStatus, TenantStatus } from '../core/api/models';

type Status = TenantStatus | ReleaseStatus;

const STYLES: Record<Status, { classes: string; label: string; spinning: boolean }> = {
  CREATING: { classes: 'bg-amber-50 text-amber-800 ring-amber-600/20', label: 'Creando cuenta', spinning: true },
  ACTIVE: { classes: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20', label: 'Cuenta activa', spinning: false },
  PENDING: { classes: 'bg-slate-100 text-slate-700 ring-slate-500/20', label: 'En cola', spinning: true },
  RUNNING: { classes: 'bg-sky-50 text-sky-700 ring-sky-600/20', label: 'Desplegando', spinning: true },
  SUCCEEDED: { classes: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20', label: 'Desplegado', spinning: false },
  FAILED: { classes: 'bg-rose-50 text-rose-700 ring-rose-600/20', label: 'Fallido', spinning: false },
};

/**
 * Etiqueta de estado (de cuenta AWS o de release). Muestra el código técnico (`RUNNING`...) con
 * su descripción en español como texto de ayuda.
 */
@Component({
  selector: 'app-status-badge',
  template: `
    <span
      class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 font-mono text-xs font-medium ring-1 ring-inset"
      [class]="style().classes"
      [title]="style().label"
    >
      @if (style().spinning) {
        <span class="size-1.5 animate-pulse rounded-full bg-current" aria-hidden="true"></span>
      }
      {{ status() }}
    </span>
  `,
})
export class StatusBadge {
  readonly status = input.required<Status>();
  protected readonly style = computed(() => STYLES[this.status()]);
}

import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { App, Release } from '../../core/api/models';
import { ReleasyApi } from '../../core/api/releasy-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { APP_CONFIG } from '../../core/config/app-config';
import { MockBackend } from '../../core/mock/mock-backend';
import { estimateMonthlyCost } from '../../core/pricing/cost-estimator';
import { describeOptions, findTemplate } from '../../core/templates/template-catalog';
import { CostBreakdown } from '../../shared/cost-breakdown';
import { describeHttpError } from '../../shared/http-error';
import { Poller } from '../../shared/poller';
import { ACTIVE_POLL_MS, IDLE_POLL_MS, hasReleaseInProgress, releasePollInterval } from '../../shared/polling-policy';
import { StatusBadge } from '../../shared/status-badge';

/**
 * Detalle de una aplicación (para su propietario y para el administrador): URL pública, plantilla y
 * componentes, coste estimado e historial de releases.
 *
 * Mientras haya un release en curso refresca cada {@link ACTIVE_POLL_MS} ms; en reposo, cada
 * {@link IDLE_POLL_MS} ms, para que aparezcan los releases nuevos que dispara un merge.
 */
@Component({
  selector: 'app-app-detail-page',
  imports: [RouterLink, DatePipe, StatusBadge, CostBreakdown],
  templateUrl: './app-detail.page.html',
})
export class AppDetailPage implements OnInit {
  /** Parámetro de ruta `:appId`. */
  readonly appId = input.required<string>();

  private readonly api = inject(ReleasyApi);
  private readonly mockBackend = inject(MockBackend);
  private readonly poller = new Poller(() => this.load(), inject(DestroyRef));

  protected readonly mockApi = inject(APP_CONFIG).mockApi;
  protected readonly isAdmin = inject(AuthService).role() === 'ADMIN';
  protected readonly app = signal<App | null>(null);
  protected readonly releases = signal<Release[]>([]);
  protected readonly loadError = signal<string | null>(null);
  protected readonly lastUpdated = signal<Date | null>(null);

  protected readonly template = computed(() => {
    const app = this.app();
    return app ? findTemplate(app.templateId) : undefined;
  });
  protected readonly components = computed(() => {
    const app = this.app();
    const template = this.template();
    return app && template ? describeOptions(template, app.options) : [];
  });
  protected readonly estimate = computed(() => {
    const app = this.app();
    const template = this.template();
    return app && template ? estimateMonthlyCost(template, app.options, app.usage) : null;
  });
  protected readonly inProgress = computed(() => hasReleaseInProgress(this.releases()));
  /** La URL es provisional hasta el primer despliegue correcto (decisión D-03). */
  protected readonly provisionalUrl = computed(() => !this.releases().some((r) => r.status === 'SUCCEEDED'));
  protected readonly activePollSeconds = ACTIVE_POLL_MS / 1000;

  ngOnInit(): void {
    this.load();
  }

  protected backLink(app: App): string[] {
    return this.isAdmin ? ['/admin/tenants', app.tenantId] : ['/portal'];
  }

  protected refresh(): void {
    this.load();
  }

  /** Solo en modo demo: simula el webhook de un merge a la rama de la aplicación. */
  protected simulateMerge(): void {
    this.mockBackend.simulatePush(this.appId());
    this.load();
  }

  protected commitUrl(release: Release): string {
    return `https://github.com/${this.app()?.repository}/commit/${release.commitSha}`;
  }

  private load(): void {
    forkJoin({ app: this.api.getApp(this.appId()), releases: this.api.listReleases(this.appId()) }).subscribe({
      next: ({ app, releases }) => {
        this.app.set(app);
        this.releases.set(releases);
        this.loadError.set(null);
        this.lastUpdated.set(new Date());
        this.poller.schedule(releasePollInterval(releases));
      },
      error: (e: unknown) => {
        this.loadError.set(describeHttpError(e));
        this.poller.schedule(IDLE_POLL_MS);
      },
    });
  }
}

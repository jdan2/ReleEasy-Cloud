import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { AppOptions, GithubRepository, OptionValue, Tenant, UsageEstimate } from '../../core/api/models';
import { ReleasyApi } from '../../core/api/releasy-api.service';
import { estimateMonthlyCost } from '../../core/pricing/cost-estimator';
import { defaultOptions, defaultUsage, isVisible, validateOptions } from '../../core/templates/template-catalog';
import { Template, UsagePreset } from '../../core/templates/template.model';
import { CostBreakdown } from '../../shared/cost-breakdown';
import { describeHttpError } from '../../shared/http-error';
import { OptionFieldComponent } from '../../shared/option-field';

const APP_NAME = /^[a-z][a-z0-9-]{2,19}$/;

/**
 * Alta de una aplicación a partir de una plantilla: nombre, repositorio y rama, componentes
 * opcionales y uso estimado. El coste mensual se recalcula en cada cambio.
 *
 * El formulario usa signals en lugar de Reactive Forms porque sus campos salen de la definición de la
 * plantilla y cambian de visibilidad según otras opciones.
 */
@Component({
  selector: 'app-new-app-page',
  imports: [RouterLink, OptionFieldComponent, CostBreakdown],
  templateUrl: './new-app.page.html',
})
export class NewAppPage implements OnInit {
  /** Parámetro de ruta `:templateId`. */
  readonly templateId = input.required<string>();

  private readonly api = inject(ReleasyApi);
  private readonly router = inject(Router);

  protected readonly template = signal<Template | null>(null);
  protected readonly tenant = signal<Tenant | null>(null);
  protected readonly repositories = signal<GithubRepository[]>([]);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saveError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly submitted = signal(false);

  protected readonly name = signal('');
  protected readonly repository = signal('');
  protected readonly branch = signal('');
  protected readonly options = signal<AppOptions>({});
  protected readonly usage = signal<UsageEstimate>({});
  protected readonly activePreset = signal<UsagePreset['id'] | null>('medium');

  protected readonly connected = computed(
    () => this.tenant()?.aws.status === 'CONNECTED' && this.tenant()?.github.status === 'CONNECTED',
  );
  protected readonly branches = computed(
    () => this.repositories().find((r) => r.fullName === this.repository())?.branches ?? [],
  );
  protected readonly visibleUsageFields = computed(
    () => this.template()?.usageFields.filter((f) => isVisible(f.visibleWhen, this.options())) ?? [],
  );
  protected readonly estimate = computed(() => {
    const template = this.template();
    return template ? estimateMonthlyCost(template, this.options(), this.usage()) : null;
  });
  protected readonly errors = computed(() => {
    const template = this.template();
    if (!template) {
      return [];
    }
    const list: string[] = [];
    if (!APP_NAME.test(this.name())) {
      list.push('Nombre: 3-20 caracteres en minúsculas, dígitos o guiones; empieza por letra.');
    }
    if (!this.repository()) {
      list.push('Repositorio: elige uno de tus repositorios.');
    }
    if (!this.branch()) {
      list.push('Rama: elige la rama que se desplegará.');
    }
    return [...list, ...validateOptions(template, this.options())];
  });

  protected readonly isVisible = isVisible;

  ngOnInit(): void {
    forkJoin({ template: this.api.template(this.templateId()), tenant: this.api.myTenant() }).subscribe({
      next: ({ template, tenant }) => {
        this.template.set(template);
        this.tenant.set(tenant);
        this.options.set(defaultOptions(template));
        const medium = template.usagePresets.find((p) => p.id === 'medium');
        this.usage.set({ ...defaultUsage(template), ...(medium?.values ?? {}) });
        const repos$ = tenant.github.status === 'CONNECTED' ? this.api.myRepositories() : of([]);
        repos$.subscribe((repos) => this.repositories.set(repos));
      },
      error: (e: unknown) => this.loadError.set(describeHttpError(e)),
    });
  }

  protected setOption(key: string, value: OptionValue): void {
    this.options.update((current) => ({ ...current, [key]: value }));
  }

  protected setUsage(key: string, value: number): void {
    this.usage.update((current) => ({ ...current, [key]: Number.isFinite(value) ? value : 0 }));
    this.activePreset.set(null);
  }

  protected applyPreset(preset: UsagePreset): void {
    this.usage.update((current) => ({ ...current, ...preset.values }));
    this.activePreset.set(preset.id);
  }

  protected selectRepository(fullName: string): void {
    this.repository.set(fullName);
    const repo = this.repositories().find((r) => r.fullName === fullName);
    this.branch.set(repo?.branches.includes('master') ? 'master' : (repo?.defaultBranch ?? ''));
  }

  protected submit(): void {
    this.submitted.set(true);
    const template = this.template();
    if (!template || this.errors().length > 0) {
      return;
    }
    this.saving.set(true);
    this.saveError.set(null);
    this.api
      .createApp({
        name: this.name(),
        templateId: template.id,
        repository: this.repository(),
        branch: this.branch(),
        options: this.visibleOptions(template),
        usage: this.usage(),
      })
      .subscribe({
        next: (app) => void this.router.navigate(['/portal/apps', app.id]),
        error: (e: unknown) => {
          this.saveError.set(describeHttpError(e));
          this.saving.set(false);
        },
      });
  }

  /** Solo se envían las opciones visibles; las ocultas vuelven a su valor por defecto. */
  private visibleOptions(template: Template): AppOptions {
    const defaults = defaultOptions(template);
    const current = this.options();
    return Object.fromEntries(
      template.optionGroups
        .flatMap((g) => g.fields)
        .map((f) => [f.key, isVisible(f.visibleWhen, current) ? current[f.key] : defaults[f.key]]),
    );
  }
}

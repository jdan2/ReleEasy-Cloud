import { Component, computed, input, output } from '@angular/core';
import { OptionValue } from '../core/api/models';
import { OptionField } from '../core/templates/template.model';

/**
 * Renderiza un campo de opción de plantilla (interruptor, selector, número, texto o variables de
 * entorno) a partir de su definición. Emite el valor nuevo en cada cambio.
 */
@Component({
  selector: 'app-option-field',
  template: `
    @let f = field();
    @switch (f.type) {
      @case ('toggle') {
        <label class="flex cursor-pointer items-start gap-3">
          <input
            type="checkbox"
            class="mt-0.5 size-4 rounded border-slate-300 text-indigo-600 accent-indigo-600"
            [checked]="value() === true"
            (change)="changed.emit($any($event.target).checked)"
          />
          <span>
            <span class="block text-sm font-medium text-slate-800">{{ f.label }}</span>
            @if (f.help) {
              <span class="block text-xs text-slate-500">{{ f.help }}</span>
            }
          </span>
        </label>
      }
      @case ('select') {
        <label class="form-label" [for]="id()">{{ f.label }}</label>
        <select [id]="id()" class="form-input" [value]="value()" (change)="changed.emit($any($event.target).value)">
          @for (choice of f.choices; track choice.value) {
            <option [value]="choice.value" [selected]="choice.value === value()">{{ choice.label }}</option>
          }
        </select>
        @if (f.help) {
          <p class="form-hint">{{ f.help }}</p>
        }
      }
      @case ('number') {
        <label class="form-label" [for]="id()">{{ f.label }}</label>
        <div class="flex items-center gap-2">
          <input
            [id]="id()"
            type="number"
            class="form-input"
            [min]="f.min"
            [max]="f.max"
            [step]="f.step ?? 1"
            [value]="value()"
            (input)="changed.emit($any($event.target).valueAsNumber)"
          />
          @if (f.unit) {
            <span class="mt-1 text-sm text-slate-500">{{ f.unit }}</span>
          }
        </div>
        @if (f.help) {
          <p class="form-hint">{{ f.help }}</p>
        }
      }
      @case ('text') {
        <label class="form-label" [for]="id()">{{ f.label }}</label>
        <input
          [id]="id()"
          class="form-input"
          [placeholder]="f.placeholder ?? ''"
          [value]="value()"
          (input)="changed.emit($any($event.target).value)"
        />
        @if (f.help) {
          <p class="form-hint">{{ f.help }}</p>
        }
      }
      @case ('keyvalue') {
        <div class="space-y-2">
          @for (entry of entries(); track $index) {
            <div class="flex gap-2">
              <input
                class="form-input mt-0 font-mono"
                placeholder="NOMBRE"
                aria-label="Nombre de la variable"
                [value]="entry[0]"
                (input)="updateEntry($index, $any($event.target).value, entry[1])"
              />
              <input
                class="form-input mt-0 font-mono"
                placeholder="valor"
                aria-label="Valor de la variable"
                [value]="entry[1]"
                (input)="updateEntry($index, entry[0], $any($event.target).value)"
              />
              <button type="button" class="btn-secondary" (click)="removeEntry($index)" aria-label="Quitar variable">✕</button>
            </div>
          }
          <button type="button" class="btn-secondary" (click)="addEntry()">+ Añadir variable</button>
        </div>
      }
    }
  `,
})
export class OptionFieldComponent {
  readonly field = input.required<OptionField>();
  readonly value = input.required<OptionValue>();
  readonly changed = output<OptionValue>();

  protected readonly id = computed(() => `opt-${this.field().key}`);
  protected readonly entries = computed(() => Object.entries((this.value() as Record<string, string>) ?? {}));

  protected updateEntry(index: number, key: string, value: string): void {
    const next = this.entries().map((e, i) => (i === index ? [key, value] : e));
    this.emitEntries(next);
  }

  protected addEntry(): void {
    // Clave temporal única para que la fila nueva no pise otra vacía.
    this.emitEntries([...this.entries(), [`VARIABLE_${this.entries().length + 1}`, '']]);
  }

  protected removeEntry(index: number): void {
    this.emitEntries(this.entries().filter((_, i) => i !== index));
  }

  private emitEntries(entries: string[][]): void {
    this.changed.emit(Object.fromEntries(entries.map(([k, v]) => [k.trim().toUpperCase(), v])));
  }
}

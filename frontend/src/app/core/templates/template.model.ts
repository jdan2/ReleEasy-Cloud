import { OptionValue, TemplateId, UsageEstimate } from '../api/models';

/**
 * Definición declarativa de una plantilla. El formulario de alta se construye a partir de ella y
 * cada opción se traducirá en una variable del módulo Terraform de la plantilla (fase 4).
 */
export interface Template {
  id: TemplateId;
  name: string;
  tagline: string;
  description: string;
  /** Capas que despliega: determinan qué partes del coste se calculan. */
  layers: readonly Layer[];
  /** Servicios de AWS que crea siempre (los opcionales salen de las opciones). */
  awsServices: readonly string[];
  /** Estructura que debe tener el repositorio del cliente. */
  repoContract: readonly string[];
  optionGroups: readonly OptionGroup[];
  usageFields: readonly UsageField[];
  usagePresets: readonly UsagePreset[];
}

export type Layer = 'frontend' | 'backend';

export interface OptionGroup {
  id: string;
  title: string;
  description: string;
  fields: readonly OptionField[];
}

/** Muestra el campo solo si otra opción tiene un valor concreto. */
export interface VisibleWhen {
  key: string;
  equals: OptionValue;
}

interface BaseField {
  key: string;
  label: string;
  help?: string;
  visibleWhen?: VisibleWhen;
}

export interface ToggleField extends BaseField {
  type: 'toggle';
  default: boolean;
}

export interface SelectField extends BaseField {
  type: 'select';
  default: string;
  choices: readonly { value: string; label: string }[];
}

export interface NumberField extends BaseField {
  type: 'number';
  default: number;
  min: number;
  max: number;
  step?: number;
  unit?: string;
}

export interface TextField extends BaseField {
  type: 'text';
  default: string;
  placeholder?: string;
  /** Expresión regular que debe cumplir (si `required` o si tiene valor). */
  pattern?: string;
  patternMessage?: string;
  required?: boolean;
}

export interface KeyValueField extends BaseField {
  type: 'keyvalue';
  default: Record<string, string>;
}

export type OptionField = ToggleField | SelectField | NumberField | TextField | KeyValueField;

export interface UsageField {
  key: string;
  label: string;
  help?: string;
  unit: string;
  default: number;
  min: number;
  step?: number;
  visibleWhen?: VisibleWhen;
}

export interface UsagePreset {
  id: 'low' | 'medium' | 'high';
  label: string;
  description: string;
  values: UsageEstimate;
}

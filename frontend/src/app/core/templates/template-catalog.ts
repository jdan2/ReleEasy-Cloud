import { AppOptions, OptionValue, TemplateId, UsageEstimate } from '../api/models';
import { OptionField, OptionGroup, Template, UsageField, UsagePreset, VisibleWhen } from './template.model';

/*
 * Catálogo de plantillas. Cada grupo de opciones corresponde a componentes opcionales de AWS que
 * activa el módulo Terraform de la plantilla. La base de datos queda fuera de momento (D-19).
 */

// ---------- Grupos de opciones del front (S3 + CloudFront) ----------

const FRONT_OBSERVABILITY: OptionGroup = {
  id: 'front-observability',
  title: 'Observabilidad del front',
  description: 'Saber si tu web funciona y qué experimentan tus usuarios.',
  fields: [
    {
      type: 'toggle',
      key: 'front_alarms',
      label: 'Alarmas de errores 4xx/5xx',
      help: 'Alarmas de CloudWatch sobre la tasa de errores de CloudFront, con aviso por email.',
      default: true,
    },
    {
      type: 'toggle',
      key: 'front_rum',
      label: 'Monitorización de usuarios reales (RUM)',
      help: 'CloudWatch RUM mide tiempos de carga y errores JavaScript en los navegadores de tus visitantes.',
      default: false,
    },
  ],
};

const FRONT_SECURITY: OptionGroup = {
  id: 'front-security',
  title: 'Seguridad',
  description: 'Protección frente a tráfico malicioso.',
  fields: [
    {
      type: 'toggle',
      key: 'waf',
      label: 'Firewall de aplicaciones (AWS WAF)',
      help: 'Reglas gestionadas de AWS contra ataques comunes (inyecciones, bots) y límite de peticiones por IP.',
      default: false,
    },
  ],
};

const FRONT_DOMAIN: OptionGroup = {
  id: 'front-domain',
  title: 'Dominio',
  description: 'Por defecto la app usa un dominio *.cloudfront.net.',
  fields: [
    {
      type: 'toggle',
      key: 'custom_domain',
      label: 'Dominio propio',
      help: 'Zona DNS en Route 53 y certificado TLS gratuito de ACM.',
      default: false,
    },
    {
      type: 'text',
      key: 'domain_name',
      label: 'Nombre de dominio',
      placeholder: 'www.mitienda.es',
      pattern: '^([a-z0-9-]+\\.)+[a-z]{2,}$',
      patternMessage: 'Introduce un dominio válido en minúsculas (p. ej. www.mitienda.es).',
      required: true,
      default: '',
      visibleWhen: { key: 'custom_domain', equals: true },
    },
  ],
};

// ---------- Grupos de opciones del back (ECS Fargate) ----------

const BACK_EXPOSURE: OptionGroup = {
  id: 'back-exposure',
  title: 'Exposición de la API',
  description: 'Cómo llegan las peticiones a tu backend.',
  fields: [
    {
      type: 'select',
      key: 'api_exposure',
      label: 'Punto de entrada',
      help: 'API Gateway añade límite de peticiones y métricas por ruta, y evita el coste fijo del balanceador.',
      default: 'alb',
      choices: [
        { value: 'alb', label: 'Balanceador de carga (ALB)' },
        { value: 'api_gateway', label: 'API Gateway (HTTP API)' },
      ],
    },
    {
      type: 'number',
      key: 'api_throttle_rps',
      label: 'Límite de peticiones por segundo',
      unit: 'req/s',
      default: 100,
      min: 1,
      max: 10_000,
      visibleWhen: { key: 'api_exposure', equals: 'api_gateway' },
    },
  ],
};

const BACK_CAPACITY: OptionGroup = {
  id: 'back-capacity',
  title: 'Capacidad',
  description: 'Tamaño y número de contenedores de tu backend.',
  fields: [
    {
      type: 'select',
      key: 'task_size',
      label: 'Tamaño de cada contenedor',
      default: '0.5-1',
      choices: [
        { value: '0.25-0.5', label: '0,25 vCPU · 0,5 GB (pruebas)' },
        { value: '0.5-1', label: '0,5 vCPU · 1 GB (recomendado)' },
        { value: '1-2', label: '1 vCPU · 2 GB' },
        { value: '2-4', label: '2 vCPU · 4 GB' },
      ],
    },
    { type: 'number', key: 'min_tasks', label: 'Contenedores mínimos', default: 1, min: 1, max: 10 },
    {
      type: 'number',
      key: 'max_tasks',
      label: 'Contenedores máximos',
      help: 'Si es mayor que el mínimo, se activa el autoescalado por uso de CPU.',
      default: 2,
      min: 1,
      max: 20,
    },
    { type: 'number', key: 'cpu_target', label: 'CPU objetivo del autoescalado', unit: '%', default: 70, min: 20, max: 90 },
  ],
};

const BACK_OBSERVABILITY: OptionGroup = {
  id: 'back-observability',
  title: 'Observabilidad del backend',
  description: 'Logs, métricas, trazas y alarmas.',
  fields: [
    {
      type: 'select',
      key: 'log_retention_days',
      label: 'Retención de logs',
      default: '14',
      choices: [
        { value: '7', label: '7 días' },
        { value: '14', label: '14 días' },
        { value: '30', label: '30 días' },
        { value: '90', label: '90 días' },
      ],
    },
    {
      type: 'toggle',
      key: 'backend_alarms',
      label: 'Alarmas de CPU, memoria, errores 5xx y latencia',
      help: 'Te avisan por email cuando algo va mal.',
      default: true,
    },
    {
      type: 'toggle',
      key: 'container_insights',
      label: 'Métricas detalladas de contenedores (Container Insights)',
      default: false,
    },
    {
      type: 'toggle',
      key: 'xray_tracing',
      label: 'Trazas distribuidas (AWS X-Ray)',
      help: 'Sigue cada petición a través de tu código. Se muestrea el 5 % de las peticiones.',
      default: false,
    },
  ],
};

const BACK_MESSAGING: OptionGroup = {
  id: 'back-messaging',
  title: 'Mensajería',
  description: 'Procesamiento asíncrono de tareas.',
  fields: [
    {
      type: 'toggle',
      key: 'sqs_queue',
      label: 'Cola de mensajes (Amazon SQS)',
      help: 'Cola estándar con cola de mensajes fallidos (DLQ). Tu app recibe su URL en RELEASY_SQS_QUEUE_URL.',
      default: false,
    },
    {
      type: 'number',
      key: 'sqs_visibility_timeout',
      label: 'Tiempo de visibilidad',
      help: 'Tiempo que tiene tu app para procesar un mensaje antes de que vuelva a la cola.',
      unit: 's',
      default: 30,
      min: 0,
      max: 43_200,
      visibleWhen: { key: 'sqs_queue', equals: true },
    },
  ],
};

const BACK_ENV: OptionGroup = {
  id: 'back-env',
  title: 'Variables de entorno',
  description: 'Configuración no sensible de tu backend. Los secretos irán a Secrets Manager (próximamente).',
  fields: [{ type: 'keyvalue', key: 'env_vars', label: 'Variables', default: {} }],
};

const NOTIFICATIONS: OptionGroup = {
  id: 'notifications',
  title: 'Avisos',
  description: 'A dónde enviamos las alarmas.',
  fields: [
    {
      type: 'text',
      key: 'notification_email',
      label: 'Email para alarmas',
      help: 'Obligatorio si activas alguna alarma. Recibirás un email de AWS para confirmar la suscripción.',
      placeholder: 'ops@miempresa.es',
      pattern: '^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$',
      patternMessage: 'Introduce un email válido.',
      default: '',
    },
  ],
};

// ---------- Uso estimado ----------

const FRONT_USAGE: UsageField[] = [
  {
    key: 'pageViewsPerMonth',
    label: 'Visitas al mes',
    help: 'Páginas vistas en un mes.',
    unit: 'visitas',
    default: 100_000,
    min: 0,
    step: 1000,
  },
  { key: 'pageWeightMb', label: 'Peso medio de una página', unit: 'MB', default: 1.5, min: 0.1, step: 0.1 },
];

const BACK_USAGE: UsageField[] = [
  { key: 'apiRequestsPerMonth', label: 'Peticiones a la API al mes', unit: 'peticiones', default: 1_000_000, min: 0, step: 10_000 },
  { key: 'responseSizeKb', label: 'Tamaño medio de respuesta', unit: 'KB', default: 10, min: 1, step: 1 },
  {
    key: 'sqsMessagesPerMonth',
    label: 'Mensajes SQS al mes',
    unit: 'mensajes',
    default: 100_000,
    min: 0,
    step: 10_000,
    visibleWhen: { key: 'sqs_queue', equals: true },
  },
];

function presets(front: boolean, back: boolean): UsagePreset[] {
  const values = (pageViews: number, requests: number, messages: number): UsageEstimate => ({
    ...(front ? { pageViewsPerMonth: pageViews } : {}),
    ...(back ? { apiRequestsPerMonth: requests, sqsMessagesPerMonth: messages } : {}),
  });
  return [
    { id: 'low', label: 'Bajo', description: 'Web corporativa o app interna', values: values(10_000, 100_000, 10_000) },
    { id: 'medium', label: 'Medio', description: 'Tienda o servicio con clientes activos', values: values(100_000, 1_000_000, 100_000) },
    { id: 'high', label: 'Alto', description: 'Producto con mucho tráfico', values: values(1_000_000, 10_000_000, 1_000_000) },
  ];
}

// ---------- Plantillas ----------

export const TEMPLATES: readonly Template[] = [
  {
    id: 'angular-static',
    name: 'Angular',
    tagline: 'Web o SPA Angular en una CDN global',
    description:
      'Compila tu proyecto Angular y lo publica en un bucket S3 privado servido por CloudFront, con HTTPS y caché en todo el mundo.',
    layers: ['frontend'],
    awsServices: ['S3', 'CloudFront'],
    repoContract: [
      '/frontend con un proyecto Angular (package.json y angular.json).',
      'Se ejecuta npm ci y ng build; se publica la carpeta dist/<proyecto>/browser.',
    ],
    optionGroups: [FRONT_OBSERVABILITY, FRONT_SECURITY, FRONT_DOMAIN, NOTIFICATIONS],
    usageFields: FRONT_USAGE,
    usagePresets: presets(true, false),
  },
  {
    id: 'springboot-api',
    name: 'Spring Boot',
    tagline: 'API Java en contenedores gestionados',
    description:
      'Construye tu backend Spring Boot como imagen Docker y lo ejecuta en ECS Fargate, con despliegues sin cortes y vuelta atrás automática si falla.',
    layers: ['backend'],
    awsServices: ['ECR', 'ECS Fargate', 'VPC', 'CloudWatch Logs'],
    repoContract: [
      '/backend con Maven Wrapper (mvnw) y Java 21; se ejecuta ./mvnw -B verify.',
      'Dockerfile opcional; si no hay, se usa uno basado en eclipse-temurin:21-jre.',
      'La app escucha en el puerto 8080 y expone /actuator/health.',
    ],
    optionGroups: [BACK_EXPOSURE, BACK_CAPACITY, BACK_OBSERVABILITY, BACK_MESSAGING, BACK_ENV, NOTIFICATIONS],
    usageFields: BACK_USAGE,
    usagePresets: presets(false, true),
  },
  {
    id: 'angular-springboot',
    name: 'Angular + Spring Boot',
    tagline: 'Aplicación completa en un mismo dominio',
    description:
      'El front Angular en S3 + CloudFront y la API Spring Boot en ECS Fargate. CloudFront envía /api/* al backend, así no hay problemas de CORS.',
    layers: ['frontend', 'backend'],
    awsServices: ['S3', 'CloudFront', 'ECR', 'ECS Fargate', 'VPC', 'CloudWatch Logs'],
    repoContract: [
      '/frontend con el proyecto Angular y /backend con el proyecto Spring Boot (Maven Wrapper, Java 21).',
      'El front llama a la API con rutas relativas que empiezan por /api.',
      'El backend escucha en el puerto 8080 y expone /actuator/health.',
    ],
    optionGroups: [
      FRONT_OBSERVABILITY,
      FRONT_SECURITY,
      FRONT_DOMAIN,
      BACK_EXPOSURE,
      BACK_CAPACITY,
      BACK_OBSERVABILITY,
      BACK_MESSAGING,
      BACK_ENV,
      NOTIFICATIONS,
    ],
    usageFields: [...FRONT_USAGE, ...BACK_USAGE],
    usagePresets: presets(true, true),
  },
];

export function findTemplate(id: string): Template | undefined {
  return TEMPLATES.find((t) => t.id === id);
}

// ---------- Utilidades sobre opciones ----------

export function allFields(template: Template): OptionField[] {
  return template.optionGroups.flatMap((g) => g.fields);
}

export function defaultOptions(template: Template): AppOptions {
  return Object.fromEntries(allFields(template).map((f) => [f.key, structuredClone(f.default) as OptionValue]));
}

export function defaultUsage(template: Template): UsageEstimate {
  return Object.fromEntries(template.usageFields.map((f) => [f.key, f.default]));
}

export function isVisible(condition: VisibleWhen | undefined, options: AppOptions): boolean {
  return !condition || options[condition.key] === condition.equals;
}

const ENV_VAR_NAME = /^[A-Z_][A-Z0-9_]*$/;
const ALARM_KEYS = ['front_alarms', 'backend_alarms'];

/** Errores de validación de las opciones (vacío si son válidas). La API aplica las mismas reglas. */
export function validateOptions(template: Template, options: AppOptions): string[] {
  const errors: string[] = [];
  for (const field of allFields(template)) {
    if (!isVisible(field.visibleWhen, options)) {
      continue;
    }
    const value = options[field.key];
    switch (field.type) {
      case 'number':
        if (typeof value !== 'number' || Number.isNaN(value) || value < field.min || value > field.max) {
          errors.push(`${field.label}: debe estar entre ${field.min} y ${field.max}.`);
        }
        break;
      case 'text': {
        const text = typeof value === 'string' ? value.trim() : '';
        if (field.required && text === '') {
          errors.push(`${field.label}: es obligatorio.`);
        } else if (text !== '' && field.pattern && !new RegExp(field.pattern).test(text)) {
          errors.push(`${field.label}: ${field.patternMessage ?? 'formato no válido.'}`);
        }
        break;
      }
      case 'keyvalue':
        for (const key of Object.keys((value as Record<string, string>) ?? {})) {
          if (!ENV_VAR_NAME.test(key)) {
            errors.push(`Variable "${key}": usa mayúsculas, dígitos y guiones bajos (p. ej. API_TIMEOUT).`);
          } else if (key.startsWith('RELEASY_')) {
            errors.push(`Variable "${key}": el prefijo RELEASY_ está reservado para la plataforma.`);
          }
        }
        break;
      default:
        break;
    }
  }
  if (typeof options['min_tasks'] === 'number' && typeof options['max_tasks'] === 'number') {
    if (options['min_tasks'] > options['max_tasks']) {
      errors.push('Contenedores máximos: no puede ser menor que el mínimo.');
    }
  }
  const alarmsEnabled = ALARM_KEYS.some((key) => options[key] === true);
  const email = typeof options['notification_email'] === 'string' ? options['notification_email'].trim() : '';
  if (alarmsEnabled && email === '') {
    errors.push('Email para alarmas: es obligatorio si activas alguna alarma.');
  }
  return errors;
}

/** Resumen legible de los componentes opcionales activados, para el detalle de la app. */
export function describeOptions(template: Template, options: AppOptions): string[] {
  const lines: string[] = [];
  for (const field of allFields(template)) {
    if (!isVisible(field.visibleWhen, options)) {
      continue;
    }
    const value = options[field.key];
    if (field.type === 'toggle' && value === true) {
      lines.push(field.label);
    } else if (field.type === 'select') {
      const choice = field.choices.find((c) => c.value === value);
      lines.push(`${field.label}: ${choice?.label ?? String(value)}`);
    } else if (field.type === 'number') {
      lines.push(`${field.label}: ${String(value)}${field.unit ? ' ' + field.unit : ''}`);
    } else if (field.type === 'text' && typeof value === 'string' && value.trim() !== '') {
      lines.push(`${field.label}: ${value}`);
    } else if (field.type === 'keyvalue') {
      const count = Object.keys((value as Record<string, string>) ?? {}).length;
      if (count > 0) {
        lines.push(`${count} variable(s) de entorno`);
      }
    }
  }
  return lines;
}

export function templateName(id: TemplateId): string {
  return findTemplate(id)?.name ?? id;
}

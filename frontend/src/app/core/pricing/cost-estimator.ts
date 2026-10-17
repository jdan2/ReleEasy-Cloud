import { AppOptions, UsageEstimate } from '../api/models';
import { Template } from '../templates/template.model';
import { AWS_PRICES as P, HOURS_PER_MONTH, PLATFORM_FEE_PER_APP_USD } from './aws-prices';

/** Una línea del desglose: un servicio de AWS y su coste mensual estimado. */
export interface CostLine {
  service: string;
  detail: string;
  monthlyUsd: number;
}

export interface CostEstimate {
  lines: CostLine[];
  /** Lo que AWS facturaría a la cuenta del cliente. */
  awsMonthlyUsd: number;
  /** Coste de AWS si el backend escalara al máximo de contenedores todo el mes. */
  awsMaxMonthlyUsd: number;
  /** Cuota simulada de la plataforma. */
  platformFeeUsd: number;
  totalMonthlyUsd: number;
  assumptions: string[];
}

/*
 * Supuestos del modelo de consumo. Se muestran al cliente junto al desglose.
 */
const REQUESTS_PER_PAGE_VIEW = 10; // HTML, JS, CSS e imágenes (tras la caché del navegador)
const RUM_EVENTS_PER_PAGE_VIEW = 3;
const WAF_RULES = 3;
const LOG_KB_PER_REQUEST = 1;
const XRAY_SAMPLING = 0.05;
const SQS_REQUESTS_PER_MESSAGE = 3; // enviar, recibir y borrar
const NAT_GB_PER_MONTH = 5; // llamadas salientes del backend (APIs externas, ECR...)
const IMAGE_SIZE_GB = 0.4;
const FRONT_ALARMS = 2;
const BACKEND_ALARMS = 4;
const SECONDS_PER_MONTH = HOURS_PER_MONTH * 3600;

/**
 * Estima el coste mensual de una aplicación a partir de su plantilla, las opciones elegidas y el
 * uso previsto. Es una función pura, así que el formulario la recalcula en cada cambio.
 */
export function estimateMonthlyCost(template: Template, options: AppOptions, usage: UsageEstimate): CostEstimate {
  const hasFront = template.layers.includes('frontend');
  const hasBack = template.layers.includes('backend');
  const lines: CostLine[] = [];
  let backendScalingExtra = 0;

  if (hasFront) {
    lines.push(...frontendLines(options, usage, hasBack));
  }
  if (hasBack) {
    const back = backendLines(options, usage, hasFront);
    lines.push(...back.lines);
    backendScalingExtra = back.scalingExtraUsd;
  }

  const awsMonthlyUsd = round(lines.reduce((sum, line) => sum + line.monthlyUsd, 0));
  return {
    lines: lines.map((l) => ({ ...l, monthlyUsd: round(l.monthlyUsd) })),
    awsMonthlyUsd,
    awsMaxMonthlyUsd: round(awsMonthlyUsd + backendScalingExtra),
    platformFeeUsd: PLATFORM_FEE_PER_APP_USD,
    totalMonthlyUsd: round(awsMonthlyUsd + PLATFORM_FEE_PER_APP_USD),
    assumptions: assumptions(hasFront, hasBack),
  };
}

function frontendLines(options: AppOptions, usage: UsageEstimate, hasBack: boolean): CostLine[] {
  const pageViews = num(usage['pageViewsPerMonth']);
  const requests = pageViews * REQUESTS_PER_PAGE_VIEW;
  const transferGb = (pageViews * num(usage['pageWeightMb'])) / 1024;
  const apiRequests = hasBack ? num(usage['apiRequestsPerMonth']) : 0;
  const lines: CostLine[] = [
    {
      service: 'CloudFront',
      detail: `${fmt(transferGb)} GB servidos y ${fmt(requests)} peticiones`,
      monthlyUsd: transferGb * P.cloudFront.perGbOut + (requests / 10_000) * P.cloudFront.per10kHttpsRequests,
    },
    {
      service: 'S3',
      detail: 'Alojamiento del front (la mayoría de peticiones las sirve la caché)',
      monthlyUsd: 0.5 * P.s3.perGbMonth + ((requests * 0.05) / 1000) * P.s3.per1kGetRequests,
    },
  ];
  if (options['waf'] === true) {
    lines.push({
      service: 'AWS WAF',
      detail: `1 ACL web, ${WAF_RULES} reglas y ${fmt(requests + apiRequests)} peticiones inspeccionadas`,
      monthlyUsd: P.waf.perWebAcl + WAF_RULES * P.waf.perRule + ((requests + apiRequests) / 1e6) * P.waf.perMillionRequests,
    });
  }
  if (options['front_rum'] === true) {
    const events = pageViews * RUM_EVENTS_PER_PAGE_VIEW;
    lines.push({
      service: 'CloudWatch RUM',
      detail: `${fmt(events)} eventos de navegador`,
      monthlyUsd: (events / 100_000) * P.cloudWatch.rumPer100kEvents,
    });
  }
  if (options['front_alarms'] === true) {
    lines.push({ service: 'CloudWatch Alarms', detail: `${FRONT_ALARMS} alarmas del front`, monthlyUsd: FRONT_ALARMS * P.cloudWatch.perAlarm });
  }
  if (options['custom_domain'] === true) {
    lines.push({
      service: 'Route 53 + ACM',
      detail: 'Zona DNS y consultas; el certificado TLS es gratuito',
      monthlyUsd: P.route53.perHostedZone + (pageViews / 1e6) * P.route53.perMillionQueries,
    });
  }
  return lines;
}

function backendLines(
  options: AppOptions,
  usage: UsageEstimate,
  behindCloudFront: boolean,
): { lines: CostLine[]; scalingExtraUsd: number } {
  const requests = num(usage['apiRequestsPerMonth']);
  const responseGb = (requests * num(usage['responseSizeKb'])) / (1024 * 1024);
  const [vcpu, memoryGb] = String(options['task_size'] ?? '0.5-1').split('-').map(Number);
  const minTasks = num(options['min_tasks'], 1);
  const maxTasks = Math.max(minTasks, num(options['max_tasks'], minTasks));
  const taskMonthly = (vcpu * P.fargate.perVcpuHour + memoryGb * P.fargate.perGbHour) * HOURS_PER_MONTH;
  const useApiGateway = options['api_exposure'] === 'api_gateway';
  const lines: CostLine[] = [];

  lines.push({
    service: 'ECS Fargate',
    detail:
      `${minTasks} contenedor(es) de ${vcpu} vCPU y ${memoryGb} GB` +
      (maxTasks > minTasks ? `, con autoescalado hasta ${maxTasks}` : ''),
    monthlyUsd: minTasks * taskMonthly,
  });

  if (useApiGateway) {
    lines.push({
      service: 'API Gateway',
      detail: `HTTP API con ${fmt(requests)} peticiones, VPC Link y Cloud Map`,
      monthlyUsd: (requests / 1e6) * P.apiGateway.perMillionHttpApiRequests + maxTasks * P.cloudMap.perRegisteredInstance,
    });
  } else {
    const averageRps = requests / SECONDS_PER_MONTH;
    const lcus = Math.max(1, averageRps / 25);
    lines.push({
      service: 'Application Load Balancer',
      detail: `Coste fijo por hora y ${fmt(lcus)} LCU de media`,
      monthlyUsd: (P.alb.perHour + lcus * P.alb.perLcuHour) * HOURS_PER_MONTH,
    });
  }

  lines.push({
    service: 'NAT Gateway',
    detail: `Salida a internet de los contenedores (~${NAT_GB_PER_MONTH} GB)`,
    monthlyUsd: P.natGateway.perHour * HOURS_PER_MONTH + NAT_GB_PER_MONTH * P.natGateway.perGbProcessed,
  });

  lines.push(
    behindCloudFront
      ? {
          service: 'CloudFront (/api/*)',
          detail: `${fmt(responseGb)} GB de respuestas de la API`,
          monthlyUsd: responseGb * P.cloudFront.perGbOut + (requests / 10_000) * P.cloudFront.per10kHttpsRequests,
        }
      : {
          service: 'Transferencia de datos',
          detail: `${fmt(responseGb)} GB de respuestas hacia internet`,
          monthlyUsd: responseGb * P.dataTransfer.perGbOutToInternet,
        },
  );

  const logsGb = (requests * LOG_KB_PER_REQUEST) / (1024 * 1024);
  const retentionDays = num(Number(options['log_retention_days']), 14);
  lines.push({
    service: 'CloudWatch Logs',
    detail: `${fmt(logsGb)} GB de logs al mes, retenidos ${retentionDays} días`,
    monthlyUsd: logsGb * P.cloudWatch.perGbLogsIngested + logsGb * (retentionDays / 30) * P.cloudWatch.perGbLogsStoredMonth,
  });

  if (options['backend_alarms'] === true) {
    lines.push({
      service: 'CloudWatch Alarms',
      detail: `${BACKEND_ALARMS} alarmas del backend`,
      monthlyUsd: BACKEND_ALARMS * P.cloudWatch.perAlarm,
    });
  }
  if (options['container_insights'] === true) {
    lines.push({ service: 'Container Insights', detail: 'Métricas detalladas del servicio', monthlyUsd: P.containerInsightsPerService });
  }
  if (options['xray_tracing'] === true) {
    const traces = requests * XRAY_SAMPLING;
    lines.push({
      service: 'AWS X-Ray',
      detail: `${fmt(traces)} trazas (muestreo del ${XRAY_SAMPLING * 100} %)`,
      monthlyUsd: (traces / 1e6) * P.xray.perMillionTracesRecorded,
    });
  }
  if (options['sqs_queue'] === true) {
    const messages = num(usage['sqsMessagesPerMonth']);
    lines.push({
      service: 'Amazon SQS',
      detail: `${fmt(messages)} mensajes (cola + DLQ)`,
      monthlyUsd: ((messages * SQS_REQUESTS_PER_MESSAGE) / 1e6) * P.sqs.perMillionRequests,
    });
  }
  lines.push({ service: 'ECR', detail: 'Imágenes Docker del backend', monthlyUsd: IMAGE_SIZE_GB * P.ecr.perGbMonth });

  return { lines, scalingExtraUsd: (maxTasks - minTasks) * taskMonthly };
}

function assumptions(hasFront: boolean, hasBack: boolean): string[] {
  const list = ['Precios bajo demanda aproximados, sin capa gratuita ni impuestos.'];
  if (hasFront) {
    list.push(`Cada visita genera unas ${REQUESTS_PER_PAGE_VIEW} peticiones a CloudFront.`);
  }
  if (hasBack) {
    list.push('El backend funciona 24/7 con el mínimo de contenedores.');
    list.push(`Se generan ~${LOG_KB_PER_REQUEST} KB de logs por petición.`);
  }
  return list;
}

function num(value: unknown, fallback = 0): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}

function round(value: number): number {
  return Math.round(value * 100) / 100;
}

function fmt(value: number): string {
  return new Intl.NumberFormat('es-ES', { maximumFractionDigits: value < 10 ? 2 : 0 }).format(value);
}

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
  /** Coste de AWS si el backend escalara al máximo de instancias todo el mes. */
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
const NAT_GB_PER_MONTH = 5; // llamadas salientes del backend (APIs externas, ECR...)
const DISK_GB_PER_INSTANCE = 20; // volumen raíz gp3 de cada instancia
const DETAILED_METRICS_PER_INSTANCE = 7; // métricas de EC2 que pasan a ser de 1 minuto
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
  const instanceType = String(options['instance_type'] ?? 't3.small');
  const minInstances = num(options['min_instances'], 1);
  const maxInstances = Math.max(minInstances, num(options['max_instances'], minInstances));
  const instanceMonthly =
    num(P.ec2PerHour[instanceType], P.ec2PerHour['t3.small']) * HOURS_PER_MONTH + DISK_GB_PER_INSTANCE * P.ebs.gp3PerGbMonth;
  const useApiGateway = options['api_exposure'] === 'api_gateway';
  const lines: CostLine[] = [];

  lines.push({
    service: 'EC2 Auto Scaling',
    detail:
      `${minInstances} instancia(s) ${instanceType} con ${DISK_GB_PER_INSTANCE} GB de disco` +
      (maxInstances > minInstances ? `, con autoescalado hasta ${maxInstances}` : ''),
    monthlyUsd: minInstances * instanceMonthly,
  });

  // El ALB siempre existe: es el que reparte entre instancias. Con API Gateway pasa a ser interno.
  const averageRps = requests / SECONDS_PER_MONTH;
  const lcus = Math.max(1, averageRps / 25);
  lines.push({
    service: 'Application Load Balancer',
    detail: `${useApiGateway ? 'Interno, ' : ''}coste fijo por hora y ${fmt(lcus)} LCU de media`,
    monthlyUsd: (P.alb.perHour + lcus * P.alb.perLcuHour) * HOURS_PER_MONTH,
  });
  if (useApiGateway) {
    lines.push({
      service: 'API Gateway',
      detail: `HTTP API con ${fmt(requests)} peticiones (el VPC Link no tiene coste)`,
      monthlyUsd: (requests / 1e6) * P.apiGateway.perMillionHttpApiRequests,
    });
  }

  lines.push({
    service: 'NAT Gateway',
    detail: `Salida a internet de las instancias (~${NAT_GB_PER_MONTH} GB)`,
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
  if (options['detailed_monitoring'] === true) {
    lines.push({
      service: 'Monitorización detallada',
      detail: `${DETAILED_METRICS_PER_INSTANCE} métricas de 1 minuto por instancia`,
      monthlyUsd: minInstances * DETAILED_METRICS_PER_INSTANCE * P.cloudWatch.perCustomMetric,
    });
  }
  if (options['xray_tracing'] === true) {
    const traces = requests * XRAY_SAMPLING;
    lines.push({
      service: 'AWS X-Ray',
      detail: `${fmt(traces)} trazas (muestreo del ${XRAY_SAMPLING * 100} %)`,
      monthlyUsd: (traces / 1e6) * P.xray.perMillionTracesRecorded,
    });
  }
  lines.push({ service: 'ECR', detail: 'Imágenes Docker del backend', monthlyUsd: IMAGE_SIZE_GB * P.ecr.perGbMonth });

  return { lines, scalingExtraUsd: (maxInstances - minInstances) * instanceMonthly };
}

function assumptions(hasFront: boolean, hasBack: boolean): string[] {
  const list = ['Precios bajo demanda aproximados, sin capa gratuita ni impuestos.'];
  if (hasFront) {
    list.push(`Cada visita genera unas ${REQUESTS_PER_PAGE_VIEW} peticiones a CloudFront.`);
  }
  if (hasBack) {
    list.push('El backend funciona 24/7 con el mínimo de instancias.');
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

/**
 * Precios unitarios de referencia de AWS (USD, bajo demanda, región eu-west-1 · Irlanda).
 *
 * Son APROXIMADOS: sirven para que el cliente se haga una idea del orden de magnitud. No incluyen
 * capa gratuita, descuentos, impuestos ni transferencias poco habituales. Se revisaron por última vez
 * en {@link PRICES_REVIEWED_AT} y deben contrastarse con la AWS Pricing Calculator
 * (https://calculator.aws) antes de usarlos para algo más que una estimación. Ver D-20.
 */
export const PRICES_REVIEWED_AT = '2026-10';
export const PRICES_REGION = 'eu-west-1 (Irlanda)';

export const HOURS_PER_MONTH = 730;

export const AWS_PRICES = {
  cloudFront: {
    perGbOut: 0.085,
    per10kHttpsRequests: 0.012,
  },
  s3: {
    perGbMonth: 0.023,
    per1kGetRequests: 0.0004,
  },
  waf: {
    perWebAcl: 5,
    perRule: 1,
    perMillionRequests: 0.6,
  },
  route53: {
    perHostedZone: 0.5,
    perMillionQueries: 0.4,
  },
  cloudWatch: {
    perAlarm: 0.1,
    perGbLogsIngested: 0.57,
    perGbLogsStoredMonth: 0.03,
    rumPer100kEvents: 1,
  },
  fargate: {
    perVcpuHour: 0.04048,
    perGbHour: 0.004445,
  },
  alb: {
    perHour: 0.0252,
    perLcuHour: 0.008,
  },
  natGateway: {
    perHour: 0.048,
    perGbProcessed: 0.048,
  },
  apiGateway: {
    perMillionHttpApiRequests: 1.11,
  },
  cloudMap: {
    perRegisteredInstance: 0.1,
  },
  dataTransfer: {
    perGbOutToInternet: 0.09,
  },
  sqs: {
    perMillionRequests: 0.4,
  },
  xray: {
    perMillionTracesRecorded: 5,
  },
  ecr: {
    perGbMonth: 0.1,
  },
  /** Container Insights cobra por métrica; se aproxima con un importe fijo por servicio. */
  containerInsightsPerService: 3,
} as const;

/** Cuota mensual simulada de la plataforma por aplicación (lo que cobraría ReleEasy). */
export const PLATFORM_FEE_PER_APP_USD = 9;

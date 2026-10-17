import { AppOptions } from '../api/models';
import { defaultOptions, findTemplate } from '../templates/template-catalog';
import { Template } from '../templates/template.model';
import { AWS_PRICES, HOURS_PER_MONTH, PLATFORM_FEE_PER_APP_USD } from './aws-prices';
import { CostEstimate, estimateMonthlyCost } from './cost-estimator';

const angular = findTemplate('angular-static')!;
const spring = findTemplate('springboot-api')!;
const fullstack = findTemplate('angular-springboot')!;

function estimate(template: Template, options: AppOptions = {}, usage = {}): CostEstimate {
  return estimateMonthlyCost(template, { ...defaultOptions(template), ...options }, usage);
}

function services(e: CostEstimate): string[] {
  return e.lines.map((l) => l.service);
}

describe('estimateMonthlyCost', () => {
  it('Angular: calcula CloudFront según visitas y peso de página', () => {
    const e = estimate(angular, { front_alarms: false }, { pageViewsPerMonth: 100_000, pageWeightMb: 1.024 });

    // 100.000 visitas × 1,024 MB = 100 GB servidos y 1.000.000 de peticiones
    const cloudFront = e.lines.find((l) => l.service === 'CloudFront')!;
    expect(cloudFront.monthlyUsd).toBeCloseTo(100 * AWS_PRICES.cloudFront.perGbOut + 100 * AWS_PRICES.cloudFront.per10kHttpsRequests, 2);
    expect(services(e)).toEqual(['CloudFront', 'S3']);
  });

  it('cada componente opcional añade su línea al desglose', () => {
    const e = estimate(angular, { waf: true, front_rum: true, front_alarms: true, custom_domain: true }, { pageViewsPerMonth: 10_000, pageWeightMb: 1 });

    expect(services(e)).toEqual(
      expect.arrayContaining(['AWS WAF', 'CloudWatch RUM', 'CloudWatch Alarms', 'Route 53 + ACM']),
    );
    expect(e.lines.find((l) => l.service === 'AWS WAF')!.monthlyUsd).toBeGreaterThanOrEqual(AWS_PRICES.waf.perWebAcl);
  });

  it('el total suma la infraestructura AWS y la cuota de la plataforma', () => {
    const e = estimate(spring, {}, { apiRequestsPerMonth: 1_000_000, responseSizeKb: 10 });
    const sum = e.lines.reduce((acc, l) => acc + l.monthlyUsd, 0);

    expect(e.awsMonthlyUsd).toBeCloseTo(sum, 1);
    expect(e.platformFeeUsd).toBe(PLATFORM_FEE_PER_APP_USD);
    expect(e.totalMonthlyUsd).toBeCloseTo(e.awsMonthlyUsd + PLATFORM_FEE_PER_APP_USD, 2);
  });

  it('Spring Boot: Fargate depende del tamaño y número de contenedores', () => {
    const small = estimate(spring, { task_size: '0.25-0.5', min_tasks: 1, max_tasks: 1 }, {});
    const big = estimate(spring, { task_size: '1-2', min_tasks: 2, max_tasks: 2 }, {});
    const fargate = (e: CostEstimate): number => e.lines.find((l) => l.service === 'ECS Fargate')!.monthlyUsd;

    const expected = (0.25 * AWS_PRICES.fargate.perVcpuHour + 0.5 * AWS_PRICES.fargate.perGbHour) * HOURS_PER_MONTH;
    expect(fargate(small)).toBeCloseTo(expected, 2);
    expect(fargate(big)).toBeCloseTo(fargate(small) * 8, 1);
  });

  it('con autoescalado informa del coste si escala al máximo', () => {
    const e = estimate(spring, { min_tasks: 1, max_tasks: 3 }, {});
    expect(e.awsMaxMonthlyUsd).toBeGreaterThan(e.awsMonthlyUsd);

    const fixed = estimate(spring, { min_tasks: 2, max_tasks: 2 }, {});
    expect(fixed.awsMaxMonthlyUsd).toBe(fixed.awsMonthlyUsd);
  });

  it('API Gateway sustituye al balanceador y cobra por petición', () => {
    const alb = estimate(spring, { api_exposure: 'alb' }, { apiRequestsPerMonth: 1_000_000 });
    const gateway = estimate(spring, { api_exposure: 'api_gateway' }, { apiRequestsPerMonth: 1_000_000 });

    expect(services(alb)).toContain('Application Load Balancer');
    expect(services(gateway)).not.toContain('Application Load Balancer');
    expect(services(gateway)).toContain('API Gateway');
    expect(gateway.awsMonthlyUsd).toBeLessThan(alb.awsMonthlyUsd);
  });

  it('SQS y X-Ray escalan con el uso', () => {
    const e = estimate(spring, { sqs_queue: true, xray_tracing: true }, { apiRequestsPerMonth: 2_000_000, sqsMessagesPerMonth: 1_000_000 });

    expect(e.lines.find((l) => l.service === 'Amazon SQS')!.monthlyUsd).toBeCloseTo(3 * AWS_PRICES.sqs.perMillionRequests, 2);
    expect(e.lines.find((l) => l.service === 'AWS X-Ray')!.monthlyUsd).toBeCloseTo(0.1 * AWS_PRICES.xray.perMillionTracesRecorded, 2);
  });

  it('Angular + Spring Boot sirve la API a través de CloudFront', () => {
    const e = estimate(fullstack, {}, { pageViewsPerMonth: 10_000, pageWeightMb: 1, apiRequestsPerMonth: 100_000, responseSizeKb: 10 });

    expect(services(e)).toEqual(expect.arrayContaining(['CloudFront', 'S3', 'ECS Fargate', 'CloudFront (/api/*)']));
    expect(services(e)).not.toContain('Transferencia de datos');
  });
});

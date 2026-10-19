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

  it('Spring Boot: EC2 depende del tipo y número de instancias', () => {
    const small = estimate(spring, { instance_type: 't3.small', min_instances: 1, max_instances: 1 }, {});
    const big = estimate(spring, { instance_type: 't3.large', min_instances: 2, max_instances: 2 }, {});
    const ec2 = (e: CostEstimate): number => e.lines.find((l) => l.service === 'EC2 Auto Scaling')!.monthlyUsd;

    const disk = 20 * AWS_PRICES.ebs.gp3PerGbMonth;
    expect(ec2(small)).toBeCloseTo(AWS_PRICES.ec2PerHour['t3.small'] * HOURS_PER_MONTH + disk, 1);
    expect(ec2(big)).toBeCloseTo(2 * (AWS_PRICES.ec2PerHour['t3.large'] * HOURS_PER_MONTH + disk), 1);
    expect(services(small)).not.toContain('ECS Fargate');
  });

  it('con autoescalado informa del coste si escala al máximo', () => {
    const e = estimate(spring, { min_instances: 1, max_instances: 3 }, {});
    expect(e.awsMaxMonthlyUsd).toBeGreaterThan(e.awsMonthlyUsd);

    const fixed = estimate(spring, { min_instances: 2, max_instances: 2 }, {});
    expect(fixed.awsMaxMonthlyUsd).toBe(fixed.awsMonthlyUsd);
  });

  it('API Gateway se suma al balanceador interno y cobra por petición', () => {
    const alb = estimate(spring, { api_exposure: 'alb' }, { apiRequestsPerMonth: 1_000_000 });
    const gateway = estimate(spring, { api_exposure: 'api_gateway' }, { apiRequestsPerMonth: 1_000_000 });

    expect(services(alb)).toContain('Application Load Balancer');
    expect(services(alb)).not.toContain('API Gateway');
    expect(services(gateway)).toEqual(expect.arrayContaining(['Application Load Balancer', 'API Gateway']));
    expect(gateway.awsMonthlyUsd).toBeCloseTo(alb.awsMonthlyUsd + AWS_PRICES.apiGateway.perMillionHttpApiRequests, 1);
  });

  it('X-Ray escala con el uso y la monitorización detallada con las instancias', () => {
    const e = estimate(spring, { xray_tracing: true, detailed_monitoring: true, min_instances: 2, max_instances: 2 }, { apiRequestsPerMonth: 2_000_000 });

    expect(e.lines.find((l) => l.service === 'AWS X-Ray')!.monthlyUsd).toBeCloseTo(0.1 * AWS_PRICES.xray.perMillionTracesRecorded, 2);
    expect(e.lines.find((l) => l.service === 'Monitorización detallada')!.monthlyUsd).toBeCloseTo(2 * 7 * AWS_PRICES.cloudWatch.perCustomMetric, 2);
  });

  it('Spring Boot sin CloudFront cobra la transferencia de datos hacia internet', () => {
    const e = estimate(spring, {}, { apiRequestsPerMonth: 1_048_576, responseSizeKb: 10 });
    // 1.048.576 respuestas × 10 KB = 10 GB
    expect(e.lines.find((l) => l.service === 'Transferencia de datos')!.monthlyUsd).toBeCloseTo(10 * AWS_PRICES.dataTransfer.perGbOutToInternet, 2);
  });

  it('Angular + Spring Boot sirve la API a través de CloudFront', () => {
    const e = estimate(fullstack, {}, { pageViewsPerMonth: 10_000, pageWeightMb: 1, apiRequestsPerMonth: 100_000, responseSizeKb: 10 });

    expect(services(e)).toEqual(expect.arrayContaining(['CloudFront', 'S3', 'EC2 Auto Scaling', 'CloudFront (/api/*)']));
    expect(services(e)).not.toContain('Transferencia de datos');
  });
});

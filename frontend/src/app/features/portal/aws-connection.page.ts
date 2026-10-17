import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Tenant } from '../../core/api/models';
import { ReleasyApi } from '../../core/api/releasy-api.service';
import { describeHttpError } from '../../shared/http-error';
import { StatusBadge } from '../../shared/status-badge';

const ROLE_NAME = 'ReleEasyDeployRole';
/** URL pública de la plantilla de CloudFormation del rol (se publicará en la fase 4). */
const ROLE_TEMPLATE_URL = 'https://releasy-public.s3.eu-west-1.amazonaws.com/cloudformation/releasy-deploy-role.yaml';

/**
 * Conexión con la cuenta AWS del propio cliente (D-18).
 *
 * El cliente crea en su cuenta, con un stack de CloudFormation, un rol que confía en la cuenta de la
 * plataforma y exige su External ID. La plataforma nunca recibe claves de acceso: solo asume ese rol
 * con credenciales temporales.
 */
@Component({
  selector: 'app-aws-connection-page',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, StatusBadge],
  templateUrl: './aws-connection.page.html',
})
export class AwsConnectionPage implements OnInit {
  private readonly api = inject(ReleasyApi);

  protected readonly roleName = ROLE_NAME;
  protected readonly tenant = signal<Tenant | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly form = inject(NonNullableFormBuilder).group({
    roleArn: ['', [Validators.required, Validators.pattern(/^arn:aws:iam::\d{12}:role\/[\w+=,.@/-]{1,64}$/)]],
  });

  /** Enlace "Quick-create" de CloudFormation con los parámetros ya rellenos. */
  protected readonly quickCreateUrl = computed(() => {
    const aws = this.tenant()?.aws;
    if (!aws) {
      return '';
    }
    const params = new URLSearchParams({
      templateURL: ROLE_TEMPLATE_URL,
      stackName: 'releasy-connection',
      param_PlatformAccountId: aws.platformAccountId,
      param_ExternalId: aws.externalId,
    });
    return `https://console.aws.amazon.com/cloudformation/home#/stacks/quickcreate?${params.toString()}`;
  });

  /** Lo que crea el stack, para que el cliente sepa exactamente qué permisos concede. */
  protected readonly cloudFormationPreview = computed(() => {
    const aws = this.tenant()?.aws;
    return aws
      ? `Resources:
  ReleEasyDeployRole:
    Type: AWS::IAM::Role
    Properties:
      RoleName: ${ROLE_NAME}
      AssumeRolePolicyDocument:
        Statement:
          - Effect: Allow
            Principal: { AWS: arn:aws:iam::${aws.platformAccountId}:root }
            Action: sts:AssumeRole
            Condition:
              StringEquals: { sts:ExternalId: ${aws.externalId} }
      # Permisos limitados a los servicios que usan las plantillas
      # (S3, CloudFront, ECS, ECR, ELB, VPC, CloudWatch, SQS, API Gateway...)
      ManagedPolicyArns: [ ... ]`
      : '';
  });

  ngOnInit(): void {
    this.api.myTenant().subscribe({
      next: (tenant) => this.tenant.set(tenant),
      error: (e: unknown) => this.error.set(describeHttpError(e)),
    });
  }

  protected connect(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.run(this.api.connectAws({ roleArn: this.form.getRawValue().roleArn.trim() }));
  }

  protected disconnect(): void {
    this.run(this.api.disconnectAws());
  }

  protected copy(text: string): void {
    void navigator.clipboard?.writeText(text);
  }

  private run(request: ReturnType<ReleasyApi['connectAws']>): void {
    this.saving.set(true);
    this.error.set(null);
    request.subscribe({
      next: (tenant) => {
        this.tenant.set(tenant);
        this.saving.set(false);
        this.form.reset();
      },
      error: (e: unknown) => {
        this.error.set(describeHttpError(e));
        this.saving.set(false);
      },
    });
  }
}

/**
 * Tipos de la API de la plataforma. Reflejan los DTOs de `backend/src/main/java/com/releasy/web/dto`.
 * Las fechas llegan como texto ISO-8601.
 */

export type TenantStatus = 'CREATING' | 'ACTIVE' | 'FAILED';
export type ReleaseStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
export type AppPlan = 'BASIC' | 'STANDARD';

export interface Tenant {
  id: string;
  name: string;
  email: string;
  status: TenantStatus;
  accountId: string | null;
  roleArn: string | null;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface App {
  id: string;
  tenantId: string;
  name: string;
  repository: string;
  branch: string;
  plan: AppPlan;
  url: string;
  createdAt: string;
  updatedAt: string;
}

export interface Release {
  id: string;
  appId: string;
  commitSha: string;
  commitMessage: string | null;
  status: ReleaseStatus;
  url: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTenantRequest {
  name: string;
  email: string;
}

export interface CreateAppRequest {
  name: string;
  repository: string;
  branch: string;
  plan: AppPlan;
}

/**
 * Tipos del contrato entre el portal y la API de la plataforma. Las fechas son texto ISO-8601.
 *
 * Este contrato lo define el portal y de momento solo lo implementa el modo demo (`core/mock`). El
 * backend tendrá que adaptarse a él (ver CLAUDE.md, decisión D-18).
 */

// ---------- Usuarios y sesión ----------

export type Role = 'ADMIN' | 'CLIENT';

export interface User {
  id: string;
  email: string;
  name: string;
  role: Role;
  /** Cliente al que pertenece el usuario; `null` para el administrador. */
  tenantId: string | null;
}

export interface Session {
  /** Token opaco que se envía como `Authorization: Bearer <token>`. */
  token: string;
  user: User;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  companyName: string;
  email: string;
  password: string;
}

// ---------- Clientes y conexiones ----------

export type ConnectionStatus = 'NOT_CONNECTED' | 'CONNECTED';

/**
 * Conexión con la cuenta AWS del propio cliente: un rol IAM que confía en la cuenta de la plataforma
 * y exige el `externalId` (ver D-18).
 */
export interface AwsConnection {
  status: ConnectionStatus;
  /** Identificador secreto que debe exigir la política de confianza del rol. */
  externalId: string;
  /** Cuenta AWS de la plataforma, en la que debe confiar el rol. */
  platformAccountId: string;
  roleArn: string | null;
  accountId: string | null;
  connectedAt: string | null;
}

/** Instalación de la GitHub App de ReleEasy en la cuenta u organización del cliente. */
export interface GithubConnection {
  status: ConnectionStatus;
  accountLogin: string | null;
  installationId: number | null;
  connectedAt: string | null;
}

export interface Tenant {
  id: string;
  name: string;
  email: string;
  aws: AwsConnection;
  github: GithubConnection;
  appCount: number;
  createdAt: string;
}

export interface ConnectAwsRequest {
  roleArn: string;
}

export interface GithubRepository {
  fullName: string;
  description: string;
  private: boolean;
  defaultBranch: string;
  branches: string[];
}

// ---------- Aplicaciones y releases ----------

export type ReleaseStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
export type TemplateId = 'angular-static' | 'springboot-api' | 'angular-springboot';

/** Valor de una opción de plantilla (interruptor, número, texto o pares clave-valor). */
export type OptionValue = boolean | number | string | Record<string, string>;
export type AppOptions = Record<string, OptionValue>;
/** Uso estimado por el cliente (visitas, peticiones...), usado para estimar costes. */
export type UsageEstimate = Record<string, number>;

export interface App {
  id: string;
  tenantId: string;
  name: string;
  templateId: TemplateId;
  repository: string;
  branch: string;
  options: AppOptions;
  usage: UsageEstimate;
  url: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAppRequest {
  name: string;
  templateId: TemplateId;
  repository: string;
  branch: string;
  options: AppOptions;
  usage: UsageEstimate;
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

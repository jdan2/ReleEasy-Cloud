import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { Template } from '../templates/template.model';
import {
  App,
  ConnectAwsRequest,
  ConnectGithubRequest,
  CreateAppRequest,
  GithubRepository,
  LoginRequest,
  RegisterRequest,
  Release,
  Session,
  Tenant,
} from './models';

/**
 * Cliente HTTP de la API de la plataforma. El token de sesión lo añade `authInterceptor`.
 *
 * Rutas:
 * - `/api/auth/*`: registro e inicio de sesión (públicas);
 * - `/api/me/*`: el cliente que ha iniciado sesión, sus conexiones y sus apps;
 * - `/api/tenants/*`: vista del administrador;
 * - `/api/apps/{id}`: detalle de una app (su propietario o el administrador).
 */
@Injectable({ providedIn: 'root' })
export class ReleasyApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${inject(APP_CONFIG).apiUrl}/api`;

  // ---- Autenticación ----

  login(request: LoginRequest): Observable<Session> {
    return this.http.post<Session>(`${this.baseUrl}/auth/login`, request);
  }

  register(request: RegisterRequest): Observable<Session> {
    return this.http.post<Session>(`${this.baseUrl}/auth/register`, request);
  }

  // ---- Cliente ----

  myTenant(): Observable<Tenant> {
    return this.http.get<Tenant>(`${this.baseUrl}/me/tenant`);
  }

  connectAws(request: ConnectAwsRequest): Observable<Tenant> {
    return this.http.post<Tenant>(`${this.baseUrl}/me/aws-connection`, request);
  }

  disconnectAws(): Observable<Tenant> {
    return this.http.delete<Tenant>(`${this.baseUrl}/me/aws-connection`);
  }

  connectGithub(request: ConnectGithubRequest): Observable<Tenant> {
    return this.http.post<Tenant>(`${this.baseUrl}/me/github-connection`, request);
  }

  disconnectGithub(): Observable<Tenant> {
    return this.http.delete<Tenant>(`${this.baseUrl}/me/github-connection`);
  }

  myRepositories(): Observable<GithubRepository[]> {
    return this.http.get<GithubRepository[]>(`${this.baseUrl}/me/github/repositories`);
  }

  myApps(): Observable<App[]> {
    return this.http.get<App[]>(`${this.baseUrl}/me/apps`);
  }

  createApp(request: CreateAppRequest): Observable<App> {
    return this.http.post<App>(`${this.baseUrl}/me/apps`, request);
  }

  // ---- Catálogo ----

  templates(): Observable<Template[]> {
    return this.http.get<Template[]>(`${this.baseUrl}/templates`);
  }

  template(id: string): Observable<Template> {
    return this.http.get<Template>(`${this.baseUrl}/templates/${encodeURIComponent(id)}`);
  }

  // ---- Administrador ----

  listTenants(): Observable<Tenant[]> {
    return this.http.get<Tenant[]>(`${this.baseUrl}/tenants`);
  }

  getTenant(tenantId: string): Observable<Tenant> {
    return this.http.get<Tenant>(`${this.baseUrl}/tenants/${encodeURIComponent(tenantId)}`);
  }

  listApps(tenantId: string): Observable<App[]> {
    return this.http.get<App[]>(`${this.baseUrl}/tenants/${encodeURIComponent(tenantId)}/apps`);
  }

  // ---- Aplicaciones (propietario o administrador) ----

  getApp(appId: string): Observable<App> {
    return this.http.get<App>(`${this.baseUrl}/apps/${encodeURIComponent(appId)}`);
  }

  listReleases(appId: string): Observable<Release[]> {
    return this.http.get<Release[]>(`${this.baseUrl}/apps/${encodeURIComponent(appId)}/releases`);
  }
}

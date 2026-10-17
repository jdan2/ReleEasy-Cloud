import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { App, CreateAppRequest, CreateTenantRequest, Release, Tenant } from './models';

/**
 * Cliente HTTP de la API de la plataforma. La cabecera de autenticación la añade `authInterceptor`.
 */
@Injectable({ providedIn: 'root' })
export class ReleasyApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${inject(APP_CONFIG).apiUrl}/api`;

  listTenants(): Observable<Tenant[]> {
    return this.http.get<Tenant[]>(`${this.baseUrl}/tenants`);
  }

  getTenant(tenantId: string): Observable<Tenant> {
    return this.http.get<Tenant>(`${this.baseUrl}/tenants/${encodeURIComponent(tenantId)}`);
  }

  createTenant(request: CreateTenantRequest): Observable<Tenant> {
    return this.http.post<Tenant>(`${this.baseUrl}/tenants`, request);
  }

  listApps(tenantId: string): Observable<App[]> {
    return this.http.get<App[]>(`${this.baseUrl}/tenants/${encodeURIComponent(tenantId)}/apps`);
  }

  createApp(tenantId: string, request: CreateAppRequest): Observable<App> {
    return this.http.post<App>(`${this.baseUrl}/tenants/${encodeURIComponent(tenantId)}/apps`, request);
  }

  getApp(appId: string): Observable<App> {
    return this.http.get<App>(`${this.baseUrl}/apps/${encodeURIComponent(appId)}`);
  }

  listReleases(appId: string): Observable<Release[]> {
    return this.http.get<Release[]>(`${this.baseUrl}/apps/${encodeURIComponent(appId)}/releases`);
  }
}

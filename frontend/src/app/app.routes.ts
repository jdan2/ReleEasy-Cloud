import { inject } from '@angular/core';
import { Router, Routes } from '@angular/router';
import { guestGuard, roleGuard } from './core/auth/auth.guard';
import { AuthService } from './core/auth/auth.service';

/**
 * Rutas:
 * - `/login`, `/register`: acceso (sin sesión);
 * - `/portal/**`: vista del cliente;
 * - `/admin/**`: vista del administrador.
 */
export const routes: Routes = [
  {
    path: 'login',
    title: 'Acceso · ReleEasy Cloud',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    title: 'Crear cuenta · ReleEasy Cloud',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register.page').then((m) => m.RegisterPage),
  },
  {
    path: 'portal',
    canActivate: [roleGuard('CLIENT')],
    children: [
      {
        path: '',
        title: 'Inicio · ReleEasy Cloud',
        loadComponent: () => import('./features/portal/portal-home.page').then((m) => m.PortalHomePage),
      },
      {
        path: 'aws',
        title: 'Cuenta AWS · ReleEasy Cloud',
        loadComponent: () => import('./features/portal/aws-connection.page').then((m) => m.AwsConnectionPage),
      },
      {
        path: 'github',
        title: 'GitHub · ReleEasy Cloud',
        loadComponent: () => import('./features/portal/github-connection.page').then((m) => m.GithubConnectionPage),
      },
      {
        path: 'templates',
        title: 'Plantillas · ReleEasy Cloud',
        loadComponent: () => import('./features/portal/template-catalog.page').then((m) => m.TemplateCatalogPage),
      },
      {
        path: 'templates/:templateId/new',
        title: 'Nueva aplicación · ReleEasy Cloud',
        loadComponent: () => import('./features/portal/new-app.page').then((m) => m.NewAppPage),
      },
      {
        path: 'apps/:appId',
        title: 'Aplicación · ReleEasy Cloud',
        loadComponent: () => import('./features/apps/app-detail.page').then((m) => m.AppDetailPage),
      },
    ],
  },
  {
    path: 'admin',
    canActivate: [roleGuard('ADMIN')],
    children: [
      {
        path: '',
        title: 'Clientes · ReleEasy Cloud',
        loadComponent: () => import('./features/admin/admin-tenants.page').then((m) => m.AdminTenantsPage),
      },
      {
        path: 'tenants/:tenantId',
        title: 'Cliente · ReleEasy Cloud',
        loadComponent: () => import('./features/admin/admin-tenant-detail.page').then((m) => m.AdminTenantDetailPage),
      },
      {
        path: 'apps/:appId',
        title: 'Aplicación · ReleEasy Cloud',
        loadComponent: () => import('./features/apps/app-detail.page').then((m) => m.AppDetailPage),
      },
    ],
  },
  // Raíz: cada rol a su inicio; sin sesión, al login.
  {
    path: '',
    pathMatch: 'full',
    redirectTo: () => {
      const auth = inject(AuthService);
      return inject(Router).parseUrl(auth.isAuthenticated() ? auth.homeUrl() : '/login');
    },
  },
  { path: '**', redirectTo: '' },
];

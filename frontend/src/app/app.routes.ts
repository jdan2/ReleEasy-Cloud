import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Acceso · ReleEasy Cloud',
    loadComponent: () => import('./features/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tenants' },
      {
        path: 'tenants',
        title: 'Clientes · ReleEasy Cloud',
        loadComponent: () => import('./features/tenants/tenant-list.page').then((m) => m.TenantListPage),
      },
      {
        path: 'tenants/:tenantId',
        title: 'Cliente · ReleEasy Cloud',
        loadComponent: () => import('./features/tenants/tenant-detail.page').then((m) => m.TenantDetailPage),
      },
      {
        path: 'apps/:appId',
        title: 'Aplicación · ReleEasy Cloud',
        loadComponent: () => import('./features/apps/app-detail.page').then((m) => m.AppDetailPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];

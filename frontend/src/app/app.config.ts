import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { APP_CONFIG, AppConfig } from './core/config/app-config';
import { mockApiInterceptor } from './core/mock/mock-api.interceptor';

/** Proveedores de la aplicación. Recibe la configuración ya leída de `config.json`. */
export function createAppConfig(config: AppConfig): ApplicationConfig {
  return {
    providers: [
      provideBrowserGlobalErrorListeners(),
      { provide: APP_CONFIG, useValue: config },
      // El interceptor simulado va el último: recibe la petición ya con la cabecera de autenticación.
      provideHttpClient(withInterceptors([authInterceptor, mockApiInterceptor])),
      // withComponentInputBinding: los parámetros de ruta (:tenantId, :appId) llegan como inputs.
      provideRouter(routes, withComponentInputBinding()),
    ],
  };
}

import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { AuthService } from './auth.service';

/**
 * Añade `Authorization: Bearer <token>` a las peticiones dirigidas a la API (y solo a esas). Si la API
 * responde 401 a una petición con sesión, cierra la sesión y vuelve al login.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const apiUrl = inject(APP_CONFIG).apiUrl;

  const header = auth.authorizationHeader();
  const outgoing =
    request.url.startsWith(`${apiUrl}/`) && header && !request.headers.has('Authorization')
      ? request.clone({ setHeaders: { Authorization: header } })
      : request;

  return next(outgoing).pipe(
    catchError((error: unknown) => {
      if (outgoing !== request && error instanceof HttpErrorResponse && error.status === 401) {
        auth.logout();
        void router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};

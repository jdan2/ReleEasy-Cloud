import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, map, tap, throwError } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';

const STORAGE_KEY = 'releasy.auth';

/**
 * Sesión del administrador (MVP: HTTP Basic).
 *
 * Las credenciales se guardan codificadas en `sessionStorage`: duran lo que la pestaña y no se
 * comparten con otras. Es suficiente para el MVP; la autenticación real (p. ej. Cognito) queda
 * fuera de alcance.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(APP_CONFIG).apiUrl;
  private readonly token = signal<string | null>(readStoredToken());

  readonly isAuthenticated = computed(() => this.token() !== null);

  /** Valor de la cabecera `Authorization`, o `null` si no hay sesión. */
  authorizationHeader(): string | null {
    const token = this.token();
    return token ? `Basic ${token}` : null;
  }

  /**
   * Comprueba las credenciales con una petición real a la API y, si son válidas, abre la sesión.
   * Emite `false` si la API responde 401.
   */
  login(username: string, password: string): Observable<boolean> {
    const candidate = encodeBasic(username, password);
    return this.http
      .get<unknown>(`${this.apiUrl}/api/tenants`, { headers: { Authorization: `Basic ${candidate}` } })
      .pipe(
        map(() => true),
        tap(() => this.store(candidate)),
        catchError((error: { status?: number }) => (error.status === 401 ? [false] : throwError(() => error))),
      );
  }

  logout(): void {
    this.store(null);
  }

  private store(token: string | null): void {
    this.token.set(token);
    try {
      if (token) {
        sessionStorage.setItem(STORAGE_KEY, token);
      } else {
        sessionStorage.removeItem(STORAGE_KEY);
      }
    } catch {
      // sessionStorage no disponible (modo privado estricto): la sesión vive solo en memoria.
    }
  }
}

function readStoredToken(): string | null {
  try {
    return sessionStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

/** Codifica `usuario:contraseña` en Base64, admitiendo caracteres no ASCII (UTF-8). */
export function encodeBasic(username: string, password: string): string {
  const bytes = new TextEncoder().encode(`${username}:${password}`);
  return btoa(String.fromCharCode(...bytes));
}

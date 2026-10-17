import { Injectable, computed, signal } from '@angular/core';
import { Role, Session, User } from '../api/models';

const STORAGE_KEY = 'releasy.session';

/**
 * Sesión del usuario (administrador o cliente).
 *
 * Guarda el token y los datos del usuario en `sessionStorage`: duran lo que la pestaña. Las
 * peticiones de login y registro las hacen las pantallas con `ReleasyApi` y luego llaman a
 * {@link start}.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly session = signal<Session | null>(readStoredSession());

  readonly user = computed<User | null>(() => this.session()?.user ?? null);
  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly role = computed<Role | null>(() => this.user()?.role ?? null);

  /** Valor de la cabecera `Authorization`, o `null` si no hay sesión. */
  authorizationHeader(): string | null {
    const token = this.session()?.token;
    return token ? `Bearer ${token}` : null;
  }

  /** Ruta de inicio según el rol. */
  homeUrl(): string {
    return this.role() === 'ADMIN' ? '/admin' : '/portal';
  }

  start(session: Session): void {
    this.store(session);
  }

  logout(): void {
    this.store(null);
  }

  private store(session: Session | null): void {
    this.session.set(session);
    try {
      if (session) {
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
      } else {
        sessionStorage.removeItem(STORAGE_KEY);
      }
    } catch {
      // sessionStorage no disponible: la sesión vive solo en memoria.
    }
  }
}

function readStoredSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

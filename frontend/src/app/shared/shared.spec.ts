import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { aRelease } from '../../testing/test-data';
import { describeHttpError } from './http-error';
import { Poller } from './poller';
import {
  ACTIVE_POLL_MS,
  IDLE_POLL_MS,
  hasReleaseInProgress,
  releasePollInterval,
} from './polling-policy';
import { StatusBadge } from './status-badge';

describe('Política de polling', () => {
  it('considera en curso los releases PENDING y RUNNING', () => {
    expect(hasReleaseInProgress([aRelease('PENDING')])).toBe(true);
    expect(hasReleaseInProgress([aRelease('SUCCEEDED'), aRelease('RUNNING')])).toBe(true);
    expect(hasReleaseInProgress([aRelease('SUCCEEDED'), aRelease('FAILED')])).toBe(false);
    expect(hasReleaseInProgress([])).toBe(false);
  });

  it('refresca rápido con un release en curso y despacio en reposo', () => {
    expect(releasePollInterval([aRelease('RUNNING')])).toBe(ACTIVE_POLL_MS);
    expect(releasePollInterval([aRelease('SUCCEEDED')])).toBe(IDLE_POLL_MS);
    expect(ACTIVE_POLL_MS).toBeLessThan(IDLE_POLL_MS);
  });
});

describe('Poller', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  function createPoller(task: () => void): { poller: Poller; destroy: () => void } {
    const callbacks: (() => void)[] = [];
    const destroyRef = { onDestroy: (cb: () => void) => callbacks.push(cb) } as unknown as DestroyRef;
    return { poller: new Poller(task, destroyRef), destroy: () => callbacks.forEach((cb) => cb()) };
  }

  it('ejecuta la tarea una vez pasado el retardo, sin solapar programaciones', () => {
    const task = vi.fn();
    const { poller } = createPoller(task);

    poller.schedule(1000);
    poller.schedule(1000);
    vi.advanceTimersByTime(999);
    expect(task).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(task).toHaveBeenCalledTimes(1);
  });

  it('deja de ejecutar al destruirse el componente', () => {
    const task = vi.fn();
    const { poller, destroy } = createPoller(task);

    poller.schedule(1000);
    destroy();
    poller.schedule(1000);
    vi.advanceTimersByTime(5000);
    expect(task).not.toHaveBeenCalled();
  });
});

describe('describeHttpError', () => {
  it('usa el campo detail de Problem Details', () => {
    const error = new HttpErrorResponse({ status: 409, error: { detail: 'La cuenta AWS no está activa' } });
    expect(describeHttpError(error)).toBe('La cuenta AWS no está activa');
  });

  it('explica cuando la API no responde', () => {
    expect(describeHttpError(new HttpErrorResponse({ status: 0 }))).toContain('No se pudo conectar');
  });

  it('da un mensaje genérico en otros casos', () => {
    expect(describeHttpError(new HttpErrorResponse({ status: 500 }))).toBe('Error 500 al llamar a la API');
    expect(describeHttpError(new Error('x'))).toBe('Error inesperado');
  });
});

describe('StatusBadge', () => {
  it('muestra el código del estado con su descripción', async () => {
    const fixture = TestBed.createComponent(StatusBadge);
    fixture.componentRef.setInput('status', 'RUNNING');
    await fixture.whenStable();

    const badge = (fixture.nativeElement as HTMLElement).querySelector('span')!;
    expect(badge.textContent?.trim()).toBe('RUNNING');
    expect(badge.title).toBe('Desplegando');
    expect(badge.className).toContain('bg-sky-50');
  });
});

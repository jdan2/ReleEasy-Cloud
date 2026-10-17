import { DestroyRef } from '@angular/core';

/**
 * Programa una tarea con `setTimeout`, de una en una (nunca se solapan), y la cancela al destruirse
 * el componente. La pantalla decide en cada vuelta cuánto esperar hasta la siguiente.
 */
export class Poller {
  private handle: ReturnType<typeof setTimeout> | null = null;
  private stopped = false;

  constructor(
    private readonly task: () => void,
    destroyRef: DestroyRef,
  ) {
    destroyRef.onDestroy(() => this.stop());
  }

  /** Ejecuta la tarea dentro de `delayMs` (cancela una ejecución pendiente). */
  schedule(delayMs: number): void {
    this.cancel();
    if (!this.stopped) {
      this.handle = setTimeout(() => {
        this.handle = null;
        this.task();
      }, delayMs);
    }
  }

  stop(): void {
    this.stopped = true;
    this.cancel();
  }

  private cancel(): void {
    if (this.handle !== null) {
      clearTimeout(this.handle);
      this.handle = null;
    }
  }
}

import { HttpErrorResponse } from '@angular/common/http';

/**
 * Convierte un error de la API en un mensaje legible. La API responde con Problem Details
 * (RFC 9457), cuyo campo `detail` explica el motivo.
 */
export function describeHttpError(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return 'Error inesperado';
  }
  if (error.status === 0) {
    return 'No se pudo conectar con la API. ¿Está arrancada y es correcta la URL de config.json?';
  }
  const body = error.error as { detail?: unknown } | null;
  if (body && typeof body.detail === 'string' && body.detail.trim() !== '') {
    return body.detail;
  }
  return `Error ${error.status} al llamar a la API`;
}

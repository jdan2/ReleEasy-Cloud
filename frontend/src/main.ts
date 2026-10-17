import { bootstrapApplication } from '@angular/platform-browser';
import { App } from './app/app';
import { createAppConfig } from './app/app.config';
import { loadAppConfig } from './app/core/config/app-config';

/*
 * Primero se lee config.json (URL de la API) y después se arranca Angular con esa configuración.
 * Si falla, se muestra el motivo en la página en lugar de dejarla en blanco.
 */
loadAppConfig()
  .then((config) => bootstrapApplication(App, createAppConfig(config)))
  .catch((error: unknown) => {
    console.error(error);
    const message = error instanceof Error ? error.message : String(error);
    document.body.innerHTML = `
      <main style="font-family: system-ui, sans-serif; max-width: 40rem; margin: 4rem auto; padding: 0 1rem">
        <h1 style="font-size: 1.25rem">No se pudo iniciar el portal</h1>
        <p style="color: #be123c"></p>
      </main>`;
    document.querySelector('main p')!.textContent = message;
  });

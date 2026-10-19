# ReleEasy Cloud · Portal

Portal de administración de la plataforma, hecho con Angular 22 (standalone components, zoneless, TypeScript estricto) y Tailwind CSS 4.

## Arranque en local

```bash
nvm use            # Node 24.22.0 (.nvmrc)
npm install
npx ng serve       # http://localhost:4200
```

La API debe estar arrancada (ver `backend/docs`). La URL de la API se lee **en tiempo de ejecución** de [`public/config.json`](public/config.json):

```json
{ "apiUrl": "http://localhost:8080", "mockApi": false, "githubAppSlug": null, "githubClientId": null }
```

- `githubAppSlug` y `githubClientId`: *slug* y client ID de la GitHub App. Con ellos, "Instalar en GitHub" lleva a GitHub de verdad (instalación y autorización OAuth); con `null`, la instalación se simula. Ver `backend/docs/github-app.md`.
- Con la API en el perfil `mock-fast` existe el cliente `demo@ferreteria-lopez.es` / `demo1234`. El administrador es el de `backend/.env`.

Para otro entorno basta con sustituir `config.json` en el servidor, sin recompilar.

Si cambias el catálogo de plantillas (`src/app/core/templates/template-catalog.ts`), vuelve a exportarlo para la API con `npm run export:catalog`.

## Pruebas

```bash
npx ng test --watch=false   # Vitest
npx ng build                # build de producción en dist/frontend/browser
```

## Modo demo

Con `"mockApi": true` en `public/config.json` el portal funciona sin backend: `src/app/core/mock/` simula la API en el navegador y guarda los datos en `localStorage`. Usuarios: cliente `demo@ferreteria-lopez.es` / `demo1234` y administrador `admin` / `admin`.

## Estructura

```
src/app/
  core/
    config/      config.json en tiempo de ejecución (APP_CONFIG)
    api/         contrato con la API: modelos y cliente HTTP (ReleasyApi)
    auth/        sesión (token Bearer), interceptor y guards por rol
    templates/   catálogo declarativo de plantillas y sus opciones
    pricing/     precios de referencia de AWS y estimador de costes
    mock/        backend simulado del modo demo
  shared/        badge de estado, campo de opción, desglose de costes, polling
  features/
    auth/        login y registro
    portal/      vista del cliente: inicio, conexión AWS y GitHub, catálogo, alta de app
    admin/       vista del administrador: clientes y sus apps
    apps/        detalle de aplicación (común) con releases y polling
src/styles.css   Tailwind y clases de componente (.card, .btn-primary, ...)
src/testing/     datos de prueba compartidos por los specs
```

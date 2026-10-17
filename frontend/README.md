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
{ "apiUrl": "http://localhost:8080" }
```

Para otro entorno basta con sustituir `config.json` en el servidor, sin recompilar.

## Pruebas

```bash
npx ng test --watch=false   # Vitest
npx ng build                # build de producción en dist/frontend/browser
```

## Estructura

```
src/app/
  core/
    config/     config.json en tiempo de ejecución (APP_CONFIG)
    api/        modelos y cliente HTTP de la API (ReleasyApi)
    auth/       sesión HTTP Basic, interceptor y guard
  shared/       badge de estado, polling, mensajes de error
  features/
    login/      acceso del administrador
    tenants/    lista y alta de clientes; detalle con sus aplicaciones
    apps/       detalle de aplicación e historial de releases (con polling)
src/styles.css  Tailwind y clases de componente (.card, .btn-primary, ...)
src/testing/    datos de prueba compartidos por los specs
```

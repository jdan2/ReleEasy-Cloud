# ReleEasy Cloud: dónde se guardan los usuarios y la sesión

Estado a 2026-11-10. Explica qué pasa al registrarse o iniciar sesión y dónde se guarda cada dato.

## 1. Usuarios y datos (backend)

El repositorio de usuarios es un puerto (`UserRepository`) con dos implementaciones. Cuál se usa depende de `RELEASY_AWS_MODE`:

| Modo | Dónde se guardan | Clase |
|---|---|---|
| `mock` (el de desarrollo local) | `ConcurrentHashMap` en memoria del proceso; **se pierden al reiniciar la API** | `backend/src/main/java/com/releasy/infrastructure/adapters/memory/InMemoryUserRepository.java` |
| `real` | Tabla DynamoDB `releasy-users`, con el email como clave (la crea `backend/deploy/platform`) | `backend/src/main/java/com/releasy/infrastructure/adapters/dynamodb/DynamoDbUserRepository.java` |

- La contraseña se guarda como hash **BCrypt**, nunca en claro.
- Tenants, apps y releases funcionan igual: en memoria (`infrastructure/adapters/memory/`) en modo `mock` y en DynamoDB (`infrastructure/adapters/dynamodb/`, tablas `releasy-tenants`, `releasy-apps` y `releasy-releases`) en modo `real`.
- El usuario demo `demo@ferreteria-lopez.es` / `demo1234` existe porque el perfil `mock-fast` lo crea en cada arranque. **Ojo:** si se arranca en modo `real` con ese perfil (como hace F5), el usuario demo se crea en la tabla real de DynamoDB.
- En modo `real`, la API usa las credenciales de la cadena por defecto del SDK (`AWS_PROFILE` en local), que deben ser de la cuenta de la plataforma.
- El **administrador** no se guarda en ningún sitio: sale de `RELEASY_ADMIN_USER` y `RELEASY_ADMIN_PASSWORD` en `backend/.env`.

## 2. Sesión (JWT, sin estado en el servidor)

En el servidor no se guarda ninguna sesión (D-24):

1. Al hacer login o registrarse, la API firma un **JWT HS256** con `RELEASY_JWT_SECRET`. Dura 12 horas (`RELEASY_TOKEN_TTL`, en `backend/src/main/resources/application.yml`).
2. El portal guarda el token y los datos del usuario en el **`sessionStorage`** del navegador, con la clave `releasy.session` (`frontend/src/app/core/auth/auth.service.ts`). Dura lo que la pestaña: si se cierra, hay que volver a entrar.
3. En cada petición, el portal envía `Authorization: Bearer <token>`. La API valida la firma y la caducidad sin consultar ninguna base de datos.

El `state` del flujo OAuth de GitHub también va en el `sessionStorage` (`frontend/src/app/features/portal/github-flow.ts`) y se borra al volver del callback.

## 3. Consecuencias prácticas en local

- Si se reinicia la API en modo `mock`, el token sigue siendo válido mientras no cambie `RELEASY_JWT_SECRET`. Sin embargo, el usuario, sus conexiones de AWS y GitHub y sus apps ya no existen, así que hay que registrarse y conectar de nuevo.
- Para conservar los datos sin usar AWS real, una opción es levantar **DynamoDB Local** en Docker y usar los repositorios DynamoDB en local. Está pendiente de decidir.
- En modo `real` los datos persisten en DynamoDB entre reinicios. La plataforma se desplegó en una cuenta de pruebas del compañero (`us-east-1`) para la prueba del 2026-11-07/10 (D-39); para usarla hace falta un perfil de la CLI con acceso a esa cuenta, o desplegar una plataforma propia con `backend/deploy/platform` (runbook en `backend/deploy/README.md`).

## 4. Configuración de la GitHub App de desarrollo (2026-10-24)

- App: `releeasy-cloud-dev` (App ID 5457976), con permisos `contents: read` y `metadata: read`.
- Callback URL: `http://localhost:4200/portal/github/callback`.
- Clave privada: **fuera del repo**, en `~/.releasy/github-app.pem` (permisos 600).
- Secretos (`RELEASY_GITHUB_*`) en `backend/.env`, que no se versiona. El slug y el Client ID, que son públicos, están en `frontend/public/config.json`.
- Webhook: todavía desactivado; necesita una URL pública (p. ej. smee.io).

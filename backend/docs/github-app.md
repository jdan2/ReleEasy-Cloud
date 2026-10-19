# GitHub App de ReleEasy

## Qué es y quién la crea

La GitHub App es la identidad de ReleEasy ante GitHub. **La crea una sola vez el dueño de la plataforma** (este documento). Los clientes **no crean nada**: en el portal pulsan "Instalar en GitHub", eligen los repositorios y autorizan a ReleEasy con su usuario, igual que al conectar GitHub en Vercel o Netlify.

Después, cada cliente ve ReleEasy en su GitHub, en **Settings → Applications**:

- **Installed GitHub Apps**: la instalación, con los repositorios compartidos. Desde ahí puede cambiar los repositorios o desinstalarla.
- **Authorized GitHub Apps**: la autorización de su usuario. Desde ahí puede revocarla.

Con la API en `RELEASY_GITHUB_MODE=real`, el portal lee los repositorios y las ramas de verdad. La cuenta AWS y los despliegues siguen simulados (`RELEASY_AWS_MODE=mock`). Ver D-25 y D-26 en `decisiones.md`.

## 1. Crear la app en GitHub

1. En GitHub, ve a **Settings → Developer settings → GitHub Apps → New GitHub App**. Para crearla en una organización: **Organization settings → Developer settings → GitHub Apps**.
2. Rellena:

   | Campo | Valor |
   |---|---|
   | GitHub App name | `ReleEasy Cloud (dev)` u otro nombre libre; de él sale el *slug* |
   | Homepage URL | `http://localhost:4200` |
   | **Callback URL** | `http://localhost:4200/portal/github/callback` |
   | Expire user authorization tokens | ✅ (por defecto) |
   | **Request user authorization (OAuth) during installation** | ✅ **marcado** |
   | Enable Device Flow | ❌ |
   | Setup URL | GitHub la desactiva al marcar la opción anterior; se usa la *Callback URL* |
   | Redirect on update | ✅ marcado |
   | Webhook → Active | ❌ **desmarcado** por ahora. Los push necesitan una URL pública; se activará más adelante. |

3. **Permisos** (*Repository permissions*); el resto, en *No access*:
   - **Contents: Read-only**: ramas y commits.
   - **Metadata: Read-only**: obligatorio; GitHub lo marca solo.
4. **Where can this GitHub App be installed?** *Any account*, para que puedan instalarla los clientes (otras cuentas y organizaciones).
5. Pulsa **Create GitHub App**.

## 2. Datos que necesitas

En la página de la app recién creada (*General*):

| Dato | Dónde | Para qué |
|---|---|---|
| **App ID** | Arriba, en *About* | La API firma con él el JWT de la app |
| **Client ID** | Debajo del App ID | La API canjea los códigos OAuth; el portal lo usa para "Autoriza tu usuario" |
| **Client secret** | *Client secrets → Generate a new client secret*. **Solo se muestra una vez.** | La API canjea los códigos OAuth |
| **Slug** | La última parte de la *Public link*, `https://github.com/apps/<slug>` | El portal lleva al cliente a instalar la app |
| **Clave privada** | *Private keys → Generate a private key*; se descarga un `.pem` | La API firma el JWT de la app |

Guarda la clave **fuera del repositorio**:

```bash
mkdir -p ~/.releasy && mv ~/Downloads/<nombre>.*.private-key.pem ~/.releasy/github-app.pem && chmod 600 ~/.releasy/github-app.pem
```

> Nunca subas el `.pem` ni el client secret a git (`.gitignore` ya excluye `*.pem` y `.env`). En AWS irán a Secrets Manager.

## 3. Configurar la API

En `backend/.env` (no se versiona):

```bash
RELEASY_GITHUB_MODE=real
RELEASY_GITHUB_APP_ID=<App ID>
RELEASY_GITHUB_PRIVATE_KEY_PATH=/Users/<tu-usuario>/.releasy/github-app.pem
RELEASY_GITHUB_CLIENT_ID=<Client ID>
RELEASY_GITHUB_CLIENT_SECRET=<Client secret>
```

La API acepta el `.pem` tal como lo da GitHub (PKCS#1); no hace falta convertirlo.

## 4. Configurar el portal

En `frontend/public/config.json` (el slug y el client ID son públicos; el secret no va aquí):

```json
{
  "apiUrl": "http://localhost:8080",
  "mockApi": false,
  "githubAppSlug": "<slug>",
  "githubClientId": "<Client ID>"
}
```

## 5. Probar

1. Arranca la API (F5 "ReleEasy API (mock)", o `SPRING_PROFILES_ACTIVE=mock-fast ./mvnw spring-boot:run` con el `.env` cargado). En el log debe aparecer `releasy.github.mode=real: GitHub App <id>`.
2. Arranca el portal (`npx ng serve`) y entra con `demo@ferreteria-lopez.es` / `demo1234`, que el perfil `mock-fast` crea al arrancar.
3. Ve a **Conecta GitHub → Instalar en GitHub**, elige los repositorios, pulsa **Install & Authorize** y autoriza.
4. GitHub vuelve a `/portal/github/callback` y el portal muestra:
   - la cuenta de la instalación;
   - "autorizada por @tu-usuario";
   - tus repositorios con sus ramas.
5. En GitHub, **Settings → Applications**, ReleEasy aparece en las dos pestañas.

Si la app ya estaba instalada en tu cuenta, usa **"¿Ya la tenías instalada? Autoriza tu usuario de GitHub"**.

## Cómo funciona por dentro

1. El portal genera un `state` aleatorio, lo guarda en la pestaña y lleva al cliente a `github.com/apps/<slug>/installations/new?state=…`.
2. El cliente instala y autoriza. GitHub vuelve a la *Callback URL* con `code`, `installation_id` y `state`.
3. El portal comprueba que el `state` coincide (protección CSRF) y envía `POST /api/me/github-connection {code, installationId}`.
4. La API:
   - canjea el `code` en `github.com/login/oauth/access_token` con el client ID y el secret;
   - con ese token del usuario pide `GET /user` y `GET /user/installations`;
   - comprueba que la instalación está entre las del usuario;
   - guarda la instalación y quién la autorizó.

   **El token del usuario no se guarda.**
5. Para leer repositorios, la API usa el JWT de la app (firmado con la clave privada) y tokens de instalación de 1 hora.

## Problemas frecuentes

| Síntoma | Causa |
|---|---|
| La API no arranca: "obligatorio releasy.github.…" | Falta alguna variable `RELEASY_GITHUB_*` con `RELEASY_GITHUB_MODE=real`. |
| "La autorización de GitHub no es válida o ha caducado" | Client ID o secret incorrectos, o el código ya se usó o pasaron más de 10 minutos. Vuelve a conectar. |
| "No corresponde a una conexión iniciada desde esta pestaña" | Se volvió de GitHub en otra pestaña o se recargó la página de vuelta. Vuelve a conectar. |
| "Tu usuario de GitHub (@…) no tiene acceso a la instalación" | El usuario que autorizó no tiene acceso a esa cuenta u organización. |
| 502 "GitHub respondió 401" | App ID que no corresponde a la clave, clave revocada o reloj del equipo muy desajustado. |
| Tras instalar, GitHub no vuelve al portal | La *Callback URL* no es `http://localhost:4200/portal/github/callback`, o no se marcó *Request user authorization during installation*. |
| Faltan repositorios | Al instalar se eligieron solo algunos. Se cambia en GitHub: *Settings → Applications → Configure*. |

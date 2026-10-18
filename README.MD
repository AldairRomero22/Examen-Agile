# Castillo · Control de pagos (Sprint 1)

Backend en **Java 21 + Spring Boot**, frontend en **HTML, CSS y JavaScript** (carpeta `static/`),
base de datos **PostgreSQL en Neon** y despliegue en **Render** con Docker.

## Estructura

```
src/main/java/pe/castillo/
├─ CastilloApplication.java     arranque (zona horaria America/Lima)
├─ config/                      seguridad, CSRF, redirección de "/"
└─ usuario/                     HU-01: usuarios, roles, cuentas iniciales, /api/me
src/main/resources/
├─ application.properties
├─ db/migration/                tablas (Flyway las crea solo al arrancar)
│  ├─ V1__tablas.sql            usuario, categoria, beneficiario, obligacion, comprobante, verificacion
│  └─ V2__categorias.sql        las 4 categorías fijas
└─ static/                      pantallas: login.html, panel.html, css/, js/
```

Paquetes que faltan crear, uno por grupo de HU:

| Paquete | HU |
|---|---|
| `beneficiario/` | HU-02 |
| `obligacion/` | HU-03, HU-05, HU-06, HU-10 |
| `panel/` | HU-04 |
| `lector/` | HU-07 (lector intercambiable: IA de visión o Tesseract) |
| `comprobante/` | HU-07, HU-08, HU-09 |

## Ejecutar en tu PC (VS Code)

1. Instala JDK 21 y las extensiones **Extension Pack for Java** y **Spring Boot Extension Pack**.
2. Abre `.vscode/launch.json` y reemplaza:
   - `PEGA_AQUI_LA_CLAVE_DE_NEON` por la clave de Neon.
   - Las dos claves de las cuentas (mínimo 8 caracteres).
3. Pestaña **Run and Debug** → **Castillo (local)** → ▶.
4. Abre http://localhost:8080 e ingresa con `asistente@castillo.pe` o `gerente@castillo.pe`.

`.vscode/` está en `.gitignore`: las claves **no** se suben a GitHub.

## Variables de entorno (Render → Environment)

| Variable | Valor |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<host-de-neon>/castillo?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | `neondb_owner` |
| `SPRING_DATASOURCE_PASSWORD` | clave de Neon |
| `CASTILLO_ASISTENTE_CLAVE` | clave inicial del asistente |
| `CASTILLO_GERENTE_CLAVE` | clave inicial del gerente |
| `SERVER_SERVLET_SESSION_COOKIE_SECURE` | `true` |

Las cuentas se crean solo la primera vez. Cambiar después la variable no cambia la clave.

## Desplegar en Render

New → Web Service → conectar este repositorio → Runtime **Docker** → región **Ohio** (la misma de Neon)
→ plan Free → cargar las variables → Create.

## Reglas que ya impone la base de datos

- Nada se borra: columna `activo` en todas las tablas.
- Documento del beneficiario: 8 u 11 dígitos y único.
- Monto mayor que 0 con 2 decimales.
- Un dato leído del voucher no se puede cambiar; solo se escribe a mano un campo que el lector dejó vacío.
- Un nº de operación o un archivo aprobado no puede usarse en otro pago.
- Un pago Pagado no puede estar En espera.

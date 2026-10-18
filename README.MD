# Castillo · Control de pagos (Sprint 1)

Backend en **Java 21 + Spring Boot**, frontend en **HTML, CSS y JavaScript** (carpeta `static/`),
base de datos **PostgreSQL en Neon** y despliegue en **Render** con Docker.

## Estructura

```
src/main/java/pe/castillo/
├─ CastilloApplication.java     arranque (zona horaria America/Lima)
├─ config/                      seguridad, CSRF, errores en JSON, fecha de "hoy" en Lima
├─ usuario/                     HU-01: usuarios, roles, cuentas iniciales, /api/me
├─ categoria/                   las 4 categorías fijas
├─ beneficiario/                HU-02
├─ obligacion/                  HU-03 a HU-06 y HU-10 (en espera)
├─ lector/                      HU-07: lee el voucher (Tesseract; la IA se agrega después)
└─ comprobante/                 HU-07/08/09: subir, verificar con 4 reglas, ver
src/main/resources/
├─ application.properties
├─ db/migration/                tablas (Flyway las crea solo al arrancar)
└─ static/                      pantallas: login, panel, beneficiarios, beneficiario,
                                beneficiario-nuevo, obligacion-nueva, pago, comprobante-subir,
                                comprobante-ver, css/, js/
```

Avance por HU:

| HU | Estado |
|---|---|
| HU-01 Iniciar sesión según mi rol | hecha |
| HU-02 Gestionar beneficiarios | hecha |
| HU-03 Registrar obligación | hecha |
| HU-04 Panel y pagos del mes | hecha (Panel = resumen y alertas; menú Pagos = listado con búsqueda y filtros) |
| HU-05 Alertas de vencimiento | hecha |
| HU-06 Detalle del pago | hecha (`pago.html`) |
| HU-07 Subir voucher y leerlo | hecha con Tesseract (`comprobante-subir.html`) |
| HU-08 Verificar con 4 reglas | hecha (Pagado u Observado) |
| HU-09 Ver comprobante | hecha (`comprobante-ver.html`) |
| HU-10 Poner en espera | hecha (botones en `pago.html`) |

## Ejecutar en tu PC (VS Code)

1. Instala JDK 21 y las extensiones **Extension Pack for Java** y **Spring Boot Extension Pack**.
2. Abre `.vscode/launch.json` y reemplaza:
   - `PEGA_AQUI_LA_CLAVE_DE_NEON` por la clave de Neon.
   - Las dos claves de las cuentas (mínimo 8 caracteres).
3. Pestaña **Run and Debug** → **Castillo (local)** → ▶.
4. Abre http://localhost:8080 e ingresa con `asistente@castillo.pe` o `gerente@castillo.pe`.

`.vscode/` está en `.gitignore`: las claves **no** se suben a GitHub.

## Lector de vouchers (Tesseract)

El voucher se lee con **Tesseract** (OCR gratuito, sin IA). Los PDF de bancos se leen directo del texto del PDF.

- **Render**: el `Dockerfile` ya instala Tesseract con español. No hay que hacer nada.
- **Tu PC (Windows)**: instala Tesseract (instalador de UB Mannheim, marca **Spanish** en idiomas).
  Si `tesseract --version` no funciona en la terminal, define la variable `TESSERACT_CMD` con la ruta
  del programa, por ejemplo `C:\Program Files\Tesseract-OCR\tesseract.exe` (sin comillas).
- Si falta el idioma español, usa solo inglés (los números y fechas se leen igual).
- En Render gratis la lectura es lenta (0.1 CPU): puede tardar decenas de segundos. Se puede bajar la
  memoria con `JAVA_OPTS=-Xmx256m`.
- El lector es intercambiable (`LECTOR_VOUCHER`, hoy solo `tesseract`): después se agrega el de IA.

Opcionales: `TESSERACT_CMD`, `TESSERACT_IDIOMAS` (por defecto `spa+eng`), `TESSERACT_LIMITE_SEGUNDOS` (90).

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

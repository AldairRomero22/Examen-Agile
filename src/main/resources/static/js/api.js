// Funciones comunes para todas las pantallas.

/** Lee el token CSRF que Spring deja en la cookie XSRF-TOKEN. */
function tokenCsrf() {
  const fila = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='));
  return fila ? decodeURIComponent(fila.split('=')[1]) : '';
}

/**
 * fetch con sesión y CSRF. Si no hay sesión (401) vuelve al login.
 * Uso: const r = await api('/api/obligaciones', { method: 'POST', body: JSON.stringify(datos) });
 */
async function api(url, opciones = {}) {
  const metodo = (opciones.method || 'GET').toUpperCase();
  const headers = { ...(opciones.headers || {}) };
  if (metodo !== 'GET') {
    headers['X-XSRF-TOKEN'] = tokenCsrf();
    if (opciones.body && !(opciones.body instanceof FormData) && !headers['Content-Type']) {
      headers['Content-Type'] = 'application/json';
    }
  }
  const respuesta = await fetch(url, { credentials: 'same-origin', ...opciones, method: metodo, headers });
  if (respuesta.status === 401) {
    window.location.href = '/login.html';
    throw new Error('Sin sesión');
  }
  return respuesta;
}

/** Usuario que inició sesión: { correo, nombre, rol } */
async function usuarioActual() {
  const r = await api('/api/me');
  return r.json();
}

/** HU-01 criterio 6: cierra la sesión y vuelve al login. */
function cerrarSesion() {
  const form = document.createElement('form');
  form.method = 'post';
  form.action = '/logout';
  const csrf = document.createElement('input');
  csrf.type = 'hidden';
  csrf.name = '_csrf';
  csrf.value = tokenCsrf();
  form.appendChild(csrf);
  document.body.appendChild(form);
  form.submit();
}

// ---------- utilidades de pantalla ----------

/** Escapa texto antes de ponerlo dentro de innerHTML (evita inyección de HTML). */
function esc(texto) {
  return String(texto ?? '').replace(/[&<>"']/g, c => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

/** 1280 -> "S/ 1,280.00" */
function formatearMonto(valor) {
  return 'S/ ' + Number(valor).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/** "2026-10-20" -> "20/10/2026" (sin pasar por Date, para no desfasar el día por zona horaria) */
function formatearFecha(iso) {
  const [a, m, d] = String(iso).split('-');
  return d + '/' + m + '/' + a;
}

/** Fecha de hoy como "AAAA-MM-DD" con la hora local del navegador. */
function hoyIso() {
  const h = new Date();
  return h.getFullYear() + '-' + String(h.getMonth() + 1).padStart(2, '0') + '-' + String(h.getDate()).padStart(2, '0');
}

/** Insignia de color del estado (HU-04 criterio 5: cada estado siempre del mismo color). */
function insigniaEstado(codigo, etiqueta) {
  return '<span class="estado estado-' + esc(String(codigo).toLowerCase().replace(/_/g, '-')) + '">' + esc(etiqueta) + '</span>';
}

/** Muestra u oculta el mensaje de error de un campo (<small id="error-ID">). */
function marcarError(id, mensaje) {
  const campo = document.getElementById(id);
  const error = document.getElementById('error-' + id);
  if (campo) campo.classList.toggle('campo-error', !!mensaje);
  if (error) {
    error.textContent = mensaje || '';
    error.hidden = !mensaje;
  }
}

/** Aviso general de la pantalla (<div id="aviso">). tipo: 'error' | 'ok' | 'info' */
function mostrarAviso(texto, tipo) {
  const aviso = document.getElementById('aviso');
  if (!aviso) return;
  if (!texto) {
    aviso.hidden = true;
    return;
  }
  aviso.textContent = texto;
  aviso.className = 'aviso aviso-' + (tipo || 'info');
  aviso.hidden = false;
}

/** HU-05 criterio 3: "vence en 2 días", "vence hoy", "vencido hace 5 días". */
function textoDias(dias) {
  const n = Math.abs(Number(dias));
  const d = n === 1 ? 'día' : 'días';
  if (dias < 0) return 'vencido hace ' + n + ' ' + d;
  if (dias === 0) return 'vence hoy';
  return 'vence en ' + n + ' ' + d;
}

/** Suma montos sin errores de decimales (trabaja en céntimos). */
function sumarMontos(lista) {
  return lista.reduce((total, o) => total + Math.round(Number(o.monto) * 100), 0) / 100;
}

/** "2026-10-17T23:40:12-05:00" -> "17/10/2026 23:40" en hora de Lima */
function formatearFechaHora(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('es-PE', {
    timeZone: 'America/Lima', day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit', hour12: false
  });
}

const NOMBRES_MES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'setiembre', 'octubre', 'noviembre', 'diciembre'];

/** "2026-10" -> "Octubre de 2026" */
function nombreMes(aaaaMm) {
  const [a, m] = aaaaMm.split('-').map(Number);
  const n = NOMBRES_MES[m - 1] || '';
  return n.charAt(0).toUpperCase() + n.slice(1) + ' de ' + a;
}

/**
 * Llena el selector de mes: "Todos los meses" y cada mes que tiene pagos (más el mes actual), en orden.
 * Devuelve el mes que quedó elegido ('' = todos).
 */
function llenarMeses(select, pagos, elegido) {
  const meses = new Set(pagos.map(o => String(o.fechaVencimiento).slice(0, 7)));
  meses.add(hoyIso().slice(0, 7));
  if (elegido) meses.add(elegido);
  select.innerHTML = '<option value="">Todos los meses</option>' +
    [...meses].sort().map(m => '<option value="' + m + '">' + nombreMes(m) + '</option>').join('');
  select.value = elegido || '';
  return select.value;
}

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

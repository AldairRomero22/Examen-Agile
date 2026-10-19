// HU-01 criterio 2: indica qué campo falta antes de enviar y muestra "Credenciales incorrectas".
const form = document.getElementById('form-login');
const aviso = document.getElementById('aviso');
const params = new URLSearchParams(window.location.search);

if (params.has('error')) {
  mostrarAviso('Credenciales incorrectas', 'aviso-error');
} else if (params.has('salir')) {
  mostrarAviso('Cerraste sesión', 'aviso-info');
}

// Mostrar u ocultar la contraseña
const botonVer = document.getElementById('ver-clave');
botonVer.addEventListener('click', () => {
  const campo = document.getElementById('clave');
  const mostrar = campo.type === 'password';
  campo.type = mostrar ? 'text' : 'password';
  botonVer.setAttribute('aria-pressed', String(mostrar));
  botonVer.setAttribute('aria-label', mostrar ? 'Ocultar contraseña' : 'Mostrar contraseña');
});

// Recordar mi correo: solo el correo, nunca la contraseña; se guarda en este navegador
const CLAVE_CORREO = 'castillo_correo';
try {
  const guardado = localStorage.getItem(CLAVE_CORREO);
  if (guardado) {
    document.getElementById('correo').value = guardado;
    document.getElementById('recordar').checked = true;
  }
} catch (e) { /* sin almacenamiento: no pasa nada */ }

form.addEventListener('submit', evento => {
  aviso.hidden = true;   // el aviso anterior (credenciales / cerraste sesión) ya no aplica
  document.getElementById('correo').value = document.getElementById('correo').value.trim();
  const faltaCorreo = marcarSiVacio('correo');
  const faltaClave = marcarSiVacio('clave');
  // Correo con formato válido (nombre@empresa.com) antes de enviar
  const malCorreo = !faltaCorreo && Reglas.correo(document.getElementById('correo').value);
  if (malCorreo) mostrarErrorCorreo(malCorreo);
  if (faltaCorreo || faltaClave || malCorreo) {
    evento.preventDefault();
    return;
  }
  try {
    if (document.getElementById('recordar').checked) localStorage.setItem(CLAVE_CORREO, document.getElementById('correo').value);
    else localStorage.removeItem(CLAVE_CORREO);
  } catch (e) { /* sin almacenamiento */ }
  document.getElementById('csrf').value = tokenCsrf();
  // Mientras Spring Security valida, el botón muestra que está ingresando y no se envía dos veces
  const boton = form.querySelector('.boton-entrar');
  boton.disabled = true;
  document.getElementById('texto-entrar').textContent = 'Ingresando…';
});

// Si el navegador vuelve a esta página desde el historial, el botón queda listo otra vez
window.addEventListener('pageshow', () => {
  form.querySelector('.boton-entrar').disabled = false;
  document.getElementById('texto-entrar').textContent = 'Ingresar';
});

function mostrarErrorCorreo(mensaje) {
  const error = document.getElementById('error-correo');
  error.textContent = mensaje;
  error.hidden = false;
  document.getElementById('correo').classList.add('campo-error');
}

function marcarSiVacio(id) {
  const campo = document.getElementById(id);
  const vacio = campo.value.trim() === '';
  if (id === 'correo') document.getElementById('error-correo').textContent = 'Ingresa tu correo';
  document.getElementById('error-' + id).hidden = !vacio;
  campo.classList.toggle('campo-error', vacio);
  return vacio;
}

function mostrarAviso(texto, clase) {
  aviso.textContent = texto;
  aviso.className = 'aviso ' + clase;
  aviso.hidden = false;
}

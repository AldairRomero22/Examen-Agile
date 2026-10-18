// HU-01 criterio 2: indica qué campo falta antes de enviar y muestra "Credenciales incorrectas".
const form = document.getElementById('form-login');
const aviso = document.getElementById('aviso');
const params = new URLSearchParams(window.location.search);

if (params.has('error')) {
  mostrarAviso('Credenciales incorrectas', 'aviso-error');
} else if (params.has('salir')) {
  mostrarAviso('Cerraste sesión', 'aviso-info');
}

form.addEventListener('submit', evento => {
  const faltaCorreo = marcarSiVacio('correo');
  const faltaClave = marcarSiVacio('clave');
  if (faltaCorreo || faltaClave) {
    evento.preventDefault();
    return;
  }
  document.getElementById('csrf').value = tokenCsrf();
});

function marcarSiVacio(id) {
  const campo = document.getElementById(id);
  const vacio = campo.value.trim() === '';
  document.getElementById('error-' + id).hidden = !vacio;
  campo.classList.toggle('campo-error', vacio);
  return vacio;
}

function mostrarAviso(texto, clase) {
  aviso.textContent = texto;
  aviso.className = 'aviso ' + clase;
  aviso.hidden = false;
}

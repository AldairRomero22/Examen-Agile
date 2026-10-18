// Barra superior común a todas las pantallas internas.
// Uso en cada página: <header id="barra"></header> ... const yo = await iniciarPagina('panel');
async function iniciarPagina(activa) {
  const yo = await usuarioActual();

  const barra = document.getElementById('barra');
  barra.className = 'barra';
  barra.innerHTML =
    '<span class="marca">Castillo</span>' +
    '<nav class="menu">' +
      '<a href="/panel.html" data-pagina="panel">Panel</a>' +
      '<a href="/beneficiarios.html" data-pagina="beneficiarios">Beneficiarios</a>' +
      '<a href="/obligacion-nueva.html" data-pagina="obligacion" class="solo-asistente">Nueva obligación</a>' +
    '</nav>' +
    '<div class="usuario">' +
      '<span id="usuario-correo"></span>' +
      '<span id="usuario-rol" class="insignia"></span>' +
      '<button type="button" class="boton boton-secundario" id="boton-salir">Cerrar sesión</button>' +
    '</div>';

  document.getElementById('usuario-correo').textContent = yo.correo;
  document.getElementById('usuario-rol').textContent = yo.rol === 'GERENTE' ? 'Gerente' : 'Asistente';
  document.getElementById('boton-salir').addEventListener('click', cerrarSesion);
  const enlace = barra.querySelector('[data-pagina="' + activa + '"]');
  if (enlace) enlace.classList.add('activo');

  // HU-01 criterio 3: el gerente solo consulta. Todo elemento con la clase "solo-asistente"
  // (crear, editar, subir comprobante) se quita para él. El servidor además rechaza esas acciones.
  if (yo.rol === 'GERENTE') {
    document.querySelectorAll('.solo-asistente').forEach(el => el.remove());
  }
  return yo;
}

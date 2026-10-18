// Menú lateral común a todas las pantallas internas (diseño Fortress Neo-Editorial).
// Uso en cada página: <header id="barra"></header> ... const yo = await iniciarPagina('panel');
async function iniciarPagina(activa) {
  // Navegación fluida: si ya conocemos al usuario (guardado en esta pestaña), el menú se dibuja
  // al instante y se confirma con el servidor en segundo plano. Si algo cambió, se recarga.
  let guardado = null;
  try { guardado = JSON.parse(sessionStorage.getItem('castillo_yo') || 'null'); } catch (e) { guardado = null; }
  const yo = guardado || await usuarioActual();
  if (!guardado) {
    try { sessionStorage.setItem('castillo_yo', JSON.stringify({ correo: yo.correo, rol: yo.rol })); } catch (e) { /* sin almacenamiento: no pasa nada */ }
  }

  const icono = (trazos) =>
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + trazos + '</svg>';
  const iconos = {
    panel: icono('<rect x="3" y="3" width="7" height="9"/><rect x="14" y="3" width="7" height="5"/><rect x="14" y="12" width="7" height="9"/><rect x="3" y="16" width="7" height="5"/>'),
    pagos: icono('<path d="M8 6h12M8 12h12M8 18h12"/><path d="M3.5 6h.01M3.5 12h.01M3.5 18h.01"/>'),
    beneficiarios: icono('<rect x="3" y="6" width="18" height="14" rx="2"/><path d="M3 10h18"/><circle cx="16" cy="15" r="1.2"/>'),
    obligacion: icono('<circle cx="12" cy="12" r="9"/><path d="M12 8v8M8 12h8"/>')
  };

  const barra = document.getElementById('barra');
  barra.className = 'barra';
  barra.innerHTML =
    '<div class="marca-bloque">' +
      '<span class="logo-c">C</span>' +
      '<div><span class="marca">Castillo</span><span class="marca-sub">Control de pagos</span></div>' +
    '</div>' +
    '<div class="menu-titulo">Menú principal</div>' +
    '<nav class="menu">' +
      '<a href="/panel.html" data-pagina="panel">' + iconos.panel + '<span>Panel</span></a>' +
      '<a href="/pagos.html" data-pagina="pagos">' + iconos.pagos + '<span>Pagos</span></a>' +
      '<a href="/beneficiarios.html" data-pagina="beneficiarios">' + iconos.beneficiarios + '<span>Beneficiarios</span></a>' +
      '<a href="/obligacion-nueva.html" data-pagina="obligacion" class="solo-asistente">' + iconos.obligacion + '<span>Nueva obligación</span></a>' +
    '</nav>' +
    '<div class="usuario">' +
      '<div class="usuario-fila">' +
        '<span class="avatar" id="usuario-inicial"></span>' +
        '<div class="usuario-datos"><span id="usuario-correo"></span><span id="usuario-rol" class="insignia"></span></div>' +
      '</div>' +
      '<button type="button" class="boton boton-secundario" id="boton-salir">Cerrar sesión</button>' +
    '</div>';

  document.getElementById('usuario-correo').textContent = yo.correo;
  document.getElementById('usuario-inicial').textContent = (yo.correo || '?').charAt(0).toUpperCase();
  document.getElementById('usuario-rol').textContent = yo.rol === 'GERENTE' ? 'Gerente' : 'Asistente';
  document.getElementById('boton-salir').addEventListener('click', () => {
    try { sessionStorage.removeItem('castillo_yo'); } catch (e) { /* nada */ }
    cerrarSesion();
  });
  const enlace = barra.querySelector('[data-pagina="' + activa + '"]');
  if (enlace) enlace.classList.add('activo');

  // HU-01 criterio 3: el gerente solo consulta. Todo elemento con la clase "solo-asistente"
  // (crear, editar, subir comprobante) se quita para él. El servidor además rechaza esas acciones.
  if (yo.rol === 'GERENTE') {
    document.querySelectorAll('.solo-asistente').forEach(el => el.remove());
  } else {
    // HU-10: poner en espera y liberar son solo del gerente
    document.querySelectorAll('.solo-gerente').forEach(el => el.remove());
  }
  if (guardado) {
    usuarioActual().then(real => {
      if (real.correo !== guardado.correo || real.rol !== guardado.rol) {
        try { sessionStorage.setItem('castillo_yo', JSON.stringify({ correo: real.correo, rol: real.rol })); } catch (e) { /* nada */ }
        window.location.reload();
      }
    }).catch(() => {});
  }
  return yo;
}

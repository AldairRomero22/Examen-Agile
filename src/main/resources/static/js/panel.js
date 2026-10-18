(async () => {
  const yo = await usuarioActual();
  document.getElementById('usuario-nombre').textContent = yo.correo;
  const rol = document.getElementById('usuario-rol');
  rol.textContent = yo.rol === 'GERENTE' ? 'Gerente' : 'Asistente';
  document.getElementById('saludo').textContent = 'Hola, ' + yo.nombre + '.';

  // HU-01 criterio 3: el gerente solo consulta. Todo elemento con la clase
  // "solo-asistente" (botones de crear, editar, subir comprobante) se oculta para él.
  if (yo.rol === 'GERENTE') {
    document.querySelectorAll('.solo-asistente').forEach(el => el.remove());
  }
})();

(async () => {
  await iniciarPagina('beneficiarios');

  const r = await api('/api/beneficiarios');
  if (!r.ok) {
    mostrarAviso('No se pudo cargar la lista', 'error');
    return;
  }
  const lista = await r.json();

  document.getElementById('filas').innerHTML = lista.map(b =>
    '<tr class="fila-click" data-id="' + b.id + '">' +
      '<td><a href="/beneficiario.html?id=' + b.id + '">' + esc(b.nombre) + '</a></td>' +
      '<td>' + esc(b.tipoDocumento) + ' ' + esc(b.documento) + '</td>' +
      '<td>' + esc(b.banco) + '</td>' +
      '<td><span class="estado estado-activo">' + esc(b.estado) + '</span></td>' +
    '</tr>').join('');

  // Criterio 4: al presionar un beneficiario se abre su pantalla de datos
  document.querySelectorAll('.fila-click').forEach(fila =>
    fila.addEventListener('click', () => { window.location.href = '/beneficiario.html?id=' + fila.dataset.id; }));

  document.getElementById('vacio').hidden = lista.length > 0;
  document.getElementById('envoltorio').hidden = lista.length === 0;
})();

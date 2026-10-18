(async () => {
  await iniciarPagina('beneficiarios');

  const id = new URLSearchParams(window.location.search).get('id');
  const r = await api('/api/beneficiarios/' + encodeURIComponent(id || ''));
  if (!r.ok) {
    document.getElementById('no-existe').hidden = false;
    return;
  }
  const datos = await r.json();
  const b = datos.beneficiario;

  document.getElementById('nombre').textContent = b.nombre;
  document.getElementById('etiqueta-documento').textContent = b.tipoDocumento;
  document.getElementById('documento').textContent = b.documento;
  document.getElementById('banco').textContent = b.banco;
  document.getElementById('cuenta').textContent = b.cuenta;
  document.getElementById('estado').textContent = b.estado;

  // Criterio 4: los gastos asociados, cada uno con su estado
  document.getElementById('filas').innerHTML = datos.gastos.map(o =>
    '<tr>' +
      '<td>' + esc(o.concepto) + '</td>' +
      '<td>' + esc(o.categoria) + '</td>' +
      '<td>' + formatearFecha(o.fechaVencimiento) + '</td>' +
      '<td class="num">' + formatearMonto(o.monto) + '</td>' +
      '<td>' + insigniaEstado(o.estado, o.estadoEtiqueta) + '</td>' +
    '</tr>').join('');

  document.getElementById('vacio').hidden = datos.gastos.length > 0;
  document.getElementById('envoltorio').hidden = datos.gastos.length === 0;
  document.getElementById('contenido').hidden = false;
})();

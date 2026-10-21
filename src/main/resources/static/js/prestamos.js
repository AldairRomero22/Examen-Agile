// Pedido del Product Owner: apartado de préstamos de capital (solo consulta del cronograma).
(async () => {
  await iniciarPagina('prestamos');
  const el = id => document.getElementById(id);
  const fecha = iso => iso ? formatearFecha(iso) : '—';

  if (new URLSearchParams(location.search).has('registrado')) mostrarAviso('Préstamo registrado correctamente', 'ok');

  const r = await api('/api/prestamos');
  el('cargando').hidden = true;
  if (!r.ok) { mostrarAviso('No se pudo cargar la lista de préstamos', 'error'); return; }
  const lista = await r.json();
  el('contador-lista').textContent = lista.length + (lista.length === 1 ? ' préstamo' : ' préstamos');
  if (lista.length === 0) { el('vacio').hidden = false; return; }

  el('filas').innerHTML = lista.map(p =>
    '<tr class="fila-click" data-id="' + p.id + '" tabindex="0">' +
      '<td data-label="Entidad"><a href="/prestamo.html?id=' + p.id + '">' + esc(p.entidad) + '</a></td>' +
      '<td data-label="N.º de crédito">' + esc(p.nroCredito) + '</td>' +
      '<td data-label="Monto" class="num">' + formatearMonto(p.monto) + '</td>' +
      '<td data-label="Tasa anual" class="num">' + esc(p.tasa) + ' %</td>' +
      '<td data-label="Plazo">' + p.plazo + ' cuotas</td>' +
      '<td data-label="Próxima cuota">' + (p.proximaCuota
        ? 'N.º ' + p.proximaCuota + ' · ' + fecha(p.proximoVencimiento) + ' · ' + formatearMonto(p.proximoMonto)
        : 'Sin cuotas pendientes') + '</td>' +
      '<td data-label="Saldo por pagar" class="num">' + formatearMonto(p.saldoPendiente) + '</td>' +
    '</tr>').join('');
  el('envoltorio').hidden = false;
  document.querySelectorAll('#filas .fila-click').forEach(f => {
    const ir = () => { location.href = '/prestamo.html?id=' + f.dataset.id; };
    f.addEventListener('click', e => { if (!e.target.closest('a')) ir(); });
    f.addEventListener('keydown', e => { if (e.key === 'Enter') ir(); });
  });
})();

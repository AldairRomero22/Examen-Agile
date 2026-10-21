// Detalle de un préstamo de capital: sus datos y el cronograma completo (solo consulta).
(async () => {
  await iniciarPagina('prestamos');
  const el = id => document.getElementById(id);
  const id = new URLSearchParams(location.search).get('id');
  const r = await api('/api/prestamos/' + encodeURIComponent(id || ''));
  if (!r.ok) { el('no-existe').hidden = false; return; }
  const d = await r.json();
  const p = d.resumen;

  el('titulo').textContent = p.entidad + ' · Crédito N.º ' + p.nroCredito;
  el('monto').textContent = formatearMonto(p.monto);
  el('registrado').textContent = d.registradoPor + ' · ' + formatearFechaHora(d.registradoEn);

  const dato = (etiqueta, valor) => '<div class="dato-prestamo"><span class="etiqueta-seccion">' + etiqueta + '</span><strong>' + valor + '</strong></div>';
  el('datos').innerHTML =
    dato('Interés anual', esc(p.tasa) + ' %') +
    dato('Plazo', p.plazo + ' cuotas') +
    dato('Desembolso', formatearFecha(p.fechaDesembolso)) +
    dato('Próxima cuota', p.proximaCuota ? 'N.º ' + p.proximaCuota + ' · ' + formatearFecha(p.proximoVencimiento) + ' · ' + formatearMonto(p.proximoMonto) : 'Sin cuotas pendientes') +
    dato('Saldo por pagar', formatearMonto(p.saldoPendiente)) +
    dato('Total de intereses', formatearMonto(p.totalIntereses));

  const hoy = hoyIso();
  el('filas').innerHTML = d.cuotas.map(c => {
    const clase = c.numero === p.proximaCuota ? 'fila-proxima' : (c.vencimiento < hoy ? 'fila-pasada' : '');
    return '<tr class="' + clase + '">' +
      '<td class="num">' + c.numero + '</td>' +
      '<td>' + formatearFecha(c.vencimiento) + '</td>' +
      '<td>' + esc(c.tipoEtiqueta) + '</td>' +
      '<td class="num">' + formatearMonto(c.saldoInicial) + '</td>' +
      '<td class="num">' + formatearMonto(c.interes) + '</td>' +
      '<td class="num">' + formatearMonto(c.amortizacion) + '</td>' +
      '<td class="num"><strong>' + formatearMonto(c.cuota) + '</strong></td>' +
      '<td class="num">' + formatearMonto(c.saldoFinal) + '</td>' +
    '</tr>';
  }).join('');
  const suma = campo => sumarMontos(d.cuotas.map(c => ({ monto: c[campo] })));
  el('totales').innerHTML = '<tr><td colspan="4"><strong>Totales</strong></td>' +
    '<td class="num"><strong>' + formatearMonto(suma('interes')) + '</strong></td>' +
    '<td class="num"><strong>' + formatearMonto(suma('amortizacion')) + '</strong></td>' +
    '<td class="num"><strong>' + formatearMonto(suma('cuota')) + '</strong></td><td></td></tr>';
  el('contenido').hidden = false;
})();

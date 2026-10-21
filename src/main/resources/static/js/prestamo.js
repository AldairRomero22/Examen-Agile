// Detalle de un préstamo de capital: sus datos y el cronograma completo (solo consulta).
(async () => {
  const yo = await iniciarPagina('prestamos');
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

  // El sistema no paga: solo verifica. El botón crea el pago de la cuota y lleva a subir su voucher
  const esAsistente = yo.rol !== 'GERENTE';
  // Mientras la cuota no esté verificada se puede subir (o volver a subir) su voucher
  const SIN_VOUCHER = ['PAGADO', 'EN_VERIFICACION', 'EN_ESPERA'];
  const boton = (texto, atributos) => '<a class="boton boton-secundario boton-chico" ' + atributos + '>' + texto + '</a>';
  const celdaPago = c => {
    const insignia = c.estado ? '<a class="enlace-pago-cuota" href="/pago.html?id=' + c.obligacionId + '">' +
      insigniaEstado(c.estado, c.estadoEtiqueta) + '</a>' : '';
    const faltaVoucher = !c.estado || !SIN_VOUCHER.includes(c.estado);
    if (!esAsistente) {
      return '<td data-label="Voucher"><span class="celda-voucher">' + (insignia || 'Sin voucher') + '</span></td>';
    }
    // sin pago todavía: el botón lo crea primero; con pago: va directo a subir el voucher
    const accion = c.obligacionId
      ? boton(c.estado === 'OBSERVADO' ? 'Subir otro' : 'Subir voucher', 'href="/comprobante-subir.html?id=' + c.obligacionId + '&cuota=1"')
      : '<button type="button" class="boton boton-secundario boton-chico" data-cuota="' + c.numero + '">Subir voucher</button>';
    return '<td data-label="Voucher"><span class="celda-voucher">' + insignia + (faltaVoucher ? accion : '') + '</span></td>';
  };

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
      celdaPago(c) +
    '</tr>';
  }).join('');
  const suma = campo => sumarMontos(d.cuotas.map(c => ({ monto: c[campo] })));
  el('totales').innerHTML = '<tr><td colspan="4"><strong>Totales</strong></td>' +
    '<td class="num"><strong>' + formatearMonto(suma('interes')) + '</strong></td>' +
    '<td class="num"><strong>' + formatearMonto(suma('amortizacion')) + '</strong></td>' +
    '<td class="num"><strong>' + formatearMonto(suma('cuota')) + '</strong></td><td></td><td></td></tr>';
  // "Pagar cuota": crea el pago de esa cuota y lleva a su detalle para subir el voucher
  el('filas').addEventListener('click', async e => {
    const boton = e.target.closest('button[data-cuota]');
    if (!boton) return;
    boton.disabled = true;
    try {
      const r = await api('/api/prestamos/' + encodeURIComponent(id) + '/cuotas/' + boton.dataset.cuota + '/pago', { method: 'POST' });
      const datos = await r.json().catch(() => ({}));
      if (!r.ok || !datos.obligacionId) {
        mostrarAviso(datos.mensaje || 'No se pudo preparar el voucher de la cuota. Intenta de nuevo.', 'error');
        boton.disabled = false;
        return;
      }
      // el sistema no paga: lleva directo a subir el voucher de esa cuota
      location.href = datos.yaExistia ? '/pago.html?id=' + datos.obligacionId
        : '/comprobante-subir.html?id=' + datos.obligacionId + '&cuota=1';
    } catch (err) {
      mostrarAviso('No se pudo preparar el voucher de la cuota. Revisa tu conexión e intenta de nuevo.', 'error');
      boton.disabled = false;
    }
  });

  el('contenido').hidden = false;
})();

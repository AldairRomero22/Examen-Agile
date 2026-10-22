// HU-04 (resumen de todos los pagos o de un mes: total y tarjetas por estado) + HU-05 (alertas de vencimiento).
// El listado completo con filtros vive en pagos.js (menú "Pagos").
(async () => {
  await iniciarPagina('panel');

  const ORDEN_ESTADOS = [
    ['PAGADO', 'Pagado'], ['POR_PAGAR', 'Por pagar'], ['POR_VENCER', 'Por vencer'],
    ['VENCIDO', 'Vencido'],
    // Solo aparecen si hay pagos en ese estado (llegan con HU-07/08)
    ['EN_VERIFICACION', 'En verificación'], ['OBSERVADO', 'Observado']
  ];
  const SIEMPRE_VISIBLES = 4;

  const svg = trazos => '<svg viewBox="0 0 24 24" fill="none" stroke="#171918" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + trazos + '</svg>';
  const ICONOS = {
    PAGADO: svg('<circle cx="12" cy="12" r="9"/><path d="M8 12.5l2.7 2.7L16 9.5"/>'),
    POR_PAGAR: svg('<rect x="4" y="5" width="16" height="15" rx="2"/><path d="M4 10h16M9 3v4M15 3v4"/>'),
    POR_VENCER: svg('<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>'),
    VENCIDO: svg('<path d="M12 3l10 18H2z"/><path d="M12 10v5M12 18v.5"/>'),
    EN_VERIFICACION: svg('<circle cx="11" cy="11" r="6"/><path d="M20 20l-4.5-4.5"/>'),
    OBSERVADO: svg('<circle cx="12" cy="12" r="9"/><path d="M9.5 9.5a2.5 2.5 0 115 0c0 1.7-2.5 2-2.5 3.5M12 17v.5"/>')
  };

  const ICONO_BANCO = svg('<path d="M3 21h18M5 21V10M19 21V10M9 21V10M15 21V10M2 10l10-6 10 6z"/>');
  const MS_DIA = 86400000;

  const params = new URLSearchParams(window.location.search);
  const campoMes = document.getElementById('mes');
  let todos = [];   // todos los pagos de todos los meses
  let lista = [];   // los que se ven: todos o los del mes elegido

  if (params.has('registrado')) mostrarAviso('Obligación registrada correctamente', 'ok');

  await cargarAlertas();
  await cargarCuotasPrestamo();

  // Pedido del Product Owner: por defecto se ven todos los pagos (meses anteriores, actual y futuros)
  await cargarTodos();
  llenarMeses(campoMes, todos, params.get('mes') || '');
  campoMes.addEventListener('change', filtrarMes);
  filtrarMes();

  // Ir a Pagos conservando el mes elegido (y el estado si se toca una tarjeta)
  function irAPagos(estado) {
    const q = new URLSearchParams();
    if (campoMes.value) q.set('mes', campoMes.value);
    if (estado) q.set('estado', estado);
    window.location.href = '/pagos.html?' + q.toString();
  }

  // ---------- Próximas cuotas de préstamos de capital (solo consulta) ----------
  async function cargarCuotasPrestamo() {
    const caja = document.getElementById('cuotas-prestamo');
    let prestamos = [];
    try {
      const r = await api('/api/prestamos');
      if (r.ok) prestamos = await r.json();
    } catch (e) { /* sin préstamos o sin conexión: la tarjeta simplemente no se muestra */ }
    const proximas = prestamos.filter(p => p.proximaCuota)
      .sort((a, b) => String(a.proximoVencimiento).localeCompare(String(b.proximoVencimiento)));
    if (proximas.length === 0) { caja.hidden = true; return; }
    const hoy = Date.parse(hoyIso() + 'T00:00:00Z');
    document.getElementById('contador-prestamos').textContent = proximas.length + (proximas.length === 1 ? ' préstamo' : ' préstamos');
    document.getElementById('cuotas-lista').innerHTML = proximas.slice(0, 3).map(p => {
      const dias = Math.round((Date.parse(p.proximoVencimiento + 'T00:00:00Z') - hoy) / MS_DIA);
      return '<li><a class="alerta alerta-prestamo' + (dias <= 7 ? ' alerta-por-vencer' : '') + '" href="/prestamo.html?id=' + p.id + '">' +
        '<span class="alerta-icono">' + ICONO_BANCO + '</span>' +
        '<span class="alerta-texto">' +
          '<span class="alerta-concepto">' + esc(p.entidad) + ' · cuota ' + p.proximaCuota + ' de ' + p.plazo + '</span>' +
          '<span class="alerta-sub">Crédito ' + esc(p.nroCredito) + ' · vence el ' + formatearFecha(p.proximoVencimiento) + '</span>' +
        '</span>' +
        '<span class="alerta-dias">' + textoDias(dias) + '</span>' +
        '<span class="alerta-monto">' + formatearMonto(p.proximoMonto) + '</span>' +
        '<span class="alerta-boton">Ver cronograma</span>' +
      '</a></li>';
    }).join('');
    caja.hidden = false;
  }

  async function cargarAlertas() {
    const r = await api('/api/obligaciones/urgentes');
    if (!r.ok) return;
    const urgentes = await r.json();
    const resumen = document.getElementById('alertas-resumen');
    const ul = document.getElementById('alertas-lista');
    const caja = document.getElementById('alertas');

    if (urgentes.length === 0) {
      caja.classList.add('alertas-tranquilo');
      resumen.textContent = 'No tienes pagos urgentes';
      ul.innerHTML = '';
      return;
    }
    const n = urgentes.length;
    resumen.textContent = n + (n === 1 ? ' pago urgente' : ' pagos urgentes') + ' · ' + formatearMonto(sumarMontos(urgentes));
    const contador = document.getElementById('contador-alertas');
    contador.textContent = n + (n === 1 ? ' alerta' : ' alertas');
    contador.hidden = false;
    document.getElementById('ver-todos').hidden = false;

    ul.innerHTML = urgentes.slice(0, 5).map(o =>
      '<li><a class="alerta alerta-' + (o.estado === 'VENCIDO' ? 'vencido' : 'por-vencer') + '" href="/pago.html?id=' + o.id + '">' +
        '<span class="alerta-icono">' + (o.estado === 'VENCIDO' ? ICONOS.VENCIDO : ICONOS.POR_VENCER) + '</span>' +
        '<span class="alerta-texto">' +
          '<span class="alerta-concepto">' + esc(o.concepto) + '</span>' +
          '<span class="alerta-sub">' + esc(o.beneficiario) + '</span>' +
        '</span>' +
        '<span class="alerta-dias">' + textoDias(o.diasParaVencer) + '</span>' +
        '<span class="alerta-monto">' + formatearMonto(o.monto) + '</span>' +
        '<span class="alerta-boton">Ver detalle</span>' +
      '</a></li>').join('');
  }

  // ---------- HU-04: todos los pagos, o los del mes elegido ----------
  async function cargarTodos() {
    const r = await api('/api/obligaciones?mes=todos');
    if (!r.ok) {
      mostrarAviso('No se pudo cargar la lista', 'error');
      return;
    }
    todos = await r.json();
  }

  function filtrarMes() {
    const mes = campoMes.value;
    lista = mes ? todos.filter(o => String(o.fechaVencimiento).startsWith(mes)) : todos;
    pintarResumen();
    document.getElementById('ir-pagos').href = '/pagos.html' + (mes ? '?mes=' + encodeURIComponent(mes) : '');
  }

  // Criterio 1: total del mes y, por estado, cantidad y suma
  function pintarResumen() {
    const gastos = items => items.length + (items.length === 1 ? ' gasto' : ' gastos');

    // Total del mes con cuánto va pagado
    const centimos = items => Math.round(sumarMontos(items) * 100);
    const totalC = centimos(lista);
    const pagadoC = centimos(lista.filter(o => o.estado === 'PAGADO'));
    const pct = totalC > 0 ? Math.round(pagadoC * 100 / totalC) : 0;
    document.getElementById('total').innerHTML =
      '<button type="button" class="tarjeta-resumen tarjeta-total" data-estado="">' +
        '<span>' +
          '<span class="tarjeta-etiqueta">' + (campoMes.value ? 'Total de ' + nombreMes(campoMes.value).toLowerCase() : 'Total de todos los pagos') + '</span><br>' +
          '<span class="tarjeta-monto">' + formatearMonto(sumarMontos(lista)) + '</span><br>' +
          '<span class="tarjeta-cantidad">' + gastos(lista) + '</span>' +
        '</span>' +
        '<span>' +
          '<span class="progreso-fila"><span>Pagado a la fecha: <strong>' + formatearMonto(pagadoC / 100) + '</strong></span>' +
          '<span class="progreso-pct">' + pct + '% pagado</span></span>' +
          '<span class="progreso-barra"><i style="width:' + pct + '%"></i></span>' +
          '<span class="progreso-fila"><span>Resta por pagar: <strong>' + formatearMonto((totalC - pagadoC) / 100) + '</strong></span></span>' +
        '</span>' +
      '</button>';

    let html = '';
    ORDEN_ESTADOS.forEach(([codigo, etiqueta], i) => {
      const items = lista.filter(o => o.estado === codigo);
      if (i < SIEMPRE_VISIBLES || items.length > 0) {
        html += '<button type="button" class="tarjeta-resumen tarjeta-' + codigo.toLowerCase().replace(/_/g, '-') + '" data-estado="' + codigo + '">' +
          '<span class="tarjeta-fila">' +
            '<span class="tarjeta-icono">' + ICONOS[codigo] + '</span>' +
            '<span class="tarjeta-texto">' +
              '<span class="tarjeta-etiqueta">' + etiqueta + '</span>' +
              '<span class="tarjeta-monto">' + formatearMonto(sumarMontos(items)) + '</span>' +
            '</span>' +
          '</span>' +
          '<span class="tarjeta-cantidad">' + gastos(items) + '</span>' +
        '</button>';
      }
    });
    document.getElementById('resumen').innerHTML = html;
    document.querySelectorAll('#bloque-resumen .tarjeta-resumen').forEach(b =>
      b.addEventListener('click', () => irAPagos(b.dataset.estado)));
  }
})();

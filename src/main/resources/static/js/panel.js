// HU-04 (panel y pagos del mes) + HU-05 (alertas de vencimiento)
(async () => {
  await iniciarPagina('panel');

  const ORDEN_ESTADOS = [
    ['PAGADO', 'Pagado'], ['POR_PAGAR', 'Por pagar'], ['POR_VENCER', 'Por vencer'],
    ['VENCIDO', 'Vencido'], ['EN_ESPERA', 'En espera'],
    // Solo aparecen si hay pagos en ese estado (llegan con HU-07/08)
    ['EN_VERIFICACION', 'En verificación'], ['OBSERVADO', 'Observado']
  ];
  const SIEMPRE_VISIBLES = 5;

  const svg = trazos => '<svg viewBox="0 0 24 24" fill="none" stroke="#171918" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + trazos + '</svg>';
  const ICONOS = {
    PAGADO: svg('<circle cx="12" cy="12" r="9"/><path d="M8 12.5l2.7 2.7L16 9.5"/>'),
    POR_PAGAR: svg('<rect x="4" y="5" width="16" height="15" rx="2"/><path d="M4 10h16M9 3v4M15 3v4"/>'),
    POR_VENCER: svg('<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>'),
    VENCIDO: svg('<path d="M12 3l10 18H2z"/><path d="M12 10v5M12 18v.5"/>'),
    EN_ESPERA: svg('<circle cx="12" cy="12" r="9"/><path d="M10 9v6M14 9v6"/>'),
    EN_VERIFICACION: svg('<circle cx="11" cy="11" r="6"/><path d="M20 20l-4.5-4.5"/>'),
    OBSERVADO: svg('<circle cx="12" cy="12" r="9"/><path d="M9.5 9.5a2.5 2.5 0 115 0c0 1.7-2.5 2-2.5 3.5M12 17v.5"/>')
  };

  const params = new URLSearchParams(window.location.search);
  const modoUrgentes = params.has('urgentes');
  const campoMes = document.getElementById('mes');
  const filtroCategoria = document.getElementById('filtro-categoria');
  let lista = [];
  let estadoElegido = params.get('estado') || '';

  if (params.has('registrado')) mostrarAviso('Obligación registrada correctamente', 'ok');

  // Categorías para el filtro
  const rCat = await api('/api/categorias');
  if (rCat.ok) {
    (await rCat.json()).forEach(c => {
      filtroCategoria.insertAdjacentHTML('beforeend', '<option value="' + esc(c.nombre) + '">' + esc(c.nombre) + '</option>');
    });
  }
  filtroCategoria.addEventListener('change', pintar);

  await cargarAlertas();

  if (modoUrgentes) {
    // "Ver todos" de las alertas: lista de urgentes de todos los meses
    document.getElementById('titulo-lista').textContent = 'Pagos urgentes';
    document.getElementById('subtitulo-lista').textContent = 'Pagos Vencidos o Por vencer de cualquier mes.';
    document.getElementById('titulo-tabla').textContent = 'Pagos urgentes';
    document.getElementById('caja-mes').hidden = true;
    document.getElementById('volver-mes').hidden = false;
    document.getElementById('bloque-resumen').hidden = true;
    document.getElementById('alertas').hidden = true;
    document.getElementById('vacio').textContent = 'No tienes pagos urgentes';
    const r = await api('/api/obligaciones/urgentes');
    lista = r.ok ? await r.json() : [];
    pintar();
  } else {
    campoMes.value = params.get('mes') || hoyIso().slice(0, 7);
    campoMes.addEventListener('change', cargarMes);
    await cargarMes();
  }

  // ---------- HU-05: alertas ----------
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

  // ---------- HU-04: pagos del mes ----------
  async function cargarMes() {
    if (!campoMes.value) return;
    const r = await api('/api/obligaciones?mes=' + encodeURIComponent(campoMes.value));
    if (!r.ok) {
      mostrarAviso('No se pudo cargar la lista', 'error');
      return;
    }
    lista = await r.json();
    pintarResumen();
    pintar();
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
          '<span class="tarjeta-etiqueta">Total del mes</span><br>' +
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
      b.addEventListener('click', () => { estadoElegido = b.dataset.estado; pintar(); }));
  }

  // Criterios 3, 4, 5 y 6
  function pintar() {
    const categoria = filtroCategoria.value;
    const porCategoria = lista.filter(o => !categoria || o.categoria === categoria);
    const visibles = porCategoria.filter(o => !estadoElegido || o.estado === estadoElegido);

    // Chips de estado con la cantidad de cada uno
    const chips = [['', 'Todos']].concat(ORDEN_ESTADOS).map(([codigo, etiqueta], i) => {
      const cantidad = codigo ? porCategoria.filter(o => o.estado === codigo).length : porCategoria.length;
      if (codigo && i > SIEMPRE_VISIBLES && cantidad === 0) return '';
      const activo = codigo === estadoElegido ? ' chip-activo' : '';
      return '<button type="button" class="chip' + activo + '" data-estado="' + codigo + '" aria-pressed="' + (codigo === estadoElegido) + '">' +
        etiqueta + ' <span class="chip-cantidad">' + cantidad + '</span></button>';
    }).join('');
    const cajaChips = document.getElementById('chips-estado');
    cajaChips.innerHTML = chips;
    cajaChips.querySelectorAll('.chip').forEach(b =>
      b.addEventListener('click', () => { estadoElegido = b.dataset.estado; pintar(); }));

    document.querySelectorAll('.tarjeta-resumen').forEach(t =>
      t.classList.toggle('tarjeta-activa', t.dataset.estado === estadoElegido && estadoElegido !== ''));

    document.getElementById('filas').innerHTML = visibles.map(o =>
      // HU-10 criterio 2: el pago en espera se distingue en la lista
      '<tr class="fila-click' + (o.estado === 'EN_ESPERA' ? ' fila-en-espera' : '') + '" data-id="' + o.id + '" tabindex="0">' +
        '<td><a href="/pago.html?id=' + o.id + '">' + esc(o.concepto) + '</a></td>' +
        '<td>' + esc(o.categoria) + '</td>' +
        '<td>' + esc(o.beneficiario) + '</td>' +
        '<td>' + formatearFecha(o.fechaVencimiento) + '</td>' +
        '<td class="num">' + formatearMonto(o.monto) + '</td>' +
        '<td>' + insigniaEstado(o.estado, o.estadoEtiqueta) + '</td>' +
      '</tr>').join('');
    document.querySelectorAll('#filas .fila-click').forEach(fila => {
      const abrir = () => { window.location.href = '/pago.html?id=' + fila.dataset.id; };
      fila.addEventListener('click', abrir);
      fila.addEventListener('keydown', e => { if (e.key === 'Enter') abrir(); });
    });

    const vacio = document.getElementById('vacio');
    if (!modoUrgentes) {
      vacio.textContent = lista.length === 0
        ? 'Aún no hay pagos registrados este mes'
        : 'Ningún pago cumple los filtros elegidos';
    }
    vacio.hidden = visibles.length > 0;
    document.getElementById('envoltorio').hidden = visibles.length === 0;
    document.getElementById('filtros').hidden = lista.length === 0;
  }
})();

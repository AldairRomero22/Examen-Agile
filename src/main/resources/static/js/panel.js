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
    document.getElementById('caja-mes').hidden = true;
    document.getElementById('volver-mes').hidden = false;
    document.getElementById('resumen').hidden = true;
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
    document.getElementById('ver-todos').hidden = false;

    ul.innerHTML = urgentes.slice(0, 5).map(o =>
      '<li><a class="alerta alerta-' + (o.estado === 'VENCIDO' ? 'vencido' : 'por-vencer') + '" href="/pago.html?id=' + o.id + '">' +
        '<span class="alerta-concepto">' + esc(o.concepto) + '</span>' +
        '<span class="alerta-monto">' + formatearMonto(o.monto) + '</span>' +
        '<span class="alerta-dias">' + textoDias(o.diasParaVencer) + '</span>' +
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
    const tarjeta = (codigo, etiqueta, items, extraClase) =>
      '<button type="button" class="tarjeta-resumen ' + extraClase + '" data-estado="' + codigo + '">' +
        '<span class="tarjeta-etiqueta">' + etiqueta + '</span>' +
        '<span class="tarjeta-monto">' + formatearMonto(sumarMontos(items)) + '</span>' +
        '<span class="tarjeta-cantidad">' + items.length + (items.length === 1 ? ' gasto' : ' gastos') + '</span>' +
      '</button>';

    let html = tarjeta('', 'Total del mes', lista, 'tarjeta-total');
    ORDEN_ESTADOS.forEach(([codigo, etiqueta], i) => {
      const items = lista.filter(o => o.estado === codigo);
      if (i < SIEMPRE_VISIBLES || items.length > 0) {
        html += tarjeta(codigo, etiqueta, items, 'tarjeta-' + codigo.toLowerCase().replace(/_/g, '-'));
      }
    });
    const resumen = document.getElementById('resumen');
    resumen.innerHTML = html;
    resumen.querySelectorAll('.tarjeta-resumen').forEach(b =>
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
      '<tr class="fila-click" data-id="' + o.id + '" tabindex="0">' +
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

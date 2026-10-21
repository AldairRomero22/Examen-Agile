// Menú "Pagos": listado de todos los pagos o de un mes (HU-04) con filtros por estado, categoría y búsqueda.
(async () => {
  await iniciarPagina('pagos');

  const ORDEN_ESTADOS = [
    ['PAGADO', 'Pagado'], ['POR_PAGAR', 'Por pagar'], ['POR_VENCER', 'Por vencer'],
    ['VENCIDO', 'Vencido'], ['EN_ESPERA', 'En espera'],
    // Solo aparecen si hay pagos en ese estado
    ['EN_VERIFICACION', 'En verificación'], ['OBSERVADO', 'Observado']
  ];
  const SIEMPRE_VISIBLES = 5;

  const params = new URLSearchParams(window.location.search);
  const modoUrgentes = params.has('urgentes');
  const campoMes = document.getElementById('mes');
  const filtroCategoria = document.getElementById('filtro-categoria');
  const buscador = document.getElementById('buscar');
  const normal = t => (t || '').toString().toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
  let todos = [];   // todos los pagos de todos los meses
  let lista = [];   // los que se ven: todos o los del mes elegido
  let estadoElegido = params.get('estado') || '';

  if (params.has('registrado')) mostrarAviso('Obligación registrada correctamente', 'ok');

  const rCat = await api('/api/categorias');
  if (rCat.ok) {
    (await rCat.json()).forEach(c => {
      filtroCategoria.insertAdjacentHTML('beforeend', '<option value="' + esc(c.nombre) + '">' + esc(c.nombre) + '</option>');
    });
  }
  filtroCategoria.addEventListener('change', pintar);
  buscador.addEventListener('input', pintar);

  if (modoUrgentes) {
    // "Ver todos" de las alertas: urgentes de todos los meses
    document.getElementById('titulo-lista').textContent = 'Pagos urgentes';
    document.getElementById('subtitulo-lista').textContent = 'Pagos Vencidos o Por vencer de cualquier mes.';
    document.getElementById('titulo-tabla').textContent = 'Pagos urgentes';
    document.getElementById('caja-mes').hidden = true;
    document.getElementById('volver-mes').hidden = false;
    document.getElementById('vacio').textContent = 'No tienes pagos urgentes';
    const r = await api('/api/obligaciones/urgentes');
    lista = r.ok ? await r.json() : [];
    pintar();
  } else {
    // Pedido del Product Owner: por defecto todos los pagos de todos los meses; el mes es un filtro opcional
    const r = await api('/api/obligaciones?mes=todos');
    if (!r.ok) {
      document.getElementById('cargando').hidden = true;
      mostrarAviso('No se pudo cargar la lista', 'error');
      return;
    }
    todos = await r.json();
    llenarMeses(campoMes, todos, params.get('mes') || '');
    campoMes.addEventListener('change', filtrarMes);
    filtrarMes();
  }

  function filtrarMes() {
    const mes = campoMes.value;
    lista = mes ? todos.filter(o => String(o.fechaVencimiento).startsWith(mes)) : todos;
    document.getElementById('titulo-tabla').textContent = mes ? 'Pagos de ' + nombreMes(mes).toLowerCase() : 'Todos los pagos';
    document.getElementById('vacio').textContent = mes ? 'No hay pagos con vencimiento en ' + nombreMes(mes).toLowerCase() : 'Aún no hay pagos registrados';
    pintar();
  }


  function pintar() {
    document.getElementById('cargando').hidden = true;
    const categoria = filtroCategoria.value;
    const texto = normal(buscador.value.trim());
    const base = lista.filter(o =>
      (!categoria || o.categoria === categoria) &&
      (!texto || normal(o.concepto).includes(texto) || normal(o.beneficiario).includes(texto)));
    const visibles = base.filter(o => !estadoElegido || o.estado === estadoElegido);

    const chips = [['', 'Todos']].concat(ORDEN_ESTADOS).map(([codigo, etiqueta], i) => {
      const cantidad = codigo ? base.filter(o => o.estado === codigo).length : base.length;
      if (codigo && i > SIEMPRE_VISIBLES && cantidad === 0 && codigo !== estadoElegido) return '';
      const activo = codigo === estadoElegido ? ' chip-activo' : '';
      return '<button type="button" class="chip' + activo + '" data-estado="' + codigo + '" aria-pressed="' + (codigo === estadoElegido) + '">' +
        etiqueta + ' <span class="chip-cantidad">' + cantidad + '</span></button>';
    }).join('');
    const cajaChips = document.getElementById('chips-estado');
    cajaChips.innerHTML = chips;
    cajaChips.querySelectorAll('.chip').forEach(b =>
      b.addEventListener('click', () => { estadoElegido = b.dataset.estado; pintar(); }));

    document.getElementById('filas').innerHTML = visibles.map(o =>
      '<tr class="fila-click' + (o.estado === 'EN_ESPERA' ? ' fila-en-espera' : '') + '" data-id="' + o.id + '" tabindex="0">' +
        '<td data-label="Concepto"><a href="/pago.html?id=' + o.id + '">' + esc(o.concepto) + '</a></td>' +
        '<td data-label="Categoría">' + esc(o.categoria) + '</td>' +
        '<td data-label="A quién se paga">' + esc(o.beneficiario) + '</td>' +
        '<td data-label="Vence">' + formatearFecha(o.fechaVencimiento) + '</td>' +
        '<td data-label="Monto" class="num">' + formatearMonto(o.monto) + '</td>' +
        '<td data-label="Estado">' + insigniaEstado(o.estado, o.estadoEtiqueta) + '</td>' +
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
    document.getElementById('contador-lista').textContent =
      visibles.length + (visibles.length === 1 ? ' pago' : ' pagos') +
      (visibles.length > 0 ? ' · ' + formatearMonto(sumarMontos(visibles)) : '');
  }
})();

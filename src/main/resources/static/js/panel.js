(async () => {
  await iniciarPagina('panel');

  const params = new URLSearchParams(window.location.search);
  const campoMes = document.getElementById('mes');
  campoMes.value = params.get('mes') || hoyIso().slice(0, 7);

  if (params.has('registrado')) {
    mostrarAviso('Obligación registrada correctamente', 'ok');
  }

  campoMes.addEventListener('change', cargar);
  await cargar();

  async function cargar() {
    const filas = document.getElementById('filas');
    const vacio = document.getElementById('vacio');
    const envoltorio = document.getElementById('envoltorio');
    if (!campoMes.value) return;

    const r = await api('/api/obligaciones?mes=' + encodeURIComponent(campoMes.value));
    if (!r.ok) {
      mostrarAviso('No se pudo cargar la lista', 'error');
      return;
    }
    const lista = await r.json();

    filas.innerHTML = lista.map(o =>
      '<tr>' +
        '<td>' + esc(o.concepto) + '</td>' +
        '<td>' + esc(o.categoria) + '</td>' +
        '<td><a href="/beneficiario.html?id=' + o.beneficiarioId + '">' + esc(o.beneficiario) + '</a></td>' +
        '<td>' + formatearFecha(o.fechaVencimiento) + '</td>' +
        '<td class="num">' + formatearMonto(o.monto) + '</td>' +
        '<td>' + insigniaEstado(o.estado, o.estadoEtiqueta) + '</td>' +
      '</tr>').join('');

    vacio.hidden = lista.length > 0;
    envoltorio.hidden = lista.length === 0;
  }
})();

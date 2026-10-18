// HU-06: ver el detalle del pago
(async () => {
  await iniciarPagina('panel');
  const el = id => document.getElementById(id);

  const id = new URLSearchParams(window.location.search).get('id');
  const r = await api('/api/obligaciones/' + encodeURIComponent(id || ''));
  if (!r.ok) {
    // Criterio 4
    el('no-existe').hidden = false;
    return;
  }
  const p = await r.json();
  const b = p.beneficiario;

  // Criterio 1
  el('concepto').textContent = p.concepto;
  el('estado').innerHTML = insigniaEstado(p.estado, p.estadoEtiqueta);
  el('monto').textContent = formatearMonto(p.monto);
  const pendiente = ['POR_PAGAR', 'POR_VENCER', 'VENCIDO'].includes(p.estado);
  el('vencimiento').textContent = formatearFecha(p.fechaVencimiento) + (pendiente ? ' · ' + textoDias(p.diasParaVencer) : '');
  el('categoria').textContent = p.categoria;
  el('registrado-por').textContent = p.registradoPor;
  el('registrado-en').textContent = formatearFechaHora(p.registradoEn);
  if (p.observacion) {
    el('observacion').textContent = p.observacion;
    el('caja-observacion').hidden = false;
  }
  el('volver').href = '/panel.html?mes=' + p.fechaVencimiento.slice(0, 7);

  // Criterio 2
  el('beneficiario').textContent = b.nombre;
  el('beneficiario').href = '/beneficiario.html?id=' + b.id;
  el('etiqueta-documento').textContent = b.tipoDocumento;
  el('documento').textContent = b.documento;
  el('banco').textContent = b.banco;
  el('cuenta').textContent = b.cuenta;

  // Criterio 3: copiar al portapapeles y avisar "Datos copiados"
  el('copiar').addEventListener('click', async () => {
    const texto =
      'Beneficiario: ' + b.nombre + '\n' +
      b.tipoDocumento + ': ' + b.documento + '\n' +
      'Banco: ' + b.banco + '\n' +
      'Cuenta o CCI: ' + b.cuenta + '\n' +
      'Monto: ' + formatearMonto(p.monto);
    const ok = await copiar(texto);
    mostrarAviso(ok ? 'Datos copiados' : 'No se pudo copiar. Selecciona los datos y cópialos a mano.', ok ? 'ok' : 'error');
    if (ok) setTimeout(() => mostrarAviso(''), 3000);
  });

  el('contenido').hidden = false;

  async function copiar(texto) {
    try {
      await navigator.clipboard.writeText(texto);
      return true;
    } catch (e) {
      // Respaldo para navegadores sin acceso al portapapeles
      const area = document.createElement('textarea');
      area.value = texto;
      area.style.position = 'fixed';
      area.style.opacity = '0';
      document.body.appendChild(area);
      area.select();
      let ok = false;
      try { ok = document.execCommand('copy'); } catch (e2) { ok = false; }
      area.remove();
      return ok;
    }
  }
})();

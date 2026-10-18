// HU-09: ver el comprobante de un pago Pagado u Observado (solo lectura para gerente y asistente)
(async () => {
  await iniciarPagina('panel');
  const el = id => document.getElementById(id);

  const id = new URLSearchParams(window.location.search).get('id');
  const rp = await api('/api/obligaciones/' + encodeURIComponent(id || ''));
  if (!rp.ok) {
    el('no-existe').hidden = false;
    return;
  }
  const p = await rp.json();
  el('volver').href = '/pago.html?id=' + p.id;

  const rc = await api('/api/obligaciones/' + p.id + '/comprobante');
  if (!rc.ok) {
    el('no-existe').textContent = 'Este pago aún no tiene comprobante.';
    el('no-existe').hidden = false;
    return;
  }
  const c = await rc.json();

  el('concepto').textContent = p.concepto;
  el('monto').textContent = formatearMonto(p.monto);
  el('estado').innerHTML = insigniaEstado(p.estado, p.estadoEtiqueta);
  el('insignia').innerHTML = c.insignia ? insigniaVerificacion(c.insignia, c.insigniaEtiqueta) : '';

  // ---------- El archivo (imagen o PDF) con abrir y descargar ----------
  const url = '/api/comprobantes/' + c.id + '/archivo';
  el('archivo-info').textContent = c.nombreArchivo + ' · ' + formatearTamano(c.tamanoBytes);
  el('visor').innerHTML = c.tipoMime === 'application/pdf'
    ? '<iframe src="' + url + '" title="Comprobante en PDF"></iframe>'
    : '<img src="' + url + '" alt="Comprobante de pago">';
  el('abrir').href = url;
  el('descargar').href = url + '?descargar=true';

  // ---------- Datos leídos, quién lo registró y cuándo ----------
  const mano = '<span class="marca-mano">' + ICONO_LAPIZ + 'escrito a mano</span>';
  el('d-monto').innerHTML = esc(formatearMonto(c.monto)) + (c.montoManual ? ' ' + mano : '');
  el('d-fecha').innerHTML = esc(formatearFecha(c.fecha)) + (c.fechaManual ? ' ' + mano : '');
  el('d-nro').innerHTML = esc(c.nroOperacion) + (c.nroOperacionManual ? ' ' + mano : '');
  el('d-subido-por').textContent = c.subidoPor;
  el('d-subido-en').textContent = formatearFechaHora(c.subidoEn);
  el('d-lector').textContent = c.lector === 'TESSERACT' ? 'Lectura automática (OCR)' : 'Lectura automática (IA)';
  el('nota-manual').hidden = !(c.montoManual || c.fechaManual || c.nroOperacionManual);

  // ---------- Resultado de las 4 reglas ----------
  if (c.resultado && c.reglas && c.reglas.length) {
    el('resultado').innerHTML = panelResultado(c.resultado, c.insignia, c.insigniaEtiqueta, c.reglas, '');
  }

  // ---------- Desde un pago Observado, solo el asistente puede subir otro ----------
  // (para el gerente la página ya no tiene este bloque: layout.js lo quita, por eso se comprueba que exista)
  if (p.estado === 'OBSERVADO' && el('acciones-observado')) {
    el('subir-otro').href = '/comprobante-subir.html?id=' + p.id;
    el('acciones-observado').hidden = false;
  }

  el('contenido').hidden = false;
})();

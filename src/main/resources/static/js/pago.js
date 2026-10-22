// HU-06: ver el detalle del pago
(async () => {
  const yo = await iniciarPagina('pagos');
  const el = id => document.getElementById(id);
  const esGerente = yo.rol === 'GERENTE';

  const params = new URLSearchParams(window.location.search);
  const id = params.get('id');
  const r = await api('/api/obligaciones/' + encodeURIComponent(id || ''));
  if (!r.ok) {
    // HU-06 criterio 4
    el('no-existe').hidden = false;
    return;
  }
  let p = await r.json();

  pintar();
  pintarVerificacion();

  // Viene de "Subir voucher" en el cronograma de un préstamo
  if (params.has('cuota')) {
    mostrarAviso('Esta cuota ya está registrada como pago. Sube su voucher para verificarla.', 'ok');
  }

  // HU-06 criterio 2: a quién se paga
  el('beneficiario').textContent = p.beneficiario;
  el('ruc-pago').textContent = p.ruc ? (p.ruc.length === 11 ? 'RUC ' : 'DNI ') + p.ruc : 'Sin RUC registrado';

  // El asistente sube el comprobante del pago
  if (!esGerente) {
    el('subir').addEventListener('click', () => {
      window.location.href = '/comprobante-subir.html?id=' + p.id;
    });
  }

  // Constancia en PDF (ambos roles): incluye el comprobante y su verificación si el pago ya tiene uno
  el('constancia').addEventListener('click', async () => {
    const boton = el('constancia');
    boton.disabled = true;
    try {
      let comprobante = null;
      if (p.estado === 'PAGADO' || p.estado === 'OBSERVADO') {
        const rc = await api('/api/obligaciones/' + p.id + '/comprobante');
        if (rc.ok) comprobante = await rc.json();
      }
      await descargarConstancia(p, comprobante, yo.correo);
    } catch (err) {
      mostrarAviso('No se pudo generar el PDF', 'error');
    } finally {
      boton.disabled = false;
    }
  });

  el('contenido').hidden = false;

  // Pinta todo lo que depende del estado 
  function pintar() {
    // HU-06 criterio 1
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

    if (!esGerente) {
      el('subir').hidden = p.estado === 'PAGADO' || p.estado === 'EN_VERIFICACION';
      el('subir').textContent = p.estado === 'OBSERVADO' ? 'Subir otro comprobante' : 'Subir comprobante';
    }
    // Si el rol no tiene nada que hacer con este pago, se dice en lugar de dejar la tarjeta vacía
    const hayBoton = [...document.querySelectorAll('.acciones-pago .boton')].some(x => !x.hidden);
    el('sin-acciones').hidden = hayBoton;
  }

  // HU-09: en pagos Pagado u Observado se muestra la verificación y el enlace "Ver comprobante"
  async function pintarVerificacion() {
    const tiene = p.estado === 'PAGADO' || p.estado === 'OBSERVADO';
    el('caja-verificacion').hidden = !tiene;
    if (!tiene) return;
    el('ver-comprobante').href = '/comprobante-ver.html?id=' + p.id;
    try {
      const resp = await api('/api/obligaciones/' + p.id + '/comprobante');
      if (!resp.ok) return;
      const c = await resp.json();
      el('verif-insignia').innerHTML = c.insignia ? insigniaVerificacion(c.insignia, c.insigniaEtiqueta) : '';
      const fallas = (c.reglas || []).filter(x => !x.cumple).map(x => x.mensaje);
      el('verif-fallas').innerHTML = fallas.map(f => '<li>' + esc(f) + '</li>').join('');
      el('verif-fallas').hidden = fallas.length === 0;
    } catch (err) {
      // el enlace "Ver comprobante" sigue disponible aunque no se pueda traer el resumen
    }
  }

})();

// HU-07: subir el comprobante con lectura automática
// HU-08: enviarlo, verlo verificado u observado, y reenviar otro si queda Observado
(async () => {
  await iniciarPagina('panel');
  const el = id => document.getElementById(id);
  const MAXIMO = 5 * 1024 * 1024;
  const TIPOS_OK = ['image/jpeg', 'image/png', 'application/pdf'];
  const EXT_OK = /\.(jpe?g|png|pdf)$/i;

  const id = new URLSearchParams(window.location.search).get('id');
  const r = await api('/api/obligaciones/' + encodeURIComponent(id || ''));
  if (!r.ok) {
    el('no-existe').hidden = false;
    return;
  }
  const p = await r.json();
  el('volver').href = '/pago.html?id=' + p.id;

  // ---------- HU-07 criterio 1: solo Por pagar, Por vencer, Vencido u Observado ----------
  const mensajeBloqueo = {
    PAGADO: 'Este pago ya está pagado y no admite nuevos comprobantes.',
    EN_ESPERA: 'Este pago está en espera: el gerente debe liberarlo antes de subir un comprobante.',
    EN_VERIFICACION: 'Este pago ya se está verificando.'
  }[p.estado];
  if (mensajeBloqueo) {
    el('bloqueado').innerHTML = esc(mensajeBloqueo) + ' <a href="/pago.html?id=' + p.id + '">Volver al detalle del pago</a>';
    el('bloqueado').hidden = false;
    return;
  }

  el('concepto').textContent = p.concepto;
  el('monto').textContent = formatearMonto(p.monto);
  el('estado').innerHTML = insigniaEstado(p.estado, p.estadoEtiqueta);
  const pendiente = ['POR_PAGAR', 'POR_VENCER', 'VENCIDO'].includes(p.estado);
  el('vencimiento').textContent = formatearFecha(p.fechaVencimiento) + (pendiente ? ' · ' + textoDias(p.diasParaVencer) : '');
  el('monto-obligacion').textContent = formatearMonto(p.monto);
  el('zona-icono').innerHTML = ICONO_NUBE;
  el('contenido').hidden = false;

  // HU-08 criterio 4: desde un pago Observado se sube otro archivo; se recuerda qué falló la vez anterior
  if (p.estado === 'OBSERVADO') {
    const prev = await api('/api/obligaciones/' + p.id + '/comprobante');
    if (prev.ok) {
      const c = await prev.json();
      const fallas = (c.reglas || []).filter(x => !x.cumple).map(x => x.mensaje);
      el('observado-previo').innerHTML = '<strong>Este pago está observado.</strong> Sube otro comprobante.' +
        (fallas.length ? '<ul class="lista-fallas">' + fallas.map(f => '<li>' + esc(f) + '</li>').join('') + '</ul>' : '');
      el('observado-previo').hidden = false;
    }
  }

  // Campo de la pantalla donde se muestra cada error que devuelve el servidor
  const CAMPOS = { archivo: 'archivo', fecha: 'fecha-leida', nroOperacion: 'nro-leido', monto: 'monto-leido' };

  let comprobante = null;          // lo que devolvió el servidor al subir (ComprobanteLeido)
  let vistaPrevia = null;          // URL temporal de la vista previa

  // ---------- Elegir el archivo ----------
  el('archivo').addEventListener('change', () => elegir(el('archivo').files[0]));
  ['dragover', 'dragenter'].forEach(ev => el('zona').addEventListener(ev, e => {
    e.preventDefault();
    el('zona').classList.add('arrastrando');
  }));
  ['dragleave', 'drop'].forEach(ev => el('zona').addEventListener(ev, e => {
    e.preventDefault();
    el('zona').classList.remove('arrastrando');
  }));
  el('zona').addEventListener('drop', e => {
    if (e.dataTransfer && e.dataTransfer.files.length) {
      el('archivo').files = e.dataTransfer.files;
      elegir(e.dataTransfer.files[0]);
    }
  });

  function elegir(archivo) {
    marcarError('archivo', '');
    mostrarAviso('');
    liberarVistaPrevia();
    el('elegido').hidden = true;
    el('leer').disabled = true;
    if (!archivo) return;

    // HU-07 criterio 2: JPG, PNG o PDF de hasta 5 MB (el servidor lo vuelve a comprobar)
    const tipoOk = TIPOS_OK.includes(archivo.type) || (!archivo.type && EXT_OK.test(archivo.name));
    if (!tipoOk || !EXT_OK.test(archivo.name) || archivo.size > MAXIMO || archivo.size === 0) {
      el('archivo').value = '';
      marcarError('archivo', 'Formato o tamaño no permitido');
      return;
    }
    el('nombre-archivo').textContent = archivo.name;
    el('tamano-archivo').textContent = formatearTamano(archivo.size);
    if (archivo.type.startsWith('image/')) {
      vistaPrevia = URL.createObjectURL(archivo);
      el('vista-previa').src = vistaPrevia;
      el('vista-previa').hidden = false;
    } else {
      el('vista-previa').hidden = true;
    }
    el('elegido').hidden = false;
    el('leer').disabled = false;
  }

  function liberarVistaPrevia() {
    if (vistaPrevia) {
      URL.revokeObjectURL(vistaPrevia);
      vistaPrevia = null;
    }
  }

  // ---------- HU-07 criterio 3: subir y leer ----------
  el('leer').addEventListener('click', async () => {
    const archivo = el('archivo').files[0];
    if (!archivo) {
      marcarError('archivo', 'Selecciona el archivo del voucher');   // criterio 1: sin archivo no se continúa
      return;
    }
    mostrarAviso('');
    marcarError('archivo', '');
    ocupado(true);
    try {
      const datos = new FormData();
      datos.append('archivo', archivo);
      const resp = await api('/api/obligaciones/' + p.id + '/comprobantes', { method: 'POST', body: datos });
      const cuerpo = await resp.json().catch(() => ({}));
      if (!resp.ok) {
        if (cuerpo.campo === 'archivo') {
          marcarError('archivo', cuerpo.mensaje);
        } else {
          mostrarAviso(cuerpo.mensaje || 'No se pudo leer el voucher, intenta de nuevo', 'error');   // criterio 5
        }
        return;
      }
      comprobante = cuerpo;
      mostrarLectura();
    } catch (err) {
      mostrarAviso('No se pudo leer el voucher, intenta de nuevo', 'error');
    } finally {
      ocupado(false);
    }
  });

  function ocupado(si) {
    el('leyendo').hidden = !si;
    el('leer').disabled = si || !el('archivo').files[0];
    el('archivo').disabled = si;
    el('enviar').disabled = si || el('enviar').disabled;
  }

  // ---------- Datos leídos (solo lectura) y campos que quedaron vacíos ----------
  function mostrarLectura() {
    el('espera-lectura').hidden = true;
    el('form-leido').hidden = false;

    configurarCampo('monto-leido', 'marca-monto', comprobante.monto != null,
      comprobante.monto != null ? formatearMonto(comprobante.monto) : '', 'Ejemplo: 1280.00');
    configurarCampo('fecha-leida', 'marca-fecha', comprobante.fecha != null, comprobante.fecha || '', '');
    configurarCampo('nro-leido', 'marca-nro', comprobante.nroOperacion != null, comprobante.nroOperacion || '', 'Ejemplo: 12345678');

    const vacios = !comprobante.monto || !comprobante.fecha || !comprobante.nroOperacion;
    if (vacios) {
      mostrarAviso('No se pudieron leer todos los datos. Sube otra imagen más clara o escribe solo los campos vacíos.', 'info');
    }
    validar();
    el('form-leido').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  /** Un dato leído queda bloqueado; uno vacío se puede escribir a mano (HU-07 criterio 4). */
  function configurarCampo(idCampo, idMarca, leido, valor, ejemplo) {
    const campo = el(idCampo);
    campo.value = valor;
    campo.readOnly = leido;
    campo.classList.toggle('campo-leido', leido);
    campo.classList.remove('campo-error');
    campo.placeholder = leido ? '' : ejemplo;
    campo.dataset.leido = leido ? '1' : '0';
    el(idMarca).innerHTML = leido
      ? '<span class="marca-leido">' + ICONO_CANDADO + 'Leído del voucher · no se puede modificar</span>'
      : '<span class="marca-mano">' + ICONO_LAPIZ + 'No se pudo leer: escríbelo (quedará «escrito a mano»)</span>';
  }

  ['monto-leido', 'fecha-leida', 'nro-leido'].forEach(i => el(i).addEventListener('input', validar));

  /** HU-07 criterio 6: "Enviar comprobante" solo se activa con los tres campos llenos y válidos. */
  function validar() {
    const datos = valores();
    ['monto-leido', 'fecha-leida', 'nro-leido'].forEach(i => marcarError(i, ''));

    let completo = true;
    // Monto
    if (el('monto-leido').dataset.leido === '0') {
      const texto = el('monto-leido').value.trim();
      if (!texto) completo = false;
      else if (Number.isNaN(datos.montoNumero)) {
        marcarError('monto-leido', 'Escribe un monto válido (máximo 2 decimales)');
        completo = false;
      }
    }
    // Fecha
    if (el('fecha-leida').dataset.leido === '0' && !el('fecha-leida').value) completo = false;
    // Número de operación
    if (el('nro-leido').dataset.leido === '0') {
      const nro = el('nro-leido').value.replace(/\s+/g, '').toUpperCase();
      if (!nro) completo = false;
      else if (!/^[A-Z0-9-]{4,40}$/.test(nro)) {
        marcarError('nro-leido', 'De 4 a 40 letras o números');
        completo = false;
      }
    }
    // Marca "escrito a mano" cuando el asistente escribe en un campo que el lector dejó vacío
    [['monto-leido', 'marca-monto'], ['fecha-leida', 'marca-fecha'], ['nro-leido', 'marca-nro']].forEach(([c, m]) => {
      if (el(c).dataset.leido === '0' && el(c).value.trim()) {
        el(m).innerHTML = '<span class="marca-mano">' + ICONO_LAPIZ + 'Escrito a mano</span>';
      }
    });

    // Avisos que no bloquean: el sistema lo marcará Observado
    const montoFinal = el('monto-leido').dataset.leido === '1' ? comprobante.monto : datos.montoNumero;
    const alertaMonto = el('alerta-monto');
    alertaMonto.hidden = !(montoFinal && !Number.isNaN(montoFinal) && Math.round(montoFinal * 100) !== Math.round(Number(p.monto) * 100));
    alertaMonto.textContent = 'No coincide con el monto de la obligación (' + formatearMonto(p.monto) + '): el pago quedará Observado.';
    const fechaFinal = el('fecha-leida').value;
    const alertaFecha = el('alerta-fecha');
    alertaFecha.hidden = !(fechaFinal && fechaFinal > hoyIso());
    alertaFecha.textContent = 'La fecha es posterior a hoy: el pago quedará Observado.';

    el('enviar').disabled = !completo;
  }

  /** Lo que se manda: solo los campos que el lector dejó vacíos (el servidor usa lo leído para los demás). */
  function valores() {
    return {
      montoNumero: el('monto-leido').dataset.leido === '0' ? parsearMonto(el('monto-leido').value) : null,
      fecha: el('fecha-leida').dataset.leido === '0' ? (el('fecha-leida').value || null) : null,
      nroOperacion: el('nro-leido').dataset.leido === '0'
        ? el('nro-leido').value.replace(/\s+/g, '').toUpperCase() || null : null
    };
  }

  // ---------- HU-08: enviar el comprobante ----------
  el('form-leido').addEventListener('submit', async e => {
    e.preventDefault();
    if (el('enviar').disabled) return;
    const v = valores();
    el('enviar').disabled = true;
    mostrarAviso('');
    try {
      const resp = await api('/api/comprobantes/' + comprobante.comprobanteId + '/enviar', {
        method: 'POST',
        body: JSON.stringify({ fecha: v.fecha, nroOperacion: v.nroOperacion, monto: v.montoNumero })
      });
      const cuerpo = await resp.json().catch(() => ({}));
      if (!resp.ok) {
        if (cuerpo.campo && CAMPOS[cuerpo.campo]) {
          marcarError(CAMPOS[cuerpo.campo], cuerpo.mensaje);
        } else {
          mostrarAviso(cuerpo.mensaje || 'No se pudo enviar el comprobante', 'error');
        }
        el('enviar').disabled = false;
        return;
      }
      mostrarResultado(cuerpo);
    } catch (err) {
      mostrarAviso('No se pudo conectar con el servidor', 'error');
      el('enviar').disabled = false;
    }
  });

  el('otra-imagen').addEventListener('click', () => {
    comprobante = null;
    el('archivo').value = '';
    el('elegido').hidden = true;
    liberarVistaPrevia();
    el('leer').disabled = true;
    el('form-leido').hidden = true;
    el('espera-lectura').hidden = false;
    mostrarAviso('');
  });

  // ---------- HU-08 criterios 2 y 3: Pagado (4 marcas) u Observado (qué regla falló) ----------
  function mostrarResultado(res) {
    el('paso-subir').hidden = true;
    el('observado-previo').hidden = true;
    mostrarAviso('');
    const acciones = res.resultado === 'PAGADO'
      ? '<a class="boton boton-turquesa" href="/comprobante-ver.html?id=' + p.id + '">Ver comprobante</a>' +
        '<a class="boton boton-secundario" href="/pago.html?id=' + p.id + '">Volver al detalle del pago</a>'
      : '<a class="boton boton-primario" href="/comprobante-subir.html?id=' + p.id + '">Subir otro comprobante</a>' +
        '<a class="boton boton-secundario" href="/pago.html?id=' + p.id + '">Volver al detalle del pago</a>';
    el('paso-resultado').innerHTML = panelResultado(res.resultado, res.insignia, res.insigniaEtiqueta, res.reglas, acciones);
    el('paso-resultado').hidden = false;
    el('estado').innerHTML = insigniaEstado(res.resultado, res.resultadoEtiqueta);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
})();

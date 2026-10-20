// HU-06: ver el detalle del pago
// HU-10: poner en espera y liberar (gerente); aviso y bloqueo de subida (asistente)
(async () => {
  const yo = await iniciarPagina('pagos');
  const el = id => document.getElementById(id);
  const esGerente = yo.rol === 'GERENTE';

  const id = new URLSearchParams(window.location.search).get('id');
  const r = await api('/api/obligaciones/' + encodeURIComponent(id || ''));
  if (!r.ok) {
    // HU-06 criterio 4
    el('no-existe').hidden = false;
    return;
  }
  let p = await r.json();

  pintar();
  pintarVerificacion();

  // HU-06 criterio 2: a quién se paga
  el('beneficiario').textContent = p.beneficiario;

  // ---------- HU-10: acciones del gerente ----------
  if (esGerente) {
    el('poner-espera').addEventListener('click', () => {
      mostrarAviso('');
      el('form-espera').hidden = false;
      el('poner-espera').hidden = true;
      el('nota').focus();
    });
    el('cancelar-espera').addEventListener('click', cerrarFormulario);
    el('nota').addEventListener('input', () => {
      el('contador-nota').textContent = el('nota').value.length;
      marcarError('nota', '');
    });

    // Criterios 1 y 2: nota obligatoria de hasta 200 caracteres
    el('form-espera').addEventListener('submit', async e => {
      e.preventDefault();
      const nota = el('nota').value.trim();
      if (!nota) {
        marcarError('nota', 'Escribe una nota para el asistente');
        return;
      }
      if (nota.length > 200) {
        marcarError('nota', 'Máximo 200 caracteres');
        return;
      }
      await enviar('/espera', { nota }, 'El pago quedó en espera');
    });

    // Criterio 4: vuelve al estado que le toca por su fecha
    el('liberar').addEventListener('click', () => enviar('/liberar', null, 'Pago liberado'));
  }

  // ---------- HU-10 criterio 3: el asistente no puede subir comprobante si está en espera ----------
  if (!esGerente) {
    el('subir').addEventListener('click', () => {
      if (p.enEspera) return;
      window.location.href = '/comprobante-subir.html?id=' + p.id;
    });
  }

  el('contenido').hidden = false;

  async function enviar(ruta, cuerpo, mensajeOk) {
    const botones = ['confirmar-espera', 'liberar'].map(el);
    botones.forEach(x => { x.disabled = true; });
    try {
      const resp = await api('/api/obligaciones/' + p.id + ruta, {
        method: 'POST',
        body: cuerpo ? JSON.stringify(cuerpo) : undefined
      });
      const datos = await resp.json().catch(() => ({}));
      if (!resp.ok) {
        if (datos.errores && datos.errores.nota) {
          marcarError('nota', datos.errores.nota);
        } else {
          mostrarAviso(datos.mensaje || 'No se pudo guardar', 'error');
        }
        return;
      }
      p = datos;
      cerrarFormulario();
      pintar();
      pintarVerificacion();
      mostrarAviso(mensajeOk, 'ok');
      setTimeout(() => mostrarAviso(''), 3000);
    } catch (err) {
      mostrarAviso('No se pudo conectar con el servidor', 'error');
    } finally {
      botones.forEach(x => { x.disabled = false; });
    }
  }

  function cerrarFormulario() {
    if (!esGerente) return;
    el('form-espera').hidden = true;
    el('nota').value = '';
    el('contador-nota').textContent = '0';
    marcarError('nota', '');
    pintar();
  }

  // Pinta todo lo que depende del estado (se repite después de poner en espera o liberar)
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
    el('volver').href = '/pagos.html?mes=' + p.fechaVencimiento.slice(0, 7);

    // HU-10 criterios 2 y 3: nota del gerente visible para los dos roles
    el('caja-espera').hidden = !p.enEspera;
    if (p.enEspera) {
      el('nota-espera').textContent = p.notaEspera;
      el('espera-info').textContent = 'Por ' + p.esperaPor + ' · ' + formatearFechaHora(p.esperaEn);
    }
    el('liberado-info').hidden = !(!p.enEspera && p.liberadoEn);
    if (!p.enEspera && p.liberadoEn) {
      el('liberado-info').textContent = 'Liberado por ' + p.liberadoPor + ' el ' + formatearFechaHora(p.liberadoEn);
    }
    document.querySelector('main').classList.toggle('pago-en-espera', p.enEspera);

    const pagado = p.estado === 'PAGADO';
    if (esGerente) {
      // Criterio 1: "Poner en espera" solo si no está Pagado; criterio 4: "Liberar pago" si está en espera
      el('poner-espera').hidden = pagado || p.enEspera || !el('form-espera').hidden;
      el('liberar').hidden = !p.enEspera;
    } else {
      // Criterio 3: botón deshabilitado con el mensaje del gerente
      el('subir').hidden = pagado || p.estado === 'EN_VERIFICACION';
      el('subir').textContent = p.estado === 'OBSERVADO' ? 'Subir otro comprobante' : 'Subir comprobante';
      el('subir').disabled = p.enEspera;
      el('subir-bloqueado').hidden = !p.enEspera;
    }
    // Si el rol no tiene nada que hacer con este pago, se dice en lugar de dejar la tarjeta vacía
    const hayBoton = [...document.querySelectorAll('.acciones-pago .boton')].some(x => !x.hidden);
    const formulario = el('form-espera');
    el('sin-acciones').hidden = hayBoton || !!(formulario && !formulario.hidden);
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

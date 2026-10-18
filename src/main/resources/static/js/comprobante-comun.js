// Piezas comunes de HU-07, HU-08 y HU-09: resultado de la verificación, insignias y utilidades.

const SVG = trazos =>
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + trazos + '</svg>';
const ICONO_OK = SVG('<circle cx="12" cy="12" r="9"/><path d="M8 12.5l2.7 2.7L16 9.5"/>');
const ICONO_FALLA = SVG('<path d="M12 3.5l9 16H3z"/><path d="M12 10v4M12 17h.01"/>');
const ICONO_LAPIZ = SVG('<path d="M4 20l1-4L16.5 4.5a2 2 0 013 3L8 19z"/><path d="M14 7l3 3"/>');
const ICONO_CANDADO = SVG('<rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 018 0v3"/>');
const ICONO_NUBE = SVG('<path d="M7 18a4 4 0 01-.5-7.97A6 6 0 0118 9.5 4.3 4.3 0 0117.5 18z"/><path d="M12 12v6M9.5 14.5L12 12l2.5 2.5"/>');

/** HU-08 criterio 2: "Verificado automáticamente" o "Verificado manualmente". */
function insigniaVerificacion(tipo, etiqueta) {
  const manual = tipo === 'MANUAL';
  return '<span class="insignia-verif ' + (manual ? 'insignia-verif-manual' : 'insignia-verif-auto') + '">' +
    (manual ? ICONO_LAPIZ : ICONO_OK) +
    esc(etiqueta) + '</span>';
}

/** Una fila por regla: ✓ cumple, o ▲ con lo esperado y lo recibido (HU-08 criterios 2 y 3). */
function matrizReglas(reglas) {
  return '<ul class="matriz">' + reglas.map(r =>
    '<li class="matriz-fila ' + (r.cumple ? 'cumple' : 'falla') + '">' +
      '<span class="matriz-icono">' + (r.cumple ? ICONO_OK : ICONO_FALLA) + '</span>' +
      '<div class="matriz-texto">' +
        '<span class="matriz-titulo">' + esc(r.titulo) + '</span>' +
        '<span class="matriz-valor">' + esc(r.recibido) + '</span>' +
        '<small>Esperado: ' + esc(r.esperado) + '</small>' +
        (r.cumple ? '' : '<strong class="matriz-mensaje">' + esc(r.mensaje) + '</strong>') +
      '</div>' +
      '<span class="matriz-sello">' + (r.cumple ? 'Cumple' : 'No cumple') + '</span>' +
    '</li>').join('') + '</ul>';
}

/**
 * Tarjeta con el resultado: "Comprobante verificado" (Pagado) o "Comprobante observado".
 * "acciones" es HTML opcional con los botones de abajo.
 */
function panelResultado(resultado, tipoInsignia, etiquetaInsignia, reglas, acciones) {
  const pagado = resultado === 'PAGADO';
  return '<section class="resultado ' + (pagado ? 'resultado-ok' : 'resultado-obs') + '">' +
    '<header class="resultado-cabecera">' +
      '<span class="resultado-icono">' + (pagado ? ICONO_OK : ICONO_FALLA) + '</span>' +
      '<div>' +
        '<span class="etiqueta-seccion">Verificación automática</span>' +
        '<h2>' + (pagado ? 'Comprobante verificado' : 'Comprobante observado') + '</h2>' +
        '<p class="nota">' + (pagado
          ? 'El pago quedó en estado Pagado.'
          : 'El pago quedó Observado. Revisa lo que falló y sube otro comprobante.') + '</p>' +
      '</div>' +
      (pagado ? insigniaVerificacion(tipoInsignia, etiquetaInsignia) : '') +
    '</header>' +
    matrizReglas(reglas) +
    (acciones ? '<div class="acciones">' + acciones + '</div>' : '') +
  '</section>';
}

/** "1,280.50" · "1280.5" · "1.280,50" · "S/ 85" → número. NaN si no se entiende o tiene más de 2 decimales. */
function parsearMonto(texto) {
  let t = String(texto || '').replace(/S\/\.?/i, '').replace(/\s/g, '');
  if (!t) return NaN;
  const comas = (t.match(/,/g) || []).length, puntos = (t.match(/\./g) || []).length;
  if (comas && puntos) {
    // el último separador es el decimal
    const dec = t.lastIndexOf(',') > t.lastIndexOf('.') ? ',' : '.';
    t = dec === ',' ? t.replace(/\./g, '').replace(',', '.') : t.replace(/,/g, '');
  } else if (comas) {
    t = comas === 1 && /,\d{1,2}$/.test(t) ? t.replace(',', '.') : t.replace(/,/g, '');
  } else if (puntos > 1) {
    t = t.replace(/\./g, '');
  }
  if (!/^\d+(\.\d{1,2})?$/.test(t)) return NaN;
  const n = Number(t);
  return n > 0 && n <= 9999999.99 ? n : NaN;
}

/** 2490368 → "2.4 MB" */
function formatearTamano(bytes) {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return Math.round(bytes / 1024) + ' KB';
  return (bytes / 1024 / 1024).toFixed(1) + ' MB';
}

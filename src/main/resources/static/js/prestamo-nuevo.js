// Registrar un préstamo de capital con su cronograma (pedido del Product Owner).
// El cronograma se escribe a mano o se pega desde Excel; las reglas de prestamo-reglas.js lo revisan en vivo
// y el servidor vuelve a revisarlo al guardar.
(async () => {
  await iniciarPagina('prestamos');
  const el = id => document.getElementById(id);
  const CABECERA = ['entidad', 'nroCredito', 'monto', 'tasa', 'plazo', 'fechaDesembolso'];
  const COLUMNAS = ['vencimiento', 'tipo', 'saldoInicial', 'interes', 'amortizacion', 'cuota', 'saldoFinal'];
  const IMPORTES = ['saldoInicial', 'interes', 'amortizacion', 'cuota', 'saldoFinal'];
  const filas = el('filas');
  let mostrarFaltantes = false;   // los "falta…" se muestran recién al intentar guardar

  // ---------- Formatos que acepta: "S/ 1,528.26", "1528,26", "04/04/2026", "Doble (Jul)" ----------
  function limpiarImporte(v) {
    if (typeof v === 'number' && Number.isFinite(v)) return v.toFixed(2);   // lo que llega leído del archivo
    let t = String(v ?? '').replace(/S\/\s*/i, '').replace(/\s/g, '');
    if (/^\d+,\d{1,2}$/.test(t)) t = t.replace(',', '.');   // coma decimal
    return t.replace(/,/g, '');                              // comas de miles
  }
  function aIso(v) {
    const t = String(v ?? '').trim();
    let m = t.match(/^(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})$/);
    if (m) return m[3] + '-' + m[2].padStart(2, '0') + '-' + m[1].padStart(2, '0');
    m = t.match(/^(\d{4})-(\d{2})-(\d{2})/);
    return m ? m[0] : t;
  }
  const aTipo = v => ReglasPrestamo.tipoNormalizado(v) || '';

  // ---------- Filas del cronograma ----------
  function nuevaFila() {
    const tr = document.createElement('tr');
    tr.innerHTML =
      '<td class="num celda-numero"></td>' +
      '<td><input type="date" data-campo="vencimiento" aria-label="Vencimiento"></td>' +
      '<td><select data-campo="tipo" aria-label="Tipo de cuota"><option value="">—</option><option value="NORMAL">Normal</option><option value="DOBLE">Doble</option></select></td>' +
      IMPORTES.map(c => '<td><input type="text" inputmode="decimal" data-campo="' + c + '" autocomplete="off" aria-label="' + c + '"></td>').join('');
    filas.appendChild(tr);
    numerar();
    return tr;
  }
  function numerar() {
    [...filas.rows].forEach((tr, i) => {
      tr.dataset.fila = i + 1;
      tr.querySelector('.celda-numero').textContent = i + 1;
      tr.querySelectorAll('[data-campo]').forEach(c => c.setAttribute('aria-label', 'Cuota ' + (i + 1) + ': ' + c.dataset.campo));
    });
  }
  const filaVacia = tr => [...tr.querySelectorAll('[data-campo]')].every(c => !c.value);

  /** Deja tantas filas como el plazo; nunca borra filas que ya tienen datos. */
  function ajustarFilas() {
    const n = Number(el('plazo').value);
    if (!/^\d{1,3}$/.test(el('plazo').value.trim()) || n < 1 || n > 360) return;
    while (filas.rows.length < n) nuevaFila();
    while (filas.rows.length > n && filaVacia(filas.rows[filas.rows.length - 1])) filas.deleteRow(-1);
  }

  function ponerFila(tr, valores) {
    COLUMNAS.forEach((campo, k) => {
      if (valores[k] === undefined) return;
      const celda = tr.querySelector('[data-campo="' + campo + '"]');
      celda.value = campo === 'vencimiento' ? aIso(valores[k]) : campo === 'tipo' ? aTipo(valores[k]) : limpiarImporte(valores[k]);
    });
  }

  // Pegar desde Excel: filas separadas por saltos de línea y celdas por tabulaciones.
  // Se acepta con o sin la columna "N.º" y desde la fila donde está el cursor.
  filas.addEventListener('paste', e => {
    const texto = (e.clipboardData || window.clipboardData).getData('text');
    if (!/[\t\n]/.test(texto)) return;   // un solo valor: pegado normal
    e.preventDefault();
    const lineas = texto.replace(/\r/g, '').split('\n').filter(l => l.trim() !== '');
    let tr = e.target.closest('tr');
    lineas.forEach(linea => {
      let celdas = linea.split('\t').map(c => c.trim());
      if (celdas.length >= 8 && /^\d+$/.test(celdas[0])) celdas = celdas.slice(1);
      if (!tr) tr = nuevaFila();
      ponerFila(tr, celdas);
      tr = tr.nextElementSibling;
    });
    revisarPronto();
  });

  el('agregar-fila').addEventListener('click', () => { nuevaFila(); revisarPronto(); });
  el('quitar-fila').addEventListener('click', () => { if (filas.rows.length > 1) filas.deleteRow(-1); revisarPronto(); });
  el('plazo').addEventListener('input', () => {
    el('plazo').value = el('plazo').value.replace(/\D/g, '');
    ajustarFilas();
  });
  ['monto', 'tasa'].forEach(id => el(id).addEventListener('change', () => { el(id).value = limpiarImporte(el(id).value).replace('%', ''); }));
  filas.addEventListener('change', e => {
    if (IMPORTES.includes(e.target.dataset.campo)) e.target.value = limpiarImporte(e.target.value);
  });
  document.querySelector('#formulario').addEventListener('input', revisarPronto);
  document.querySelector('#formulario').addEventListener('change', revisarPronto);

  // ---------- Revisión en vivo ----------
  function leerDatos() {
    const d = {};
    CABECERA.forEach(c => { d[c] = el(c).value.trim(); });
    d.monto = limpiarImporte(d.monto);
    d.tasa = d.tasa.replace('%', '').replace(',', '.').trim();
    d.cuotas = [...filas.rows].map((tr, i) => {
      const f = { numero: i + 1 };
      COLUMNAS.forEach(c => { f[c] = tr.querySelector('[data-campo="' + c + '"]').value.trim(); });
      return f;
    });
    // las filas vacías del final no cuentan (el plazo dirá si faltan)
    while (d.cuotas.length && Object.entries(d.cuotas[d.cuotas.length - 1]).every(([k, v]) => k === 'numero' || v === '')) d.cuotas.pop();
    return d;
  }

  let temporizador = null;
  function revisarPronto() { clearTimeout(temporizador); temporizador = setTimeout(revisar, 250); }

  // Lo que solo sabe el servidor (por ejemplo, un N.º de crédito ya registrado) se mantiene mientras no cambie ese dato
  let delServidor = [];
  function revisar() {
    const propios = ReglasPrestamo.revisar(leerDatos(), hoyIso());
    delServidor = delServidor.filter(p => p.campo !== 'nroCredito' || p.valor === el('nroCredito').value.trim());
    return pintar(delServidor.concat(propios));
  }

  function pintar(problemas) {
    const faltante = p => / falta |^Escribe /.test(p.mensaje) || p.campo === 'filas';
    const visibles = mostrarFaltantes ? problemas : problemas.filter(p => !faltante(p));
    CABECERA.forEach(c => marcarError(c, ''));
    filas.querySelectorAll('.celda-error, .celda-aviso').forEach(c => { c.classList.remove('celda-error', 'celda-aviso'); c.removeAttribute('title'); });

    visibles.forEach(p => {
      if (p.fila === 0) {
        if (p.bloquea && el('error-' + p.campo) && el('error-' + p.campo).hidden) marcarError(p.campo, p.mensaje);
        return;
      }
      const tr = filas.rows[p.fila - 1];
      const celda = tr && tr.querySelector('[data-campo="' + p.campo + '"]');
      if (celda) {
        celda.classList.add(p.bloquea ? 'celda-error' : 'celda-aviso');
        celda.title = p.mensaje;
      }
    });

    const errores = visibles.filter(p => p.bloquea);
    const avisos = visibles.filter(p => !p.bloquea);
    const caja = el('revision');
    const hayDatos = leerDatos().cuotas.length > 0;
    if (!hayDatos && !mostrarFaltantes) { caja.innerHTML = ''; caja.className = 'revision'; return problemas; }
    if (errores.length === 0 && avisos.length === 0) {
      caja.className = 'revision revision-ok';
      caja.innerHTML = '<strong>✓ El cronograma es coherente.</strong> Cada cuota cuadra con su interés y amortización, y el saldo llega a 0.';
      return problemas;
    }
    caja.className = 'revision' + (errores.length ? ' revision-error' : ' revision-aviso');
    const item = p => '<li class="' + (p.bloquea ? 'problema-error' : 'problema-aviso') + '"><button type="button" class="enlace-problema" data-fila="' + p.fila + '" data-campo="' + p.campo + '">' + esc(p.mensaje) + '</button></li>';
    caja.innerHTML =
      '<strong>' + (errores.length ? errores.length + (errores.length === 1 ? ' error' : ' errores') : 'Sin errores') +
      (avisos.length ? ' · ' + avisos.length + (avisos.length === 1 ? ' aviso' : ' avisos') : '') + '</strong>' +
      (errores.length ? ' <span class="nota">Los errores impiden guardar; los avisos solo llaman la atención.</span>' : '') +
      '<ul class="lista-problemas">' + errores.concat(avisos).map(item).join('') + '</ul>';
    caja.querySelectorAll('.enlace-problema').forEach(b => b.addEventListener('click', () => {
      const fila = Number(b.dataset.fila);
      const destino = fila === 0 ? el(b.dataset.campo) : filas.rows[fila - 1] && filas.rows[fila - 1].querySelector('[data-campo="' + b.dataset.campo + '"]');
      if (destino) { destino.scrollIntoView({ block: 'center', behavior: 'smooth' }); destino.focus({ preventScroll: true }); }
    }));
    return problemas;
  }

  // ---------- Guardar ----------
  el('guardar').addEventListener('click', async () => {
    mostrarAviso('');
    mostrarFaltantes = true;
    const problemas = revisar();
    if (problemas.some(p => p.bloquea)) {
      mostrarAviso('El préstamo no se guardó: corrige los errores marcados en rojo.', 'error');
      el('revision').scrollIntoView({ behavior: 'smooth', block: 'center' });
      return;
    }
    const d = leerDatos();
    const numero = v => (v === '' ? null : v);
    const cuerpo = {
      entidad: d.entidad, nroCredito: d.nroCredito, monto: numero(d.monto), tasa: numero(d.tasa),
      plazo: d.plazo === '' ? null : Number(d.plazo), fechaDesembolso: d.fechaDesembolso || null,
      cuotas: d.cuotas.map(c => ({
        numero: c.numero, vencimiento: c.vencimiento || null, tipo: c.tipo || null,
        saldoInicial: numero(c.saldoInicial), interes: numero(c.interes), amortizacion: numero(c.amortizacion),
        cuota: numero(c.cuota), saldoFinal: numero(c.saldoFinal)
      }))
    };
    el('guardar').disabled = true;
    try {
      const r = await api('/api/prestamos', { method: 'POST', body: JSON.stringify(cuerpo) });
      const datos = await r.json().catch(() => ({}));
      if (r.status === 201) { location.href = '/prestamos.html?registrado=1'; return; }
      if (datos.problemas) {
        delServidor = datos.problemas.filter(p => p.campo === 'nroCredito').map(p => Object.assign({ valor: d.nroCredito }, p));
        pintar(datos.problemas);
      }
      mostrarAviso(datos.mensaje || 'No se pudo guardar. Intenta de nuevo.', 'error');
    } catch (e) {
      mostrarAviso('No se pudo guardar. Revisa tu conexión e intenta de nuevo.', 'error');
    } finally {
      el('guardar').disabled = false;
    }
  });

  // Para que la lectura de documentos (cronograma leído) pueda rellenar la pantalla
  window.CronogramaEditor = {
    rellenar(d) {
      ['entidad', 'nroCredito', 'monto', 'tasa', 'plazo', 'fechaDesembolso'].forEach(c => {
        if (d[c] !== null && d[c] !== undefined) el(c).value = c === 'monto' ? limpiarImporte(d[c]) : String(d[c]);
      });
      filas.innerHTML = '';
      (d.cuotas || []).forEach(c => ponerFila(nuevaFila(), COLUMNAS.map(k => c[k] ?? '')));
      ajustarFilas();
      mostrarFaltantes = true;
      revisar();
    }
  };

  // ---------- Leer el cronograma desde un archivo (Excel directo; PDF o foto con IA) ----------
  el('archivo-cronograma').addEventListener('change', async () => {
    const archivo = el('archivo-cronograma').files[0];
    if (!archivo) return;
    marcarError('archivo', '');
    mostrarAviso('');
    el('aviso-leido').hidden = true;
    if (!/\.(xlsx|pdf|jpe?g|png)$/i.test(archivo.name) || archivo.size > 5 * 1024 * 1024) {
      marcarError('archivo', /\.xls$/i.test(archivo.name)
        ? 'Es un Excel antiguo (.xls). Ábrelo y guárdalo como .xlsx para poder leerlo'
        : 'Usa un Excel (.xlsx), PDF, JPG o PNG de hasta 5 MB');
      el('archivo-cronograma').value = '';
      return;
    }
    el('leyendo').hidden = false;
    el('elegir-archivo').classList.add('deshabilitado');
    try {
      const datos = new FormData();
      datos.append('archivo', archivo);
      const r = await api('/api/prestamos/leer', { method: 'POST', body: datos });
      const cuerpo = await r.json().catch(() => ({}));
      if (!r.ok) {
        marcarError('archivo', cuerpo.mensaje || 'No se pudo leer el cronograma. Escribe los datos a mano.');
        return;
      }
      window.CronogramaEditor.rellenar(cuerpo);
      const origen = cuerpo.lector === 'EXCEL' ? 'del Excel' : 'del documento (con IA)';
      el('aviso-leido').innerHTML = '<strong>Datos leídos ' + origen + ': ' + (cuerpo.cuotas || []).length + ' cuotas.</strong> ' +
        'Se copiaron tal cual; revisa lo marcado abajo y completa lo que falte' + (cuerpo.entidad ? '' : ' (por ejemplo, la entidad)') + '.';
      el('aviso-leido').hidden = false;
    } catch (e) {
      marcarError('archivo', 'No se pudo leer el cronograma. Revisa tu conexión o escribe los datos a mano.');
    } finally {
      el('leyendo').hidden = true;
      el('elegir-archivo').classList.remove('deshabilitado');
      el('archivo-cronograma').value = '';
    }
  });

  // Al entrar: 12 filas vacías listas para escribir (se ajustan al plazo)
  for (let i = 0; i < 12; i++) nuevaFila();
})();

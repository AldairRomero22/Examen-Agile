(async () => {
  await iniciarPagina('obligacion');

  const campos = ['concepto', 'categoriaId', 'beneficiarioId', 'monto', 'fechaVencimiento', 'observacion'];
  const el = id => document.getElementById(id);
  let beneficiarios = [];

  // ----- Criterio 2: categorías fijas; criterio 3: beneficiarios registrados -----
  const [rCat, rBen] = await Promise.all([api('/api/categorias'), api('/api/beneficiarios')]);
  const categorias = rCat.ok ? await rCat.json() : [];
  beneficiarios = rBen.ok ? await rBen.json() : [];

  el('categoriaId').innerHTML = '<option value="">Elige una categoría</option>' +
    categorias.map(c => '<option value="' + c.id + '">' + esc(c.nombre) + '</option>').join('');

  // Beneficiarios según la categoría elegida: sin categoría salen todos; con categoría, solo los de ese rubro;
  // si el rubro tiene uno solo, queda elegido.
  function pintarBeneficiarios() {
    const cat = el('categoriaId').value;
    const actual = el('beneficiarioId').value;
    const lista = cat ? beneficiarios.filter(b => String(b.categoriaId) === cat) : beneficiarios;
    el('beneficiarioId').innerHTML =
      (cat && lista.length === 0
        ? '<option value="">No hay beneficiarios en esta categoría</option>'
        : '<option value="">Elige un beneficiario</option>') +
      lista.map(b => '<option value="' + b.id + '">' + esc(b.nombre) + '</option>').join('');
    if (lista.some(b => String(b.id) === actual)) {
      el('beneficiarioId').value = actual;
    } else if (cat && lista.length === 1) {
      el('beneficiarioId').value = String(lista[0].id);
      marcarError('beneficiarioId', '');
    }
    el('ayuda-rubro').hidden = !(cat && lista.length === 0);
    mostrarDatosBeneficiario();
  }
  // Si el beneficiario ya elegido tiene categoría, esa es la categoría de la obligación
  function alinearCategoria() {
    const b = beneficiarios.find(x => String(x.id) === el('beneficiarioId').value);
    if (b && b.categoriaId != null) el('categoriaId').value = String(b.categoriaId);
  }
  el('categoriaId').addEventListener('change', pintarBeneficiarios);
  pintarBeneficiarios();

  // ----- Elegir cómo registrar: a mano o leyendo un documento -----
  const MAXIMO = 5 * 1024 * 1024;
  const pantallas = ['eleccion', 'lectura', 'formulario'];
  function ver(cual) { pantallas.forEach(id => { el(id).hidden = id !== cual; }); }

  el('elegir-mano').addEventListener('click', () => ver('formulario'));
  el('elegir-documento').addEventListener('click', () => { limpiarLectura(); ver('lectura'); });
  el('lectura-volver').addEventListener('click', () => ver('eleccion'));
  el('lectura-mano').addEventListener('click', () => ver('formulario'));
  el('cambiar-modo').addEventListener('click', () => ver('eleccion'));

  // Al registrar al emisor de un documento leído se sale de esta pantalla: lo leído se guarda en esta pestaña
  // (no en el servidor) y se recupera al volver.
  const CLAVE_BORRADOR = 'castillo_borrador_lectura';
  const CAMPOS_BORRADOR = ['concepto', 'categoriaId', 'monto', 'fechaVencimiento', 'observacion'];
  function guardarBorrador() {
    const datos = { campos: {}, leidos: [] };
    CAMPOS_BORRADOR.forEach(id => { datos.campos[id] = el(id).value; });
    document.querySelectorAll('.etiqueta-leido').forEach(m => {
      const f = m.parentElement && m.parentElement.getAttribute('for');
      if (f) datos.leidos.push(f);
    });
    try { sessionStorage.setItem(CLAVE_BORRADOR, JSON.stringify(datos)); } catch (e) { /* sin almacenamiento */ }
  }
  function restaurarBorrador() {
    let datos = null;
    try {
      datos = JSON.parse(sessionStorage.getItem(CLAVE_BORRADOR) || 'null');
      sessionStorage.removeItem(CLAVE_BORRADOR);
    } catch (e) { datos = null; }
    if (!datos) return;
    CAMPOS_BORRADOR.forEach(id => { if (datos.campos[id] != null) el(id).value = datos.campos[id]; });
    el('contador').textContent = el('observacion').value.length;
    const f = el('fechaVencimiento').value;
    el('ayuda-vencida').hidden = !(f && f < hoyIso());
    (datos.leidos || []).forEach(id => { if (el(id)) marcarLeido(id); });
    el('aviso-leido').hidden = false;
  }

  let emisorPendiente = null;
  el('registrar-emisor').addEventListener('click', () => {
    if (!emisorPendiente) return;
    guardarBorrador();
    const q = new URLSearchParams({ volver: 'obligacion', nombre: emisorPendiente.nombre, documento: emisorPendiente.documento });
    if (el('categoriaId').value) q.set('categoriaId', el('categoriaId').value);
    window.location.href = '/beneficiario-nuevo.html?' + q.toString();
  });
  el('beneficiarioId').addEventListener('change', () => { if (el('beneficiarioId').value) el('beneficiario-nuevo').hidden = true; });

  function limpiarLectura() {
    marcarError('archivo', '');
    el('fallo-lectura').hidden = true;
    el('leyendo').hidden = true;
    el('archivo').value = '';
  }

  // Marca "Leído del documento" junto a la etiqueta del campo; desaparece si el asistente lo corrige
  function marcarLeido(id) {
    const campo = el(id);
    const etiqueta = document.querySelector('label[for="' + id + '"]');
    if (!etiqueta || etiqueta.querySelector('.etiqueta-leido')) return;
    const marca = document.createElement('span');
    marca.className = 'etiqueta-leido';
    marca.textContent = 'Leído del documento';
    etiqueta.appendChild(marca);
    const quitar = () => marca.remove();
    campo.addEventListener('input', quitar, { once: true });
    campo.addEventListener('change', quitar, { once: true });
  }
  function quitarMarcasLeido() {
    document.querySelectorAll('.etiqueta-leido').forEach(m => m.remove());
    el('aviso-leido').hidden = true;
    el('beneficiario-nuevo').hidden = true;
  }

  const zona = el('zona');
  ['dragenter', 'dragover'].forEach(ev => zona.addEventListener(ev, e => { e.preventDefault(); zona.classList.add('arrastrando'); }));
  ['dragleave', 'drop'].forEach(ev => zona.addEventListener(ev, e => { e.preventDefault(); zona.classList.remove('arrastrando'); }));
  zona.addEventListener('drop', e => { if (e.dataTransfer.files[0]) leerDocumento(e.dataTransfer.files[0]); });
  el('archivo').addEventListener('change', () => { if (el('archivo').files[0]) leerDocumento(el('archivo').files[0]); });

  async function leerDocumento(archivo) {
    mostrarAviso('');
    marcarError('archivo', '');
    el('fallo-lectura').hidden = true;
    const nombre = (archivo.name || '').toLowerCase();
    if (!/\.(jpe?g|png|pdf)$/.test(nombre) || archivo.size > MAXIMO) {
      marcarError('archivo', 'Formato o tamaño no permitido. Usa JPG, PNG o PDF de hasta 5 MB');
      return;
    }
    el('leyendo').hidden = false;
    zona.hidden = true;
    try {
      const datos = new FormData();
      datos.append('archivo', archivo);
      const r = await api('/api/documentos/leer', { method: 'POST', body: datos });
      const cuerpo = await r.json().catch(() => ({}));
      if (!r.ok) {
        if (cuerpo.campo === 'archivo') {
          marcarError('archivo', cuerpo.mensaje);
        } else {
          fallo(cuerpo.mensaje || 'No pudimos leer el documento.');
        }
        return;
      }
      if (cuerpo.monto == null && !cuerpo.fechaVencimiento && cuerpo.beneficiarioId == null) {
        fallo('No pudimos leer el documento. Prueba con una foto más clara o escribe los datos a mano.');
        return;
      }
      rellenar(cuerpo);
      ver('formulario');
    } catch (e) {
      fallo('No pudimos leer el documento. Revisa tu conexión o escribe los datos a mano.');
    } finally {
      el('leyendo').hidden = true;
      zona.hidden = false;
      el('archivo').value = '';
    }
  }

  function fallo(mensaje) {
    const caja = el('fallo-lectura');
    caja.textContent = mensaje + ' Puedes elegir “Escribir a mano”.';
    caja.hidden = false;
  }

  function rellenar(d) {
    el('formulario').reset();
    quitarMarcasLeido();
    el('datos-beneficiario').hidden = true;
    el('contador').textContent = '0';
    const poner = (id, valor) => { el(id).value = valor; marcarLeido(id); };
    if (d.monto != null) poner('monto', Number(d.monto).toFixed(2));
    if (d.fechaVencimiento) {
      poner('fechaVencimiento', d.fechaVencimiento);
      el('ayuda-vencida').hidden = !(d.fechaVencimiento < hoyIso());
    }
    if (d.beneficiarioId != null && beneficiarios.some(b => b.id === d.beneficiarioId)) {
      poner('beneficiarioId', String(d.beneficiarioId));
      mostrarDatosBeneficiario();
    }
    // categoría: la del beneficiario (su rubro) o, si no tiene, la de su última obligación / la que sugiere el documento
    const benLeido = beneficiarios.find(b => b.id === d.beneficiarioId);
    const catId = benLeido && benLeido.categoriaId != null ? benLeido.categoriaId : d.categoriaId;
    if (catId != null && categorias.some(c => c.id === catId)) poner('categoriaId', String(catId));
    pintarBeneficiarios();
    if (d.concepto) poner('concepto', d.concepto);
    // Emisor leído que no está registrado: se ofrece registrarlo con su nombre y RUC ya puestos
    if (d.beneficiarioId == null && (d.emisorNombre || d.emisorDocumento)) {
      emisorPendiente = { nombre: d.emisorNombre || '', documento: d.emisorDocumento || '' };
      el('beneficiario-nuevo-texto').innerHTML =
        '<strong>Beneficiario no registrado:</strong> ' + esc(emisorPendiente.nombre || 'sin nombre') +
        (emisorPendiente.documento ? ' (' + (emisorPendiente.documento.length === 11 ? 'RUC' : 'DNI') + ' ' + esc(emisorPendiente.documento) + ')' : '') +
        '<br><small class="nota">Regístralo una vez (te faltará su banco y cuenta) y vuelves aquí con todo lo leído.</small>';
      el('beneficiario-nuevo').hidden = false;
    }
    el('aviso-leido').hidden = false;
    // el documento ya está registrado: se avisa de entrada (el servidor igual lo rechazaría al guardar)
    if (d.avisoDuplicada) mostrarAviso(d.avisoDuplicada, 'error');
  }

  // Criterio 3: al elegir un beneficiario se muestran su documento, banco y cuenta o CCI
  el('beneficiarioId').addEventListener('change', () => {
    if (el('beneficiarioId').value && !el('categoriaId').value) alinearCategoria();
    mostrarDatosBeneficiario();
  });
  function mostrarDatosBeneficiario() {
    const caja = el('datos-beneficiario');
    const b = beneficiarios.find(x => String(x.id) === el('beneficiarioId').value);
    if (!b) {
      caja.hidden = true;
      return;
    }
    caja.innerHTML =
      '<div><strong>' + esc(b.tipoDocumento) + ':</strong> ' + esc(b.documento) + '</div>' +
      '<div><strong>Banco:</strong> ' + esc(b.banco) + '</div>' +
      '<div><strong>Cuenta o CCI:</strong> ' + esc(b.cuenta) + '</div>';
    caja.hidden = false;
  }

  // El calendario solo permite fechas razonables (hasta 2 años atrás y 5 adelante)
  const rango = Reglas.rangoVencimiento(hoyIso());
  el('fechaVencimiento').min = rango.min;
  el('fechaVencimiento').max = rango.max;
  // Monto: se acepta la coma como decimal (1280,50) y se descartan letras y símbolos
  el('monto').addEventListener('input', () => {
    el('monto').value = el('monto').value.replace(',', '.').replace(/[^\d.]/g, '').replace(/(\..*)\./g, '$1');
  });

  // Criterio 4: si la fecha ya pasó, se avisa que quedará como Vencido
  el('fechaVencimiento').addEventListener('change', () => {
    const f = el('fechaVencimiento').value;
    el('ayuda-vencida').hidden = !(f && f < hoyIso());
  });

  el('observacion').addEventListener('input', () => {
    el('contador').textContent = el('observacion').value.length;
  });

  // Si se vuelve de registrar un beneficiario nuevo, queda elegido y se va directo al formulario
  const preseleccion = new URLSearchParams(window.location.search).get('beneficiario');
  if (preseleccion) {
    restaurarBorrador();
    // el beneficiario recién registrado manda: su categoría es la de la obligación
    const nuevo = beneficiarios.find(b => String(b.id) === preseleccion);
    if (nuevo && nuevo.categoriaId != null) el('categoriaId').value = String(nuevo.categoriaId);
    pintarBeneficiarios();
    el('beneficiarioId').value = preseleccion;
    mostrarDatosBeneficiario();
    ver('formulario');
  } else {
    ver('eleccion');
  }

  el('guardar').addEventListener('click', () => guardar(false));
  el('guardar-otra').addEventListener('click', () => guardar(true));

  async function guardar(registrarOtra) {
    mostrarAviso('');
    campos.forEach(id => marcarError(id, ''));

    // ----- Criterio 1 y 4: validación antes de enviar -----
    let hayErrores = false;
    const pedir = id => {
      if (el(id).value.trim() === '') {
        marcarError(id, 'Obligatorio');
        hayErrores = true;
      }
    };
    ['concepto', 'categoriaId', 'beneficiarioId', 'monto', 'fechaVencimiento'].forEach(pedir);
    const conceptoMal = el('concepto').value.trim() !== '' && Reglas.concepto(el('concepto').value);
    if (conceptoMal) { marcarError('concepto', conceptoMal); hayErrores = true; }
    const fechaMal = el('fechaVencimiento').value !== '' && Reglas.vencimiento(el('fechaVencimiento').value, hoyIso());
    if (fechaMal) { marcarError('fechaVencimiento', fechaMal); hayErrores = true; }

    const monto = el('monto').value.trim().replace(/\s/g, '');
    if (monto !== '') {
      if (!/^\d{1,8}(\.\d{1,2})?$/.test(monto)) {
        marcarError('monto', 'Ingresa un monto en soles con hasta 2 decimales, por ejemplo 1280.50');
        hayErrores = true;
      } else if (Number(monto) <= 0) {
        marcarError('monto', 'El monto debe ser mayor que 0');
        hayErrores = true;
      } else if (Number(monto) > 99999999.99) {
        marcarError('monto', 'El monto máximo es S/ 99,999,999.99');
        hayErrores = true;
      }
    }
    if (hayErrores) return;

    const botones = [el('guardar'), el('guardar-otra')];
    botones.forEach(b => { b.disabled = true; });
    try {
      const r = await api('/api/obligaciones', {
        method: 'POST',
        body: JSON.stringify({
          concepto: el('concepto').value.trim(),
          categoriaId: Number(el('categoriaId').value),
          beneficiarioId: Number(el('beneficiarioId').value),
          monto: monto,
          fechaVencimiento: el('fechaVencimiento').value,
          observacion: el('observacion').value.trim() || null
        })
      });
      const datos = await r.json().catch(() => ({}));

      if (r.status === 201) {
        if (registrarOtra) {
          // Criterio 6: formulario vacío y listo para el siguiente gasto
          el('formulario').reset();
          quitarMarcasLeido();
          ver('eleccion');
          el('datos-beneficiario').hidden = true;
          el('ayuda-vencida').hidden = true;
          el('contador').textContent = '0';
          mostrarAviso('Obligación registrada: ' + datos.concepto + ' (' + datos.estadoEtiqueta + ')', 'ok');
        } else {
          // Criterio 5: aparece en "Pagos del mes"
          window.location.href = '/pagos.html?registrado=1&mes=' + datos.fechaVencimiento.slice(0, 7);
        }
        return;
      }
      if (datos.errores) {
        Object.entries(datos.errores).forEach(([campo, mensaje]) => marcarError(campo, mensaje));
      } else if (datos.campo) {
        marcarError(datos.campo, datos.mensaje);
      } else {
        mostrarAviso(datos.mensaje || 'No se pudo guardar. Intenta de nuevo.', 'error');
        el('aviso').scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    } catch (e) {
      mostrarAviso('No se pudo guardar. Revisa tu conexión e intenta de nuevo.', 'error');
    } finally {
      botones.forEach(b => { b.disabled = false; });
    }
  }
})();

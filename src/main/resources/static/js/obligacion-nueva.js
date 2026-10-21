(async () => {
  await iniciarPagina('obligacion');

  const campos = ['concepto', 'categoriaId', 'beneficiario', 'ruc', 'monto', 'fechaVencimiento', 'observacion'];
  const el = id => document.getElementById(id);

  // ----- Criterio 2: categorías fijas. "A quién se paga" se escribe; se sugieren los nombres a los que ya se pagó -----
  const [rCat, rDest] = await Promise.all([api('/api/categorias'), api('/api/obligaciones/destinatarios')]);
  const categorias = rCat.ok ? await rCat.json() : [];
  // [{nombre, ruc}]: a quién ya se le pagó y su RUC o DNI (ruc null si se registró antes de pedirlo)
  const conocidos = rDest.ok ? await rDest.json() : [];
  const destinatarios = conocidos.map(d => d.nombre);

  el('categoriaId').innerHTML = '<option value="">Elige una categoría</option>' +
    categorias.map(c => '<option value="' + c.id + '">' + esc(c.nombre) + '</option>').join('');
  el('lista-destinatarios').innerHTML = destinatarios.map(n => '<option value="' + esc(n) + '"></option>').join('');

  // ----- RUC o DNI: un RUC es de una sola empresa y una empresa tiene un solo RUC -----
  const rucDe = nombre => {
    const k = Reglas.empresa(nombre);
    const d = conocidos.find(x => x.ruc && Reglas.empresa(x.nombre) === k);
    return d ? d.ruc : null;
  };
  const duenoDe = ruc => conocidos.find(x => x.ruc === ruc) || null;

  /** Revisa el RUC escrito contra lo ya registrado y avisa en el campo (el servidor hace lo mismo al guardar). */
  function revisarRuc() {
    const ruc = el('ruc').value.trim();
    const nombre = el('beneficiario').value.trim();
    marcarError('ruc', '');
    if (!/^(\d{8}|\d{11})$/.test(ruc)) return null;
    const formato = Reglas.documento(ruc);
    if (formato) { marcarError('ruc', formato); return formato; }
    const dueno = duenoDe(ruc);
    if (dueno && nombre && Reglas.empresa(dueno.nombre) !== Reglas.empresa(nombre)) {
      const msg = 'Este ' + (ruc.length === 11 ? 'RUC' : 'DNI') + ' ya está registrado para «' + dueno.nombre + '».';
      marcarError('ruc', msg);
      return msg;
    }
    const suyo = nombre ? rucDe(nombre) : null;
    if (suyo && suyo !== ruc) {
      const msg = '«' + nombre + '» ya está registrada con el ' + (suyo.length === 11 ? 'RUC' : 'DNI') + ' ' + suyo + '.';
      marcarError('ruc', msg);
      return msg;
    }
    return null;
  }

  /** Al elegir una empresa conocida se completa su RUC; al escribir un RUC conocido, su nombre. */
  function completarRuc() {
    const suyo = rucDe(el('beneficiario').value);
    if (suyo && !el('ruc').value.trim()) el('ruc').value = suyo;
    revisarRuc();
  }
  el('ruc').addEventListener('input', () => {
    el('ruc').value = el('ruc').value.replace(/\D/g, '').slice(0, 11);
    const dueno = duenoDe(el('ruc').value);
    if (dueno && !el('beneficiario').value.trim()) el('beneficiario').value = dueno.nombre;
    revisarRuc();
  });
  el('beneficiario').addEventListener('change', completarRuc);

  // ----- Nombre parecido a uno ya registrado: solo se avisa ("¿Quisiste decir…?"), el asistente decide -----
  function revisarNombre() {
    const parecido = Reglas.nombreParecido(el('beneficiario').value, destinatarios);
    el('sugerencia-nombre').hidden = !parecido;
    if (parecido) el('sugerencia-texto').textContent = parecido;
  }
  let espera = null;
  el('beneficiario').addEventListener('input', () => { clearTimeout(espera); espera = setTimeout(revisarNombre, 350); });
  el('usar-sugerencia').addEventListener('click', () => {
    el('beneficiario').value = el('sugerencia-texto').textContent;
    el('beneficiario').dispatchEvent(new Event('change'));
    completarRuc();
    el('sugerencia-nombre').hidden = true;
    el('beneficiario').focus();
  });

  // ----- Concepto y categoría que no cuadran: solo se avisa, el asistente decide -----
  const nombreCategoria = () => { const c = categorias.find(x => String(x.id) === el('categoriaId').value); return c ? c.nombre : null; };
  function revisarCategoria() {
    const nombre = nombreCategoria();
    const aviso = Reglas.avisoCategoria(el('concepto').value, nombre);
    el('aviso-categoria').hidden = !aviso;
    if (aviso) {
      el('aviso-categoria-texto').textContent = aviso.mensaje;
      const unica = aviso.sugeridas.length === 1 ? categorias.find(c => c.nombre === aviso.sugeridas[0]) : null;
      el('usar-categoria').hidden = !unica;
      el('usar-categoria').dataset.id = unica ? unica.id : '';
    }
    // los préstamos con cronograma tienen su propio apartado (no se repite si ya hay otro aviso)
    el('aviso-prestamo').hidden = !(nombre === 'Préstamos de capital' && !aviso);
  }
  let esperaCategoria = null;
  el('concepto').addEventListener('input', () => { clearTimeout(esperaCategoria); esperaCategoria = setTimeout(revisarCategoria, 350); });
  el('categoriaId').addEventListener('change', revisarCategoria);
  el('usar-categoria').addEventListener('click', () => {
    el('categoriaId').value = el('usar-categoria').dataset.id;
    revisarCategoria();
  });

  // ----- Elegir cómo registrar: a mano o leyendo un documento -----
  const MAXIMO = 5 * 1024 * 1024;
  const pantallas = ['eleccion', 'lectura', 'formulario'];
  function ver(cual) { pantallas.forEach(id => { el(id).hidden = id !== cual; }); }

  el('elegir-mano').addEventListener('click', () => ver('formulario'));
  el('elegir-documento').addEventListener('click', () => { limpiarLectura(); ver('lectura'); });
  el('lectura-volver').addEventListener('click', () => ver('eleccion'));
  el('lectura-mano').addEventListener('click', () => ver('formulario'));
  el('cambiar-modo').addEventListener('click', () => ver('eleccion'));

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
      if (cuerpo.monto == null && !cuerpo.fechaVencimiento && !cuerpo.beneficiario) {
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
    el('contador').textContent = '0';
    const poner = (id, valor) => { el(id).value = valor; marcarLeido(id); };
    if (d.monto != null) poner('monto', Number(d.monto).toFixed(2));
    if (d.fechaVencimiento) {
      poner('fechaVencimiento', d.fechaVencimiento);
      el('ayuda-vencida').hidden = !(d.fechaVencimiento < hoyIso());
    }
    if (d.beneficiario) poner('beneficiario', d.beneficiario);
    if (d.ruc) poner('ruc', d.ruc);
    revisarNombre();
    completarRuc();
    if (d.categoriaId != null && categorias.some(c => c.id === d.categoriaId)) poner('categoriaId', String(d.categoriaId));
    if (d.concepto) poner('concepto', d.concepto);
    revisarCategoria();
    el('aviso-leido').hidden = false;
    // el documento ya está registrado: se avisa de entrada (el servidor igual lo rechazaría al guardar)
    if (d.avisoDuplicada) mostrarAviso(d.avisoDuplicada, 'error');
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

  ver('eleccion');

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
    ['concepto', 'categoriaId', 'beneficiario', 'ruc', 'monto', 'fechaVencimiento'].forEach(pedir);
    const conceptoMal = el('concepto').value.trim() !== '' && Reglas.concepto(el('concepto').value);
    if (conceptoMal) { marcarError('concepto', conceptoMal); hayErrores = true; }
    const aQuienMal = el('beneficiario').value.trim() !== '' && Reglas.nombre(el('beneficiario').value);
    if (aQuienMal) { marcarError('beneficiario', aQuienMal); hayErrores = true; }
    if (el('ruc').value.trim() !== '') {
      const rucMal = Reglas.documento(el('ruc').value) || revisarRuc();
      if (rucMal) { marcarError('ruc', rucMal); hayErrores = true; }
    }
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
          beneficiario: el('beneficiario').value.trim().replace(/\s+/g, ' '),
          ruc: el('ruc').value.trim(),
          monto: monto,
          fechaVencimiento: el('fechaVencimiento').value,
          observacion: el('observacion').value.trim() || null
        })
      });
      const datos = await r.json().catch(() => ({}));

      if (r.status === 201) {
        // el nombre nuevo queda sugerido para el siguiente registro
        if (datos.beneficiario && !destinatarios.includes(datos.beneficiario)) {
          destinatarios.push(datos.beneficiario);
          conocidos.push({ nombre: datos.beneficiario, ruc: datos.ruc || null });
          el('lista-destinatarios').insertAdjacentHTML('beforeend', '<option value="' + esc(datos.beneficiario) + '"></option>');
        }
        if (registrarOtra) {
          // Criterio 6: formulario vacío y listo para el siguiente gasto
          el('formulario').reset();
          quitarMarcasLeido();
          el('sugerencia-nombre').hidden = true;
          el('aviso-categoria').hidden = true;
          el('aviso-prestamo').hidden = true;
          ver('eleccion');
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

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
  el('beneficiarioId').innerHTML = '<option value="">Elige un beneficiario</option>' +
    beneficiarios.map(b => '<option value="' + b.id + '">' + esc(b.nombre) + '</option>').join('');

  // Si se vuelve de registrar un beneficiario nuevo, queda elegido
  const preseleccion = new URLSearchParams(window.location.search).get('beneficiario');
  if (preseleccion) {
    el('beneficiarioId').value = preseleccion;
    mostrarDatosBeneficiario();
  }

  // Criterio 3: al elegir un beneficiario se muestran su documento, banco y cuenta o CCI
  el('beneficiarioId').addEventListener('change', mostrarDatosBeneficiario);
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

  // Criterio 4: si la fecha ya pasó, se avisa que quedará como Vencido
  el('fechaVencimiento').addEventListener('change', () => {
    const f = el('fechaVencimiento').value;
    el('ayuda-vencida').hidden = !(f && f < hoyIso());
  });

  el('observacion').addEventListener('input', () => {
    el('contador').textContent = el('observacion').value.length;
  });

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

    const monto = el('monto').value.trim().replace(/\s/g, '');
    if (monto !== '') {
      if (!/^\d{1,10}(\.\d{1,2})?$/.test(monto)) {
        marcarError('monto', 'Ingresa un monto en soles con hasta 2 decimales, por ejemplo 1280.50');
        hayErrores = true;
      } else if (Number(monto) <= 0) {
        marcarError('monto', 'El monto debe ser mayor que 0');
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
          el('datos-beneficiario').hidden = true;
          el('ayuda-vencida').hidden = true;
          el('contador').textContent = '0';
          mostrarAviso('Obligación registrada: ' + datos.concepto + ' (' + datos.estadoEtiqueta + ')', 'ok');
          el('concepto').focus();
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
      }
    } catch (e) {
      mostrarAviso('No se pudo guardar. Revisa tu conexión e intenta de nuevo.', 'error');
    } finally {
      botones.forEach(b => { b.disabled = false; });
    }
  }
})();

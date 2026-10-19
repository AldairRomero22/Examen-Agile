(async () => {
  await iniciarPagina('beneficiarios');

  // Si se llegó desde "Registrar un beneficiario nuevo" de Nueva obligación, se vuelve allá al guardar.
  const volverAObligacion = new URLSearchParams(window.location.search).get('volver') === 'obligacion';
  if (volverAObligacion) {
    document.getElementById('enlace-volver').textContent = '← Volver a Nueva obligación';
    document.getElementById('enlace-volver').href = '/obligacion-nueva.html';
    document.getElementById('cancelar').href = '/obligacion-nueva.html';
  }

  // Datos leídos de un documento (nombre y RUC del emisor): se prellenan y el asistente los revisa
  const consulta = new URLSearchParams(window.location.search);
  if (consulta.get('nombre')) document.getElementById('nombre').value = consulta.get('nombre').slice(0, 150);
  if (consulta.get('documento')) document.getElementById('documento').value = consulta.get('documento').replace(/\D/g, '').slice(0, 11);

  const campos = ['nombre', 'documento', 'categoriaId', 'banco', 'cuenta'];

  // Categoría (rubro) del beneficiario: en Nueva obligación, al elegir una categoría salen solo los de ese rubro
  const rCat = await api('/api/categorias');
  const listaCategorias = rCat.ok ? await rCat.json() : [];
  document.getElementById('categoriaId').innerHTML = '<option value="">Elige una categoría</option>' +
    listaCategorias.map(c => '<option value="' + c.id + '">' + esc(c.nombre) + '</option>').join('');
  const categoriaPrevia = consulta.get('categoriaId');
  if (categoriaPrevia && listaCategorias.some(c => String(c.id) === categoriaPrevia)) {
    document.getElementById('categoriaId').value = categoriaPrevia;
  }
  const valor = id => document.getElementById(id).value.trim();
  // El banco se elige de una lista; con «Otro…» se escribe
  const bancoOtro = document.getElementById('banco-otro');
  const banco = () => document.getElementById('banco').value === '__otro' ? bancoOtro.value.trim() : valor('banco');
  document.getElementById('banco').addEventListener('change', () => {
    bancoOtro.hidden = document.getElementById('banco').value !== '__otro';
    if (!bancoOtro.hidden) bancoOtro.focus();
  });
  // Cuenta o CCI: solo se escriben números (se quitan guiones, espacios y letras al pegar)
  const cuenta = document.getElementById('cuenta');
  cuenta.addEventListener('input', () => { cuenta.value = cuenta.value.replace(/\D/g, '').slice(0, 20); });

  // Criterio 2: el documento acepta solo números
  const documento = document.getElementById('documento');
  documento.addEventListener('input', () => {
    documento.value = documento.value.replace(/\D/g, '');
  });

  document.getElementById('formulario').addEventListener('submit', async evento => {
    evento.preventDefault();
    mostrarAviso('');
    campos.forEach(id => marcarError(id, ''));

    // Criterios 1 y 2: validación antes de enviar
    let hayErrores = false;
    const doc = valor('documento');
    const comprobar = (id, texto, regla) => {
      const mensaje = texto === '' ? 'Obligatorio' : regla(texto);
      if (mensaje) { marcarError(id, mensaje); hayErrores = true; }
    };
    comprobar('nombre', valor('nombre'), Reglas.nombre);
    comprobar('documento', doc, Reglas.documento);
    if (valor('categoriaId') === '') {
      marcarError('categoriaId', 'Obligatorio');
      hayErrores = true;
    }
    if (valor('banco') === '') {
      marcarError('banco', 'Obligatorio');
      hayErrores = true;
    } else if (Reglas.banco(banco())) {
      marcarError('banco', Reglas.banco(banco()) === 'Elige el banco' ? 'Escribe el nombre del banco' : Reglas.banco(banco()));
      hayErrores = true;
    }
    comprobar('cuenta', valor('cuenta'), Reglas.cuenta);
    if (hayErrores) return;

    const boton = document.getElementById('guardar');
    boton.disabled = true;
    try {
      const r = await api('/api/beneficiarios', {
        method: 'POST',
        body: JSON.stringify({
          nombre: valor('nombre'), documento: doc, categoriaId: Number(valor('categoriaId')), banco: banco(), cuenta: valor('cuenta')
        })
      });
      const datos = await r.json().catch(() => ({}));

      if (r.status === 201) {
        // Criterio 3: aparece en la lista y en el selector de Nueva obligación
        window.location.href = volverAObligacion
          ? '/obligacion-nueva.html?beneficiario=' + datos.id
          : '/beneficiarios.html';
        return;
      }
      if (datos.errores) {
        Object.entries(datos.errores).forEach(([campo, mensaje]) => marcarError(campo, mensaje));
      } else if (datos.campo) {
        marcarError(datos.campo, datos.mensaje);      // p. ej. "Este documento ya está registrado"
      } else {
        mostrarAviso(datos.mensaje || 'No se pudo guardar. Intenta de nuevo.', 'error');
      }
    } catch (e) {
      mostrarAviso('No se pudo guardar. Revisa tu conexión e intenta de nuevo.', 'error');
    } finally {
      boton.disabled = false;
    }
  });
})();

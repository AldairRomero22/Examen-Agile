(async () => {
  await iniciarPagina('beneficiarios');

  // Si se llegó desde "Registrar un beneficiario nuevo" de Nueva obligación, se vuelve allá al guardar.
  const volverAObligacion = new URLSearchParams(window.location.search).get('volver') === 'obligacion';
  if (volverAObligacion) {
    document.getElementById('enlace-volver').textContent = '← Volver a Nueva obligación';
    document.getElementById('enlace-volver').href = '/obligacion-nueva.html';
    document.getElementById('cancelar').href = '/obligacion-nueva.html';
  }

  const campos = ['nombre', 'documento', 'banco', 'cuenta'];
  const valor = id => document.getElementById(id).value.trim();

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
    campos.forEach(id => {
      if (valor(id) === '') {
        marcarError(id, 'Obligatorio');
        hayErrores = true;
      }
    });
    const doc = valor('documento');
    if (doc !== '' && doc.length !== 8 && doc.length !== 11) {
      marcarError('documento', 'Debe tener 8 dígitos (DNI) u 11 (RUC), solo números');
      hayErrores = true;
    }
    if (hayErrores) return;

    const boton = document.getElementById('guardar');
    boton.disabled = true;
    try {
      const r = await api('/api/beneficiarios', {
        method: 'POST',
        body: JSON.stringify({
          nombre: valor('nombre'), documento: doc, banco: valor('banco'), cuenta: valor('cuenta')
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

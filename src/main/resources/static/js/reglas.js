// Mismas reglas que el servidor (pe.castillo.config.Reglas): cada función devuelve el mensaje de error o null.
const Reglas = (() => {
  const LETRA = /\p{L}/u;
  const NOMBRE = /^[\p{L}\p{N}][\p{L}\p{N} .,&'’()/\-ªº°]*$/u;
  const BANCO = /^[\p{L}\p{N}][\p{L}\p{N} .,&'’()/\-]*$/u;
  const PESOS = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];

  function nombre(v) {
    v = (v || '').trim();
    if (v.length < 3) return 'Escribe el nombre completo (mínimo 3 caracteres)';
    if (!LETRA.test(v)) return 'El nombre debe tener letras, no solo números o símbolos';
    if (!NOMBRE.test(v)) return 'El nombre tiene caracteres no permitidos';
    return null;
  }

  function rucValido(r) {
    if (!/^\d{11}$/.test(r)) return false;
    if (!['10', '15', '16', '17', '20'].includes(r.slice(0, 2))) return false;
    let suma = 0;
    for (let i = 0; i < 10; i++) suma += Number(r[i]) * PESOS[i];
    let d = 11 - (suma % 11);
    if (d === 10) d = 0; else if (d === 11) d = 1;
    return d === Number(r[10]);
  }

  function documento(v) {
    v = (v || '').trim();
    if (!/^(\d{8}|\d{11})$/.test(v)) return 'Debe tener 8 dígitos (DNI) u 11 (RUC), solo números';
    if (v.length === 8) return /^(\d)\1{7}$/.test(v) ? 'El DNI no es válido' : null;
    return rucValido(v) ? null : 'El RUC no es válido: revisa los números (debe empezar con 10, 15, 16, 17 o 20)';
  }

  function banco(v) {
    v = (v || '').trim();
    if (v.length < 2) return 'Elige el banco';
    if (!LETRA.test(v) || !BANCO.test(v)) return 'Escribe el nombre del banco con letras';
    return null;
  }

  function cuenta(v) {
    return /^\d{10,20}$/.test((v || '').trim()) ? null : 'Solo números, de 10 a 20 dígitos (sin guiones ni espacios)';
  }

  function concepto(v) {
    v = (v || '').trim();
    if (v.length < 3) return 'Describe el concepto (mínimo 3 caracteres)';
    if (!LETRA.test(v)) return 'El concepto debe tener letras, no solo números o símbolos';
    // eslint-disable-next-line no-control-regex
    if (/[\u0000-\u001f\u007f]/.test(v)) return 'El concepto tiene caracteres no permitidos';
    return null;
  }

  function correo(v) {
    v = (v || '').trim();
    if (v.length > 150 || !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(v)) return 'Ingresa un correo válido, por ejemplo nombre@empresa.com';
    return null;
  }

  function sumarAnios(iso, n) {
    const [a, m, d] = iso.split('-').map(Number);
    const f = new Date(a + n, m - 1, d);
    return f.getFullYear() + '-' + String(f.getMonth() + 1).padStart(2, '0') + '-' + String(f.getDate()).padStart(2, '0');
  }
  const dmy = iso => iso.slice(8) + '/' + iso.slice(5, 7) + '/' + iso.slice(0, 4);

  /** Rango del vencimiento: hasta 2 años atrás y 5 adelante. hoy = 'AAAA-MM-DD'. */
  function rangoVencimiento(hoy) { return { min: sumarAnios(hoy, -2), max: sumarAnios(hoy, 5) }; }

  function vencimiento(f, hoy) {
    const { min, max } = rangoVencimiento(hoy);
    if (f < min) return 'La fecha no puede ser anterior al ' + dmy(min);
    if (f > max) return 'La fecha no puede ser posterior al ' + dmy(max);
    return null;
  }


  /** Como Reglas.empresa del servidor: sin tildes, mayúsculas, signos ni S.A./S.A.C./S.R.L./E.I.R.L./Perú al final. */
  function empresa(v) {
    const c = String(v || '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()
      .replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
    const sin = c.replace(/(\s+(s a a|s a c|s r l|e i r l|s a|sac|saa|srl|eirl|sa|pe|peru|del peru))+$/, '').trim();
    return sin || c;
  }

  function distancia(a, b) {
    const fila = Array.from({ length: b.length + 1 }, (_, j) => j);
    for (let i = 1; i <= a.length; i++) {
      let previo = fila[0];
      fila[0] = i;
      for (let j = 1; j <= b.length; j++) {
        const guardado = fila[j];
        fila[j] = Math.min(fila[j] + 1, fila[j - 1] + 1, previo + (a[i - 1] === b[j - 1] ? 0 : 1));
        previo = guardado;
      }
    }
    return fila[b.length];
  }

  /**
   * Nombre ya registrado que parece ser la misma empresa que "escrito" (otra forma de escribirla o un error de tipeo),
   * o null. Solo es un aviso: el asistente decide si usarlo.
   */
  function nombreParecido(escrito, conocidos) {
    const texto = String(escrito || '').trim().replace(/\s+/g, ' ');
    const k = empresa(texto);
    if (k.length < 3 || (conocidos || []).includes(texto)) return null;
    let mejor = null, puntaje = 0;
    (conocidos || []).forEach(n => {
      const kn = empresa(n);
      if (!kn) return;
      const p = kn === k ? 1 : 1 - distancia(k, kn) / Math.max(k.length, kn.length);
      if (p > puntaje) { puntaje = p; mejor = n; }
    });
    return puntaje >= 0.8 && mejor !== texto ? mejor : null;
  }

  // ----- ¿El concepto corresponde a la categoría elegida? Solo se avisa, no se bloquea -----
  const CATEGORIA_PRESTAMOS = 'Préstamos de capital';
  const PALABRAS_CATEGORIA = {
    'Servicios': ['luz', 'agua', 'internet', 'telefono', 'telefonia', 'celular', 'gas', 'alquiler', 'arrendamiento', 'electricidad', 'energia', 'cable'],
    'Impuestos': ['igv', 'renta', 'sunat', 'afp', 'essalud', 'onp', 'impuesto', 'impuestos', 'tributo', 'tributos', 'detraccion', 'predial', 'arbitrios'],
    'Préstamos de capital': ['prestamo', 'prestamos', 'credito', 'creditos', 'cuota', 'cuotas', 'cronograma', 'leasing', 'hipoteca', 'financiamiento']
  };
  const sinTildes = t => String(t || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');

  /** Categorías que sugiere el concepto (por palabras completas), p. ej. "Recibo de luz" -> ['Servicios']. */
  function categoriasDelConcepto(concepto) {
    const palabras = new Set(sinTildes(concepto).split(/[^a-z0-9ñ]+/).filter(Boolean));
    return Object.keys(PALABRAS_CATEGORIA).filter(c => PALABRAS_CATEGORIA[c].some(p => palabras.has(p)));
  }

  /**
   * Si el concepto apunta claramente a otra categoría, devuelve { sugeridas, mensaje }; si todo cuadra o el concepto
   * no dice nada claro, null. Solo es un aviso: el asistente decide.
   */
  function avisoCategoria(concepto, categoria) {
    if (!categoria) return null;
    const sugeridas = categoriasDelConcepto(concepto);
    if (sugeridas.length === 0 || sugeridas.includes(categoria)) return null;
    const lista = sugeridas.map(c => '«' + c + '»').join(' o ');
    return { sugeridas, mensaje: 'El concepto parece de ' + lista + ', pero elegiste «' + categoria + '».' + (categoria === CATEGORIA_PRESTAMOS ? '' : ' Si está bien así, guarda igual.') };
  }

  return { empresa, nombreParecido, categoriasDelConcepto, avisoCategoria, CATEGORIA_PRESTAMOS, nombre, documento, banco, cuenta, concepto, correo, vencimiento, rangoVencimiento };
})();

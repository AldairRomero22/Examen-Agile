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

  return { nombre, documento, banco, cuenta, concepto, correo, vencimiento, rangoVencimiento };
})();

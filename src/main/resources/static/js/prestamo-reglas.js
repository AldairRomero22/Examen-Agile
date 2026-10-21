// Mismas reglas que el servidor (pe.castillo.prestamo.ValidadorPrestamo): revisan que el cronograma sea coherente.
// revisarPrestamo(datos, hoyIso) -> [{fila, campo, mensaje, bloquea}]  (fila 0 = datos del préstamo; 1..n = cuota n)
// Los importes se manejan en céntimos (enteros) para no arrastrar errores de redondeo.
const ReglasPrestamo = (() => {
  const MAXIMO = 9999999999; // S/ 99,999,999.99 en céntimos

  /** "1,528.26", "S/ 1528.26", 1528.26 -> 152826 céntimos; vacío -> null; mal escrito -> NaN */
  function centimos(v) {
    if (v === null || v === undefined) return null;
    const t = String(v).replace(/S\/\s*/i, '').replace(/,/g, '').trim();
    if (t === '') return null;
    if (!/^\d{1,8}(\.\d{1,2})?$/.test(t)) return NaN;
    return Math.round(Number(t) * 100);
  }
  const soles = c => 'S/ ' + (c / 100).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const dia = iso => iso.slice(8, 10) + '/' + iso.slice(5, 7) + '/' + iso.slice(0, 4);
  const valida = iso => /^\d{4}-\d{2}-\d{2}$/.test(iso || '') && !isNaN(new Date(iso + 'T00:00:00Z'));
  const dias = (a, b) => Math.round((new Date(b + 'T00:00:00Z') - new Date(a + 'T00:00:00Z')) / 86400000);
  function tipoNormalizado(t) {
    const x = String(t || '').trim().toUpperCase();
    if (x.startsWith('NORMAL')) return 'NORMAL';
    if (x.startsWith('DOBLE')) return 'DOBLE';
    return null;
  }

  /** La cuota normal: el valor que más se repite entre las cuotas "Normal" (sin contar la última). */
  function cuotaNormal(filas) {
    const veces = new Map();
    filas.slice(0, -1).forEach(f => {
      const c = centimos(f.cuota);
      if (Number.isFinite(c) && tipoNormalizado(f.tipo) === 'NORMAL') veces.set(c, (veces.get(c) || 0) + 1);
    });
    if (veces.size === 0 && filas.length === 1) {
      const c = centimos(filas[0].cuota);
      return Number.isFinite(c) ? c : null;
    }
    let mejor = null, max = 0;
    [...veces.entries()].sort((a, b) => a[0] - b[0]).forEach(([c, n]) => { if (n >= max) { max = n; mejor = c; } });
    return mejor;
  }

  function revisar(d, hoy) {
    const p = [];
    const error = (fila, campo, mensaje) => p.push({ fila, campo, mensaje, bloquea: true });
    const aviso = (fila, campo, mensaje) => p.push({ fila, campo, mensaje, bloquea: false });

    // ---------- Datos del préstamo ----------
    const entidad = String(d.entidad || '').trim();
    if (!entidad) error(0, 'entidad', 'Escribe la entidad que dio el préstamo (por ejemplo BCP)');
    else if (entidad.length < 2 || entidad.length > 100 || !/\p{L}/u.test(entidad)) error(0, 'entidad', 'La entidad debe tener letras (de 2 a 100 caracteres)');
    const nro = String(d.nroCredito || '').trim();
    if (!nro) error(0, 'nroCredito', 'Escribe el número de crédito');
    else if (!/^[A-Za-z0-9][A-Za-z0-9-]{2,39}$/.test(nro)) error(0, 'nroCredito', 'El número de crédito solo lleva letras, números y guiones (por ejemplo 102-30031-67)');

    let monto = centimos(d.monto);
    if (monto === null) error(0, 'monto', 'Escribe el monto del préstamo');
    else if (!Number.isFinite(monto) || monto <= 0 || monto > MAXIMO) { error(0, 'monto', 'El monto debe ser mayor que 0, con hasta 2 decimales'); monto = null; }

    const tasaTxt = String(d.tasa ?? '').replace('%', '').replace(',', '.').trim();
    let tasa = tasaTxt === '' ? null : Number(tasaTxt);
    if (tasa === null) error(0, 'tasa', 'Escribe la tasa de interés anual (%)');
    else if (!/^\d{1,3}(\.\d{1,4})?$/.test(tasaTxt) || tasa <= 0 || tasa > 100) { error(0, 'tasa', 'La tasa debe ser mayor que 0 % y como máximo 100 %'); tasa = null; }

    const plazoTxt = String(d.plazo ?? '').trim();
    let plazo = plazoTxt === '' ? null : Number(plazoTxt);
    if (plazo === null) error(0, 'plazo', 'Escribe el plazo (número de cuotas)');
    else if (!/^\d{1,3}$/.test(plazoTxt) || plazo < 1 || plazo > 360) { error(0, 'plazo', 'El plazo debe ser de 1 a 360 cuotas'); plazo = null; }

    let desembolso = d.fechaDesembolso || null;
    if (!desembolso) error(0, 'fechaDesembolso', 'Escribe la fecha de desembolso');
    else {
      const anio = Number(hoy.slice(0, 4));
      if (!valida(desembolso) || desembolso < (anio - 30) + hoy.slice(4) || desembolso > (anio + 1) + hoy.slice(4)) {
        error(0, 'fechaDesembolso', 'La fecha de desembolso no es razonable');
        desembolso = null;
      }
    }

    // ---------- Cronograma ----------
    const filas = d.cuotas || [];
    if (filas.length === 0) { error(0, 'filas', 'El cronograma no tiene cuotas'); return p; }
    if (plazo !== null && filas.length !== plazo) error(0, 'plazo', 'El plazo es de ' + plazo + ' cuotas, pero el cronograma tiene ' + filas.length + ' filas');

    const normal = cuotaNormal(filas);
    const avisosInteres = [];
    let sumaAmort = 0, completos = true;
    const CAMPOS = [['saldoInicial', 'el saldo inicial'], ['interes', 'el interés'], ['amortizacion', 'la amortización'],
      ['cuota', 'la cuota a pagar'], ['saldoFinal', 'el saldo final']];

    filas.forEach((f, i) => {
      const n = i + 1;
      if (Number(f.numero) !== n) error(n, 'numero', 'Cuota ' + n + ': el número de cuota debería ser ' + n);

      const v = f.vencimiento;
      if (!v) error(n, 'vencimiento', 'Cuota ' + n + ': falta la fecha de vencimiento');
      else if (!valida(v)) error(n, 'vencimiento', 'Cuota ' + n + ': la fecha de vencimiento no es válida');
      else if (i === 0 && desembolso && v <= desembolso) error(n, 'vencimiento', 'Cuota 1: vence el ' + dia(v) + ', que no es posterior al desembolso (' + dia(desembolso) + ')');
      else if (i > 0 && valida(filas[i - 1].vencimiento)) {
        const ant = filas[i - 1].vencimiento;
        if (v <= ant) error(n, 'vencimiento', 'Cuota ' + n + ': vence el ' + dia(v) + ', que no es posterior a la cuota anterior (' + dia(ant) + ')');
        else {
          const dd = dias(ant, v);
          if (dd < 25 || dd > 35) aviso(n, 'vencimiento', 'Cuota ' + n + ': vence ' + dd + ' días después de la anterior; lo usual es un mes');
        }
      }

      const c = {};
      let completa = true;
      CAMPOS.forEach(([campo, nombre]) => {
        const x = centimos(f[campo]);
        if (x === null) { error(n, campo, 'Cuota ' + n + ': falta ' + nombre); completa = false; }
        else if (!Number.isFinite(x) || x < 0 || x > MAXIMO) { error(n, campo, 'Cuota ' + n + ': ' + nombre + ' debe ser un importe de 0 o más, con hasta 2 decimales'); completa = false; }
        c[campo] = x;
      });
      if (!completa) { completos = false; return; }
      sumaAmort += c.amortizacion;

      const suma = c.interes + c.amortizacion;
      if (Math.abs(c.cuota - suma) > 1) error(n, 'cuota', 'Cuota ' + n + ': la cuota (' + soles(c.cuota) + ') no es igual al interés + la amortización (' + soles(c.interes) + ' + ' + soles(c.amortizacion) + ' = ' + soles(suma) + ')');
      const resta = c.saldoInicial - c.amortizacion;
      if (Math.abs(c.saldoFinal - resta) > 1) error(n, 'saldoFinal', 'Cuota ' + n + ': el saldo final (' + soles(c.saldoFinal) + ') no es igual al saldo inicial − la amortización (' + soles(c.saldoInicial) + ' − ' + soles(c.amortizacion) + ' = ' + soles(resta) + ')');
      if (i === 0) {
        if (monto !== null && Math.abs(c.saldoInicial - monto) > 1) error(n, 'saldoInicial', 'Cuota 1: el saldo inicial (' + soles(c.saldoInicial) + ') debe ser igual al monto del préstamo (' + soles(monto) + ')');
      } else {
        const finAnt = centimos(filas[i - 1].saldoFinal);
        if (Number.isFinite(finAnt) && Math.abs(c.saldoInicial - finAnt) > 1) error(n, 'saldoInicial', 'Cuota ' + n + ': el saldo inicial (' + soles(c.saldoInicial) + ') debe ser igual al saldo final de la cuota ' + i + ' (' + soles(finAnt) + ')');
      }
      const tipo = tipoNormalizado(f.tipo);
      if (!tipo) error(n, 'tipo', 'Cuota ' + n + ': el tipo de cuota debe ser «Normal» o «Doble»');
      else if (normal !== null) {
        const ultima = i === filas.length - 1;
        if (tipo === 'NORMAL' && Math.abs(c.cuota - normal) > (ultima ? 100 : 1)) error(n, 'cuota', 'Cuota ' + n + ': es «Normal», pero vale ' + soles(c.cuota) + '; la cuota normal es ' + soles(normal));
        else if (tipo === 'DOBLE' && Math.abs(c.cuota - 2 * normal) > 5) error(n, 'cuota', 'Cuota ' + n + ': es «Doble», pero vale ' + soles(c.cuota) + '; una cuota doble es ' + soles(2 * normal));
      }
      if (tasa !== null) {
        const esperado = Math.round(c.saldoInicial * tasa / 1200);
        if (Math.abs(c.interes - esperado) > 5) avisosInteres.push({ fila: n, campo: 'interes', bloquea: false,
          mensaje: 'Cuota ' + n + ': el interés (' + soles(c.interes) + ') no calza con el ' + tasa + ' % anual sobre el saldo (' + soles(esperado) + ')' });
      }
    });

    if (avisosInteres.length > 3 && tasa !== null) aviso(0, 'tasa', 'Los intereses de ' + avisosInteres.length + ' cuotas no calzan con el ' + tasa + ' % anual. Revisa la tasa o los intereses.');
    else avisosInteres.forEach(a => p.push(a));

    const fin = centimos(filas[filas.length - 1].saldoFinal);
    if (Number.isFinite(fin) && Math.abs(fin) > 1) error(filas.length, 'saldoFinal', 'Cuota ' + filas.length + ': el saldo final de la última cuota debe ser 0.00 (es ' + soles(fin) + ')');
    if (completos && monto !== null && Math.abs(sumaAmort - monto) > 5) error(0, 'monto', 'La suma de las amortizaciones (' + soles(sumaAmort) + ') no es igual al monto del préstamo (' + soles(monto) + ')');
    return p;
  }

  return { revisar, centimos, tipoNormalizado };
})();

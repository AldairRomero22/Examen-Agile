// Constancia de pago en PDF: la arma el navegador con los datos que ya trae la pantalla (sin librerías ni servidor).
// Uso: await descargarConstancia(pago, comprobante /* o null */, 'correo@usuario');
(function () {
  const A4_W = 595.28, A4_H = 841.89, MARGEN = 40, ANCHO = A4_W - 2 * MARGEN;
  const CARBON = [0.10, 0.11, 0.11], TURQUESA = [0.53, 0.96, 0.93], GRIS = [0.42, 0.43, 0.42], TEXTO = [0.10, 0.11, 0.11];
  const VERDE_FONDO = [0.93, 0.98, 0.97], ROJO_FONDO = [0.99, 0.91, 0.90], ROJO = [0.64, 0.14, 0.10], VERDE = [0.05, 0.42, 0.36];

  const medidor = document.createElement('canvas').getContext('2d');
  function ancho(texto, tam, negrita) {
    medidor.font = (negrita ? 'bold ' : '') + tam + 'px Arial, Helvetica, sans-serif';
    return medidor.measureText(texto).width;
  }

  /** Deja solo caracteres que Helvetica (WinAnsi) puede dibujar; lo demás se reemplaza. */
  function limpiar(t) {
    return String(t == null ? '' : t)
      .replace(/[–—]/g, '-').replace(/[‘’]/g, "'").replace(/[“”]/g, '"')
      .replace(/…/g, '...').replace(/ /g, ' ')
      .replace(/[^\x20-\x7E\xA1-\xFF]/g, c => (c === '\n' ? ' ' : '?'));
  }

  function partir(texto, tam, negrita, maxAncho) {
    const lineas = [];
    String(texto == null ? '' : texto).split('\n').forEach(parrafo => {
      let actual = '';
      limpiar(parrafo).split(' ').forEach(palabra => {
        const prueba = actual ? actual + ' ' + palabra : palabra;
        if (actual && ancho(prueba, tam, negrita) > maxAncho) { lineas.push(actual); actual = palabra; }
        else actual = prueba;
      });
      lineas.push(actual);
    });
    return lineas;
  }

  function bytesDeTexto(s) {
    const b = new Uint8Array(s.length);
    for (let i = 0; i < s.length; i++) b[i] = s.charCodeAt(i) & 255;
    return b;
  }
  const num = n => (Math.round(n * 100) / 100).toString();
  const color = (c, trazo) => c.map(num).join(' ') + (trazo ? ' RG ' : ' rg ');

  class Pdf {
    constructor() { this.paginas = []; this.imagenes = []; this.nueva(); }
    nueva() { this.pag = { ops: [], imgs: [] }; this.paginas.push(this.pag); this.y = MARGEN; }
    libre() { return A4_H - MARGEN - 30 - this.y; }
    asegurar(alto) { if (alto > this.libre()) this.nueva(); }
    texto(x, y, t, tam, negrita, col, alinear) {
      const limpio = limpiar(t).replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)');
      let px = x;
      if (alinear === 'der') px = x - ancho(limpiar(t), tam, negrita);
      this.pag.ops.push('BT ' + color(col || TEXTO) + '/' + (negrita ? 'F2' : 'F1') + ' ' + tam + ' Tf ' + num(px) + ' ' + num(A4_H - y) + ' Td (' + limpio + ') Tj ET');
    }
    rect(x, y, w, h, col) { this.pag.ops.push(color(col) + num(x) + ' ' + num(A4_H - y - h) + ' ' + num(w) + ' ' + num(h) + ' re f'); }
    marco(x, y, w, h, col, grosor) { this.pag.ops.push(color(col, true) + num(grosor || 1) + ' w ' + num(x) + ' ' + num(A4_H - y - h) + ' ' + num(w) + ' ' + num(h) + ' re S'); }
    linea(x1, y, x2, col) { this.pag.ops.push(color(col, true) + '0.6 w ' + num(x1) + ' ' + num(A4_H - y) + ' m ' + num(x2) + ' ' + num(A4_H - y) + ' l S'); }
    imagen(bytes, w, h, x, y, dw, dh) {
      this.imagenes.push({ bytes, w, h });
      const n = this.imagenes.length;
      this.pag.imgs.push(n);
      this.pag.ops.push('q ' + num(dw) + ' 0 0 ' + num(dh) + ' ' + num(x) + ' ' + num(A4_H - y - dh) + ' cm /Im' + n + ' Do Q');
    }
  }

  // Versión simple y segura de armado (un objeto = un bloque completo)
  function armarArchivo(pdf) {
    const partes = [], offsets = [];
    let largo = 0;
    const poner = d => { const b = typeof d === 'string' ? bytesDeTexto(d) : d; partes.push(b); largo += b.length; };
    const objeto = (n, cabecera, flujo) => {
      offsets[n] = largo;
      poner(n + ' 0 obj\n' + cabecera);
      if (flujo) { poner('\nstream\n'); poner(flujo); poner('\nendstream'); }
      poner('\nendobj\n');
    };
    const nPag = pdf.paginas.length;
    const idContenido = i => 5 + i * 2, idPagina = i => 6 + i * 2, idImagen = k => 5 + nPag * 2 + k;
    poner('%PDF-1.4\n');
    objeto(1, '<< /Type /Catalog /Pages 2 0 R >>');
    objeto(2, '<< /Type /Pages /Count ' + nPag + ' /Kids [' + pdf.paginas.map((p, i) => idPagina(i) + ' 0 R').join(' ') + '] >>');
    objeto(3, '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>');
    objeto(4, '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>');
    pdf.paginas.forEach((p, i) => {
      const contenido = bytesDeTexto(p.ops.join('\n'));
      objeto(idContenido(i), '<< /Length ' + contenido.length + ' >>', contenido);
      const xobj = p.imgs.length ? ' /XObject << ' + p.imgs.map(n => '/Im' + n + ' ' + idImagen(n - 1) + ' 0 R').join(' ') + ' >>' : '';
      objeto(idPagina(i), '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ' + num(A4_W) + ' ' + num(A4_H) + '] /Contents ' + idContenido(i) + ' 0 R /Resources << /Font << /F1 3 0 R /F2 4 0 R >>' + xobj + ' >> >>');
    });
    pdf.imagenes.forEach((im, k) => {
      objeto(idImagen(k), '<< /Type /XObject /Subtype /Image /Width ' + im.w + ' /Height ' + im.h +
        ' /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ' + im.bytes.length + ' >>', im.bytes);
    });
    const total = 5 + nPag * 2 + pdf.imagenes.length;
    const inicioXref = largo;
    let xref = 'xref\n0 ' + total + '\n0000000000 65535 f \n';
    for (let n = 1; n < total; n++) xref += String(offsets[n]).padStart(10, '0') + ' 00000 n \n';
    poner(xref + 'trailer\n<< /Size ' + total + ' /Root 1 0 R >>\nstartxref\n' + inicioXref + '\n%%EOF');
    return new Blob(partes, { type: 'application/pdf' });
  }

  /** Trae la imagen del voucher y la convierte a JPEG (si es PDF no se puede incrustar: devuelve null). */
  async function imagenDelVoucher(c) {
    if (!c || !c.id || c.tipoMime === 'application/pdf') return null;
    try {
      const r = await fetch('/api/comprobantes/' + c.id + '/archivo', { credentials: 'same-origin' });
      if (!r.ok) return null;
      const url = URL.createObjectURL(await r.blob());
      const img = await new Promise((ok, mal) => { const i = new Image(); i.onload = () => ok(i); i.onerror = mal; i.src = url; });
      const tope = 1400, esc = Math.min(1, tope / Math.max(img.naturalWidth, img.naturalHeight));
      const w = Math.max(1, Math.round(img.naturalWidth * esc)), h = Math.max(1, Math.round(img.naturalHeight * esc));
      const lienzo = document.createElement('canvas'); lienzo.width = w; lienzo.height = h;
      const g = lienzo.getContext('2d'); g.fillStyle = '#fff'; g.fillRect(0, 0, w, h); g.drawImage(img, 0, 0, w, h);
      URL.revokeObjectURL(url);
      const b64 = lienzo.toDataURL('image/jpeg', 0.85).split(',')[1];
      const bin = atob(b64), bytes = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
      return { bytes, w, h };
    } catch (e) { return null; }
  }

  function seccion(pdf, titulo) {
    pdf.asegurar(40);
    pdf.y += 14;
    pdf.texto(MARGEN, pdf.y + 9, titulo.toUpperCase(), 9, true, GRIS);
    pdf.y += 15;
    pdf.linea(MARGEN, pdf.y, MARGEN + ANCHO, CARBON);
    pdf.y += 8;
  }

  function fila(pdf, etiqueta, valor) {
    const lineas = partir(valor === '' || valor == null ? '-' : valor, 10.5, false, ANCHO - 160);
    const alto = Math.max(18, lineas.length * 14 + 4);
    pdf.asegurar(alto);
    pdf.texto(MARGEN, pdf.y + 11, etiqueta, 9, false, GRIS);
    lineas.forEach((l, i) => pdf.texto(MARGEN + 160, pdf.y + 11 + i * 14, l, 10.5, false, TEXTO));
    pdf.y += alto;
  }

  async function descargarConstancia(p, c, usuario) {
    const pdf = new Pdf();
    const hoy = new Date().toLocaleString('es-PE', { timeZone: 'America/Lima', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false });

    // ---- Cabecera ----
    pdf.rect(0, 0, A4_W, 74, CARBON);
    pdf.texto(MARGEN, 36, 'Castillo', 22, true, [1, 1, 1]);
    pdf.texto(MARGEN, 52, 'CONTROL DE PAGOS', 8, false, TURQUESA);
    pdf.texto(A4_W - MARGEN, 36, 'CONSTANCIA DE PAGO', 12, true, TURQUESA, 'der');
    pdf.texto(A4_W - MARGEN, 52, 'N.º ' + String(p.id).padStart(6, '0'), 9, false, [0.78, 0.78, 0.77], 'der');
    pdf.y = 96;

    // ---- Concepto, monto y estado ----
    const titulo = partir(p.concepto, 20, true, ANCHO - 130);
    titulo.forEach((l, i) => pdf.texto(MARGEN, pdf.y + 18 + i * 24, l, 20, true, TEXTO));
    pdf.y += titulo.length * 24;
    pdf.texto(MARGEN, pdf.y + 26, formatearMonto(p.monto), 28, true, TEXTO);
    const etiqueta = limpiar(p.estadoEtiqueta || p.estado).toUpperCase();
    const anchoEtq = ancho(etiqueta, 10, true) + 24;
    const colEstado = p.estado === 'PAGADO' ? VERDE : (p.estado === 'OBSERVADO' || p.estado === 'VENCIDO' ? ROJO : CARBON);
    pdf.marco(A4_W - MARGEN - anchoEtq, 98, anchoEtq, 24, colEstado, 1.4);
    pdf.texto(A4_W - MARGEN - anchoEtq / 2 - ancho(etiqueta, 10, true) / 2, 114, etiqueta, 10, true, colEstado);
    pdf.y += 40;

    // ---- Datos del pago ----
    seccion(pdf, 'Datos del pago');
    fila(pdf, 'A quién se paga', p.beneficiario);
    if (p.ruc) fila(pdf, p.ruc.length === 11 ? 'RUC' : 'DNI', p.ruc);
    fila(pdf, 'Categoría', p.categoria);
    fila(pdf, 'Fecha de vencimiento', formatearFecha(p.fechaVencimiento));
    fila(pdf, 'Estado', p.estadoEtiqueta || p.estado);
    if (p.observacion) fila(pdf, 'Observación', p.observacion);
    fila(pdf, 'Registrado por', p.registradoPor);
    fila(pdf, 'Registrado el', formatearFechaHora(p.registradoEn));
    if (p.enEspera) {
      fila(pdf, 'En espera', p.notaEspera + (p.esperaPor ? ' (' + p.esperaPor + ', ' + formatearFechaHora(p.esperaEn) + ')' : ''));
    } else if (p.liberadoEn) {
      fila(pdf, 'Liberado', 'Por ' + p.liberadoPor + ' el ' + formatearFechaHora(p.liberadoEn));
    }

    // ---- Comprobante y verificación ----
    if (c) {
      seccion(pdf, 'Comprobante de pago');
      fila(pdf, 'Monto pagado', formatearMonto(c.monto));
      fila(pdf, 'Fecha de pago', formatearFecha(c.fecha));
      fila(pdf, 'Número de operación', c.nroOperacion);
      fila(pdf, 'Subido por', c.subidoPor);
      fila(pdf, 'Subido el', formatearFechaHora(c.subidoEn));
      if (c.insigniaEtiqueta) fila(pdf, 'Verificación', c.insigniaEtiqueta);

      if (c.reglas && c.reglas.length) {
        seccion(pdf, 'Verificación automática');
        c.reglas.forEach(r => {
          const detalle = [];
          if (r.recibido) detalle.push(r.recibido);
          if (r.esperado) detalle.push('Esperado: ' + r.esperado);
          const lineas = partir(detalle.join('   |   '), 9.5, false, ANCHO - 120);
          const msg = r.cumple ? [] : partir(r.mensaje, 9.5, true, ANCHO - 120);
          const alto = 30 + (lineas.length - 1) * 12 + msg.length * 12;
          pdf.asegurar(alto + 6);
          pdf.rect(MARGEN, pdf.y, ANCHO, alto, r.cumple ? VERDE_FONDO : ROJO_FONDO);
          pdf.texto(MARGEN + 10, pdf.y + 14, r.titulo, 10.5, true, TEXTO);
          pdf.texto(MARGEN + ANCHO - 10, pdf.y + 14, r.cumple ? 'CUMPLE' : 'NO CUMPLE', 9, true, r.cumple ? VERDE : ROJO, 'der');
          lineas.forEach((l, i) => pdf.texto(MARGEN + 10, pdf.y + 27 + i * 12, l, 9.5, false, GRIS));
          msg.forEach((l, i) => pdf.texto(MARGEN + 10, pdf.y + 27 + lineas.length * 12 + i * 12, l, 9.5, true, ROJO));
          pdf.y += alto + 6;
        });
      }

      // ---- Voucher incrustado ----
      const voucher = await imagenDelVoucher(c);
      if (voucher) {
        const maxH = 430;
        let dw = Math.min(ANCHO, voucher.w), dh = dw * voucher.h / voucher.w;
        if (dh > maxH) { dh = maxH; dw = dh * voucher.w / voucher.h; }
        pdf.asegurar(dh + 50);            // el título y la imagen van juntos en la misma página
        seccion(pdf, 'Voucher');
        const x = MARGEN + (ANCHO - dw) / 2;
        pdf.imagen(voucher.bytes, voucher.w, voucher.h, x, pdf.y, dw, dh);
        pdf.marco(x, pdf.y, dw, dh, [0.8, 0.8, 0.79], 0.6);
        pdf.y += dh + 6;
      } else {
        seccion(pdf, 'Voucher');
        fila(pdf, 'Archivo', (c.nombreArchivo || '') + ' (se ve en el sistema)');
      }
    }

    // ---- Pie en todas las páginas ----
    const n = pdf.paginas.length;
    pdf.paginas.forEach((pagina, i) => {
      pdf.pag = pagina;
      pdf.linea(MARGEN, A4_H - 40, MARGEN + ANCHO, [0.8, 0.8, 0.79]);
      pdf.texto(MARGEN, A4_H - 26, 'Generado el ' + hoy + (usuario ? ' por ' + usuario : '') + '  |  Castillo - Control de pagos', 8, false, GRIS);
      pdf.texto(A4_W - MARGEN, A4_H - 26, 'Página ' + (i + 1) + ' de ' + n, 8, false, GRIS, 'der');
    });

    const nombre = 'constancia-pago-' + String(p.id).padStart(6, '0') + '-' +
      limpiar(p.concepto).toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 40) + '.pdf';
    const blob = armarArchivo(pdf);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = nombre; document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 4000);
  }

  window.descargarConstancia = descargarConstancia;
})();

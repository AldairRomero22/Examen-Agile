// Animaciones de las pantallas internas con GSAP (+ ScrollTrigger) y desplazamiento suave con Lenis.
// Solo es presentación: no cambia datos, no llama a la API y no toca las reglas del negocio.
// Si las librerías no cargan o la persona pidió "menos movimiento" en su sistema, la página funciona igual, sin animar.
(function () {
  'use strict';
  var gsap = window.gsap;
  var menosMovimiento = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (!gsap || menosMovimiento) return;

  document.documentElement.classList.add('con-animaciones');
  var ST = window.ScrollTrigger || null;
  if (ST) gsap.registerPlugin(ST);
  var EASE = 'power3.out';

  // ---------- Desplazamiento suave (Lenis) sincronizado con GSAP ----------
  if (window.Lenis) {
    var lenis = new window.Lenis({
      lerp: 0.12,
      smoothWheel: true,
      anchors: true,
      allowNestedScroll: true,   // tablas anchas, textos largos y el menú del celular se desplazan por su cuenta
      prevent: function (nodo) { return nodo.classList && nodo.classList.contains('barra-cuerpo'); }
    });
    if (ST) lenis.on('scroll', ST.update);
    gsap.ticker.add(function (t) { lenis.raf(t * 1000); });
    gsap.ticker.lagSmoothing(0);
    window.castilloLenis = lenis;
  }

  // ---------- Utilidades ----------
  function visible(el) {
    return el && el.isConnected && !el.hidden && el.getClientRects().length > 0;
  }
  function entrar(els, o) {
    o = o || {};
    els = gsap.utils.toArray(els).filter(visible);
    if (!els.length) return;
    gsap.fromTo(els, { y: o.y == null ? 16 : o.y, x: o.x || 0, opacity: 0 }, {
      y: 0, x: 0, opacity: 1, duration: o.d || 0.55, ease: o.ease || EASE, stagger: o.s == null ? 0.06 : o.s,
      delay: o.delay || 0, overwrite: 'auto', clearProps: 'transform,opacity'
    });
  }
  // Lo que ya se ve entra al instante; lo que está más abajo entra al llegar con el scroll.
  // Solo se usa opacidad (no se oculta el elemento), así nunca queda algo inaccesible.
  var creoDisparadores = false;
  function revelarAlBajar(els, o) {
    o = o || {};
    var ahora = [];
    gsap.utils.toArray(els).filter(visible).forEach(function (el) {
      if (!ST || el.getBoundingClientRect().top < window.innerHeight) { ahora.push(el); return; }
      gsap.set(el, { opacity: 0, y: o.y == null ? 18 : o.y });
      ST.create({
        trigger: el, start: 'top bottom', once: true,
        onEnter: function () {
          gsap.to(el, { opacity: 1, y: 0, duration: o.d || 0.55, ease: EASE, overwrite: 'auto', clearProps: 'transform,opacity' });
        }
      });
      creoDisparadores = true;
    });
    entrar(ahora, o);
  }
  function limpiarDisparadores() {
    if (!ST) return;
    ST.getAll().forEach(function (t) { if (t.trigger && !t.trigger.isConnected) t.kill(); });
    if (creoDisparadores) { creoDisparadores = false; ST.refresh(); }
  }

  // Montos que "cuentan" hasta su valor; al terminar se deja exactamente el texto original
  var FORMATO = /^S\/ ([\d,]+\.\d{2})$/;
  function contar(el, duracion) {
    var texto = (el.textContent || '').trim();
    var m = FORMATO.exec(texto);
    if (!m) return;
    var valor = parseFloat(m[1].replace(/,/g, ''));
    if (!(valor > 0)) return;
    var o = { v: 0 };
    gsap.to(o, {
      v: valor, duration: duracion || 0.9, ease: 'power2.out',
      onUpdate: function () {
        if (!el.isConnected) return;
        el.textContent = 'S/ ' + o.v.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
      },
      onComplete: function () { if (el.isConnected) el.textContent = texto; }
    });
  }
  // Campo con error: un pequeño movimiento para que se note dónde está
  function sacudir(el) {
    if (!visible(el)) return;
    gsap.fromTo(el, { x: -7 }, { x: 0, duration: 0.55, ease: 'elastic.out(1, 0.3)', clearProps: 'transform' });
  }

  // ---------- Entrada de la pantalla ----------
  var main = document.querySelector('main');
  if (main) revelarAlBajar(Array.prototype.slice.call(main.children), { s: 0.07 });

  // ---------- Lo que dibujan las pantallas después (datos que llegan de la API) ----------
  var ultimoTecleo = 0;
  document.addEventListener('input', function (e) {
    if (e.target && e.target.id === 'buscar') ultimoTecleo = Date.now();
  }, true);

  var nuevos = new Set();
  var mostrados = new Set();
  var programado = false;
  function encolar(conjunto, el) {
    conjunto.add(el);
    if (!programado) { programado = true; requestAnimationFrame(procesar); }
  }

  function procesar() {
    programado = false;
    var agregados = Array.from(nuevos); nuevos.clear();
    var aparecidos = Array.from(mostrados); mostrados.clear();

    // Panel: total del mes y tarjetas por estado
    var tarjetas = agregados.filter(function (el) { return el.matches('.tarjeta-resumen'); });
    if (tarjetas.length) {
      entrar(tarjetas, { s: 0.06, y: 20 });
      tarjetas.forEach(function (t) {
        var monto = t.querySelector('.tarjeta-monto');
        if (monto) contar(monto, t.classList.contains('tarjeta-total') ? 1.1 : 0.8);
        var barra = t.querySelector('.progreso-barra i');
        if (barra) gsap.from(barra, { scaleX: 0, transformOrigin: 'left center', duration: 1.1, ease: 'power2.out', delay: 0.15, clearProps: 'transform' });
      });
    }
    // Alertas de vencimiento
    var alertas = agregados.filter(function (el) { return el.matches('.alertas-lista > li'); });
    if (alertas.length) entrar(alertas, { x: -12, y: 0, s: 0.07 });

    // Filas de las tablas (no se animan mientras se escribe en el buscador)
    var filas = agregados.filter(function (el) { return el.matches('tbody > tr'); });
    if (filas.length && Date.now() - ultimoTecleo > 700) revelarAlBajar(filas.slice(0, 40), { y: 8, s: 0.03, d: 0.4 });

    // Resultado de la verificación del comprobante
    var resultado = agregados.filter(function (el) { return el.matches('.resultado'); });
    resultado.forEach(function (r) {
      entrar(r, { y: 20 });
      entrar(r.querySelectorAll('.matriz-fila'), { x: -10, y: 0, s: 0.09, delay: 0.15 });
    });
    // "Leído del documento"
    agregados.filter(function (el) { return el.matches('.etiqueta-leido'); }).forEach(function (el) {
      gsap.fromTo(el, { scale: 0.6, opacity: 0 }, { scale: 1, opacity: 1, duration: 0.4, ease: 'back.out(2)', clearProps: 'transform,opacity' });
    });

    // Elementos que estaban ocultos y aparecen (avisos, errores, secciones, botones según el estado)
    aparecidos.forEach(function (el) {
      if (!visible(el)) return;
      if (el.matches('.error-campo')) {
        var campo = document.getElementById(el.id.replace(/^error-/, ''));
        sacudir(campo && campo.type !== 'file' ? campo : el);
        entrar(el, { y: -4, d: 0.3 });
      } else if (el.id === 'contenido') {
        revelarAlBajar(Array.prototype.slice.call(el.children), { s: 0.08 });
        var montoPago = el.querySelector('.pago-monto');
        if (montoPago) contar(montoPago, 0.9);
      } else if (el.matches('.aviso')) {
        entrar(el, { y: -10, d: 0.35 });
      } else if (!el.closest('.barra')) {
        entrar(el, { y: 10, d: 0.4 });
      }
    });
    limpiarDisparadores();
  }

  var observador = new MutationObserver(function (cambios) {
    cambios.forEach(function (c) {
      if (c.type === 'childList') {
        c.addedNodes.forEach(function (n) { if (n.nodeType === 1) encolar(nuevos, n); });
      } else if (c.type === 'attributes' && c.attributeName === 'hidden' && !c.target.hidden && c.oldValue !== null) {
        encolar(mostrados, c.target);
      }
    });
  });
  if (main) observador.observe(main, { childList: true, subtree: true, attributes: true, attributeFilter: ['hidden'], attributeOldValue: true });

  // ---------- Menú del celular ----------
  document.addEventListener('castillo:menu', function (e) {
    if (!e.detail || !e.detail.abierta) return;
    var cuerpo = document.querySelector('.barra-cuerpo');
    if (!cuerpo) return;
    gsap.fromTo(cuerpo, { height: 0, opacity: 0 }, { height: 'auto', opacity: 1, duration: 0.35, ease: 'power2.out', clearProps: 'height,opacity' });
    entrar(cuerpo.querySelectorAll('.menu a, .usuario'), { x: -10, y: 0, s: 0.05, d: 0.35, delay: 0.05 });
  });

  // ---------- Salida suave al ir a otra pantalla del sistema (sin retrasar el cambio de página) ----------
  document.addEventListener('click', function (e) {
    if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey || !main) return;
    var a = e.target.closest ? e.target.closest('a[href]') : null;
    if (!a || a.closest('.fila-click') || (a.target && a.target !== '_self') || a.hasAttribute('download')) return;
    var url;
    try { url = new URL(a.href, window.location.href); } catch (err) { return; }
    if (url.origin !== window.location.origin || url.pathname.indexOf('/api/') === 0) return;
    if (url.pathname === window.location.pathname && url.search === window.location.search) return;
    // La navegación sigue normal (no se retrasa): mientras llega la otra pantalla, esta se desvanece
    gsap.to(main, { opacity: 0, y: -8, duration: 0.2, ease: 'power1.in' });
    // Por si el navegador no cambia de página (descarga, cancelación), se vuelve a mostrar
    setTimeout(function () { gsap.set(main, { clearProps: 'opacity,transform' }); }, 2500);
  });
  window.addEventListener('pageshow', function (e) {
    if (e.persisted && main) gsap.set(main, { clearProps: 'opacity,transform' });
  });
})();

package pe.castillo.comprobante;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import pe.castillo.config.Fechas;
import pe.castillo.config.NegocioException;
import pe.castillo.lector.LecturaVoucher;
import pe.castillo.lector.LectorNoDisponibleException;
import pe.castillo.lector.LectorVoucher;
import pe.castillo.obligacion.Obligacion;
import pe.castillo.obligacion.ObligacionRepository;
import pe.castillo.usuario.UsuarioRepository;

/**
 * HU-07 subir y leer el comprobante · HU-08 verificarlo y cambiar el estado · HU-09 consultarlo.
 */
@Service
public class ComprobanteService {

    private static final Logger LOG = LoggerFactory.getLogger(ComprobanteService.class);

    /** HU-07 criterio 2 */
    private static final long MAXIMO_BYTES = 5L * 1024 * 1024;
    private static final String FORMATO_NO_PERMITIDO = "Formato o tamaño no permitido";
    private static final String YA_USADO = "Este comprobante ya se usó en otro pago";

    private final ComprobanteRepository comprobantes;
    private final VerificacionRepository verificaciones;
    private final ObligacionRepository obligaciones;
    private final UsuarioRepository usuarios;
    private final LectorVoucher lector;

    public ComprobanteService(ComprobanteRepository comprobantes, VerificacionRepository verificaciones,
                              ObligacionRepository obligaciones, UsuarioRepository usuarios, LectorVoucher lector) {
        this.comprobantes = comprobantes;
        this.verificaciones = verificaciones;
        this.obligaciones = obligaciones;
        this.usuarios = usuarios;
        this.lector = lector;
    }

    // =====================================================================
    // HU-07 · Subir el archivo y leerlo
    // (sin @Transactional: la lectura puede tardar y no debe tener una conexión de la base ocupada)
    // =====================================================================

    public ComprobanteLeido subir(Long obligacionId, MultipartFile archivo, Long usuarioId) {
        Obligacion o = buscarObligacion(obligacionId);
        exigirAdmiteComprobante(o);   // criterio 1

        if (archivo == null || archivo.isEmpty()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Selecciona el archivo del voucher", "archivo");
        }
        if (archivo.getSize() > MAXIMO_BYTES) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, FORMATO_NO_PERMITIDO, "archivo");
        }
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "No se pudo recibir el archivo, intenta de nuevo", "archivo");
        }
        String tipo = ArchivoVoucher.detectarTipo(bytes);   // por el contenido, no por el nombre ni por lo que diga el navegador
        if (tipo == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, FORMATO_NO_PERMITIDO, "archivo");
        }

        // criterio 3: el lector extrae fecha, número de operación y monto
        LecturaVoucher lectura;
        try {
            lectura = lector.leer(bytes, tipo);
        } catch (LectorNoDisponibleException e) {
            // criterio 5
            LOG.warn("El lector {} no pudo leer el voucher: {}", lector.nombre(), e.getMessage(), e);
            throw new NegocioException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo leer el voucher, intenta de nuevo");
        }
        // criterio 4: si no se leyó nada, el archivo se rechaza y no se guarda
        if (lectura.estaVacia()) {
            throw new NegocioException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No parece un voucher de pago. Sube la captura o el PDF de la operación", "archivo");
        }
        // Pedido del Product Owner: los datos del voucher solo se leen, nunca se escriben a mano.
        // Si falta alguno, no se guarda y se pide una imagen más clara.
        if (!lectura.estaCompleta()) {
            throw new NegocioException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No se pudo leer " + faltantes(lectura) + " del voucher. Sube una imagen más clara donde se vean"
                            + " completos el monto, la fecha y el número de operación", "archivo");
        }
        String leidoPor = lectura.lector() != null ? lectura.lector() : lector.nombre();

        // criterio 5: se guarda el archivo, lo leído, quién lo subió y cuándo
        Comprobante guardado = comprobantes.save(new Comprobante(
                o.getId(), bytes, ArchivoVoucher.nombreSeguro(archivo.getOriginalFilename()), tipo, ArchivoVoucher.sha256(bytes),
                leidoPor, lectura, usuarioId, OffsetDateTime.now(Fechas.LIMA)));

        return new ComprobanteLeido(guardado.getId(), guardado.getNombreArchivo(), guardado.getLector(),
                o.getMonto(), lectura.fecha(), lectura.nroOperacion(), lectura.monto());
    }

    // =====================================================================
    // HU-07 criterio 6 + HU-08 · Enviar el comprobante y verificarlo
    // =====================================================================

    @Transactional
    public ResultadoEnvio enviar(Long comprobanteId, EnvioForm form, Long usuarioId) {
        Comprobante c = comprobantes.findById(comprobanteId)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Comprobante no encontrado"));
        if (!"LEIDO".equals(c.getEstado())) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "Este comprobante ya se envió. Sube otro archivo para intentarlo de nuevo");
        }
        Obligacion o = buscarObligacion(c.getObligacionId());
        exigirAdmiteComprobante(o);

        // Los datos del voucher son solo los leídos: no se escriben ni se cambian a mano (pedido del Product Owner)
        LocalDate fecha = c.getFechaLeida();
        String nro = c.getNroOperacionLeido();
        BigDecimal monto = c.getMontoLeido();
        if (fecha == null || nro == null || monto == null) {
            throw new NegocioException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Este voucher no se leyó completo. Sube una imagen más clara", "archivo");
        }
        if (form != null && ((form.fecha() != null && !form.fecha().equals(fecha))
                || (form.nroOperacion() != null && !form.nroOperacion().isBlank()
                        && !form.nroOperacion().replaceAll("\\s+", "").equalsIgnoreCase(nro))
                || (form.monto() != null && form.monto().compareTo(monto) != 0))) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Los datos leídos del voucher no se pueden modificar");
        }
        boolean fechaManual = false;
        boolean nroManual = false;
        boolean montoManual = false;

        // HU-07 criterio 6: pasa a "En verificación" y enseguida se aplican las cuatro reglas (HU-08)
        c.enviar(fecha, nro, monto, fechaManual, nroManual, montoManual);

        LocalDate hoy = Fechas.hoy();
        boolean operacionUsada = comprobantes.existsByNroOperacionEnviadoAndEstadoAndObligacionIdNot(
                nro, "APROBADO", o.getId());
        boolean archivoUsado = comprobantes.existsByHashSha256AndEstadoAndObligacionIdNot(
                c.getHashSha256(), "APROBADO", o.getId());
        List<ReglasVerificacion.Regla> reglas = ReglasVerificacion.evaluar(
                o.getMonto(), monto, fecha, nro, operacionUsada, archivoUsado, hoy);
        boolean pagado = ReglasVerificacion.todasCumplen(reglas);

        OffsetDateTime ahora = OffsetDateTime.now(Fechas.LIMA);
        if (pagado) {
            c.aprobar();
            o.marcarPagado(c.tieneDatosManuales() ? "MANUAL" : "AUTOMATICA", ahora);
        } else {
            c.rechazar();
            o.marcarObservado();
        }
        try {
            comprobantes.saveAndFlush(c);   // si dos personas aprueban lo mismo a la vez, los índices únicos lo frenan
        } catch (DataIntegrityViolationException e) {
            throw new NegocioException(HttpStatus.CONFLICT, YA_USADO);
        }
        obligaciones.save(o);
        verificaciones.save(new Verificacion(
                c.getId(), o.getId(),
                reglas.get(0).cumple(), reglas.get(1).cumple(), reglas.get(2).cumple(), reglas.get(3).cumple(),
                pagado ? null : ReglasVerificacion.detalleDeFallas(reglas),
                usuarioId, ahora));

        return new ResultadoEnvio(c.getId(), o.getId(),
                pagado ? "PAGADO" : "OBSERVADO", pagado ? "Pagado" : "Observado",
                c.tieneDatosManuales() ? "MANUAL" : "AUTOMATICA", etiquetaInsignia(c.tieneDatosManuales()),
                reglas);
    }

    // =====================================================================
    // HU-09 · Consultar el comprobante de un pago
    // =====================================================================

    @Transactional(readOnly = true)
    public ComprobanteVista consultar(Long obligacionId) {
        Obligacion o = buscarObligacion(obligacionId);
        ComprobanteInfo c = comprobantes.findFirstByObligacionIdAndEstadoOrderByIdDesc(o.getId(), "APROBADO")
                .or(() -> comprobantes.findFirstByObligacionIdAndEstadoInOrderByIdDesc(
                        o.getId(), List.of("ENVIADO", "RECHAZADO")))
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Este pago aún no tiene comprobante"));

        Verificacion v = verificaciones.findFirstByComprobanteIdOrderByIdDesc(c.getId()).orElse(null);
        boolean manual = Boolean.TRUE.equals(c.getFechaManual()) || Boolean.TRUE.equals(c.getNroOperacionManual())
                || Boolean.TRUE.equals(c.getMontoManual());

        boolean aprobado = v != null && "PAGADO".equals(v.getResultado());
        List<ReglasVerificacion.Regla> reglas = List.of();
        if (v != null && c.getFechaEnviada() != null && c.getMontoEnviado() != null) {
            reglas = ReglasVerificacion.construir(
                    v.isReglaMonto(), v.isReglaFecha(), v.isReglaOperacion(), v.isReglaArchivo(),
                    o.getMonto(), c.getMontoEnviado(), c.getFechaEnviada(), c.getNroOperacionEnviado(),
                    v.getVerificadoEn().atZoneSameInstant(Fechas.LIMA).toLocalDate());
        }

        return new ComprobanteVista(
                c.getId(), o.getId(), c.getNombreArchivo(), c.getTipoMime(), c.getTamanoBytes(), c.getLector(),
                c.getFechaEnviada(), Boolean.TRUE.equals(c.getFechaManual()),
                c.getNroOperacionEnviado(), Boolean.TRUE.equals(c.getNroOperacionManual()),
                c.getMontoEnviado(), Boolean.TRUE.equals(c.getMontoManual()),
                o.getMonto(),
                aprobado ? (manual ? "MANUAL" : "AUTOMATICA") : null,
                aprobado ? etiquetaInsignia(manual) : null,   // la insignia solo existe en pagos verificados (Pagado)
                nombreUsuario(c.getSubidoPor()), c.getSubidoEn(),
                v == null ? null : v.getResultado(),
                v == null ? null : ("PAGADO".equals(v.getResultado()) ? "Pagado" : "Observado"),
                v == null ? null : nombreUsuario(v.getVerificadoPor()),
                v == null ? null : v.getVerificadoEn(),
                reglas);
    }

    /** HU-09 criterio 2: datos para abrir o descargar el archivo. */
    @Transactional(readOnly = true)
    public ComprobanteInfo infoDelArchivo(Long comprobanteId) {
        return comprobantes.findProjectedById(comprobanteId)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Comprobante no encontrado"));
    }

    @Transactional(readOnly = true)
    public byte[] bytesDelArchivo(Long comprobanteId) {
        byte[] bytes = comprobantes.archivoDe(comprobanteId);
        if (bytes == null) {
            throw new NegocioException(HttpStatus.NOT_FOUND, "Comprobante no encontrado");
        }
        return bytes;
    }

    // =====================================================================
    // Ayudas
    // =====================================================================

    private Obligacion buscarObligacion(Long id) {
        return obligaciones.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Pago no encontrado"));
    }

    /**
     * HU-07 criterio 1 y HU-08 criterio 5: solo se sube a pagos Por pagar, Por vencer, Vencido u Observado.
     * Un pago Pagado o En espera no admite comprobantes.
     */
    private void exigirAdmiteComprobante(Obligacion o) {
        if ("PAGADO".equals(o.getEstadoPago())) {
            throw new NegocioException(HttpStatus.CONFLICT, "Este pago ya está pagado y no admite nuevos comprobantes");
        }
        if (o.isEnEspera()) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "Este pago está en espera: el gerente debe liberarlo antes de subir un comprobante");
        }
        if ("EN_VERIFICACION".equals(o.getEstadoPago())) {
            throw new NegocioException(HttpStatus.CONFLICT, "Este pago ya se está verificando");
        }
    }

    private String nombreUsuario(Long id) {
        return usuarios.findById(id).map(u -> u.getNombre() + " (" + u.getCorreo() + ")").orElse("—");
    }

    /** "el monto", "la fecha y el número de operación"... según lo que el lector no pudo leer. */
    static String faltantes(LecturaVoucher l) {
        List<String> f = new java.util.ArrayList<>();
        if (l.monto() == null) {
            f.add("el monto");
        }
        if (l.fecha() == null) {
            f.add("la fecha de pago");
        }
        if (l.nroOperacion() == null) {
            f.add("el número de operación");
        }
        if (f.size() <= 1) {
            return f.isEmpty() ? "los datos" : f.get(0);
        }
        return String.join(", ", f.subList(0, f.size() - 1)) + " ni " + f.get(f.size() - 1);
    }

    private static String etiquetaInsignia(boolean manual) {
        return manual ? "Verificado manualmente" : "Verificado automáticamente";
    }
}

package pe.castillo.comprobante;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import pe.castillo.usuario.UsuarioRepository;

/**
 * HU-07 subir y leer · HU-08 enviar y verificar (solo asistente; lo exige SecurityConfig)
 * HU-09 consultar y abrir el archivo (asistente y gerente, solo lectura).
 */
@RestController
public class ComprobanteController {

    private final ComprobanteService servicio;
    private final UsuarioRepository usuarios;

    public ComprobanteController(ComprobanteService servicio, UsuarioRepository usuarios) {
        this.servicio = servicio;
        this.usuarios = usuarios;
    }

    /** HU-07: sube el archivo (JPG, PNG o PDF, máximo 5 MB) y devuelve lo que leyó el lector. */
    @PostMapping(path = "/api/obligaciones/{id}/comprobantes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ComprobanteLeido subir(@PathVariable Long id,
                                  @RequestParam(value = "archivo", required = false) MultipartFile archivo,
                                  Authentication auth) {
        return servicio.subir(id, archivo, usuarioId(auth));
    }

    /** HU-07 criterio 6 y HU-08: envía el comprobante; el sistema lo verifica y devuelve Pagado u Observado. */
    @PostMapping("/api/comprobantes/{id}/enviar")
    public ResultadoEnvio enviar(@PathVariable Long id, @RequestBody EnvioForm form, Authentication auth) {
        return servicio.enviar(id, form, usuarioId(auth));
    }

    /** HU-09: datos del comprobante vigente de un pago (el aprobado o, si no hay, el último observado). */
    @GetMapping("/api/obligaciones/{id}/comprobante")
    public ComprobanteVista consultar(@PathVariable Long id) {
        return servicio.consultar(id);
    }

    /** HU-09 criterio 2: el archivo, para verlo en pantalla (inline) o descargarlo (?descargar=true). */
    @GetMapping("/api/comprobantes/{id}/archivo")
    public ResponseEntity<byte[]> archivo(@PathVariable Long id,
                                          @RequestParam(defaultValue = "false") boolean descargar) {
        ComprobanteInfo info = servicio.infoDelArchivo(id);
        byte[] bytes = servicio.bytesDelArchivo(id);
        ContentDisposition disposicion = (descargar ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(info.getNombreArchivo(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(info.getTipoMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .contentLength(bytes.length)
                .body(bytes);
    }

    private Long usuarioId(Authentication auth) {
        return usuarios.findByCorreoIgnoreCase(auth.getName()).orElseThrow().getId();
    }
}

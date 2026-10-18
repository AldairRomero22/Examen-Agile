package pe.castillo.config;

import org.springframework.http.HttpStatus;

/** Error esperado de negocio: se responde con el estado HTTP indicado y un mensaje para la pantalla. */
public class NegocioException extends RuntimeException {

    private final HttpStatus estado;
    private final String campo;

    public NegocioException(HttpStatus estado, String mensaje) {
        this(estado, mensaje, null);
    }

    /** "campo" es el id del campo del formulario donde la pantalla debe mostrar el mensaje. */
    public NegocioException(HttpStatus estado, String mensaje, String campo) {
        super(mensaje);
        this.estado = estado;
        this.campo = campo;
    }

    public HttpStatus getEstado() { return estado; }
    public String getCampo() { return campo; }
}

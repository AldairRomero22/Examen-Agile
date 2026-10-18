package pe.castillo.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Convierte los errores en respuestas JSON que las pantallas saben mostrar. */
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<Map<String, Object>> negocio(NegocioException e) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("mensaje", e.getMessage());
        if (e.getCampo() != null) {
            cuerpo.put("campo", e.getCampo());
        }
        return ResponseEntity.status(e.getEstado()).body(cuerpo);
    }

    /** Falla de validación: devuelve { errores: { campo: mensaje } } */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException e) {
        Map<String, String> errores = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            String mensaje = fe.getDefaultMessage() == null ? "Valor no válido" : fe.getDefaultMessage();
            // si un campo tiene varios errores, "Obligatorio" tiene prioridad
            if (!errores.containsKey(fe.getField()) || "Obligatorio".equals(mensaje)) {
                errores.put(fe.getField(), mensaje);
            }
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("mensaje", "Revisa los campos marcados");
        cuerpo.put("errores", errores);
        return ResponseEntity.badRequest().body(cuerpo);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> ilegible(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", "Datos no válidos: revisa los valores ingresados"));
    }

    /** Respaldo si dos personas guardan el mismo dato a la vez (índices únicos de la base). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integridad(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensaje", "No se pudo guardar: el dato ya existe o no es válido"));
    }
}

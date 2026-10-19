package pe.castillo.lector;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconoce, en el texto de un documento, a cuál de los beneficiarios registrados corresponde.
 *
 * 1) Por documento (RUC o DNI): es lo más seguro, porque aparece tal cual en facturas y recibos.
 *    Si el documento de un beneficiario aparece como número aparte, es ese. Si hay varios, el que aparece primero
 *    (el emisor suele ir arriba; el RUC del cliente casi nunca está registrado como beneficiario).
 * 2) Si no, por nombre: solo si el nombre completo (sin tildes ni signos, mínimo 6 letras) aparece en el texto
 *    y es un único beneficiario.
 *
 * Si no hay una coincidencia clara, devuelve null: es preferible que el asistente lo elija a que se equivoque.
 * Es una clase sin dependencias de Spring, para poder probarla sola.
 */
public final class CoincidenciaBeneficiario {

    /** Los datos mínimos de un beneficiario para compararlo. */
    public record Candidato(Long id, String nombre, String documento) { }

    private CoincidenciaBeneficiario() { }

    public static Long buscar(String texto, List<Candidato> candidatos) {
        if (texto == null || texto.isBlank() || candidatos == null || candidatos.isEmpty()) {
            return null;
        }
        // 1) por documento
        Long porDocumento = null;
        int posicionMasTemprana = Integer.MAX_VALUE;
        for (Candidato c : candidatos) {
            if (c.documento() == null || c.documento().isBlank()) {
                continue;
            }
            Matcher m = Pattern.compile("(?<!\\d)" + Pattern.quote(c.documento().trim()) + "(?!\\d)").matcher(texto);
            if (m.find() && m.start() < posicionMasTemprana) {
                posicionMasTemprana = m.start();
                porDocumento = c.id();
            }
        }
        if (porDocumento != null) {
            return porDocumento;
        }
        // 2) por nombre, solo si es único
        String textoSimple = " " + ExtractorDatosDocumento.simplificar(texto) + " ";
        Long porNombre = null;
        int coincidencias = 0;
        for (Candidato c : candidatos) {
            String nombre = ExtractorDatosDocumento.simplificar(c.nombre());
            if (nombre.length() >= 6 && textoSimple.contains(" " + nombre + " ")) {
                porNombre = c.id();
                coincidencias++;
            }
        }
        return coincidencias == 1 ? porNombre : null;
    }
}

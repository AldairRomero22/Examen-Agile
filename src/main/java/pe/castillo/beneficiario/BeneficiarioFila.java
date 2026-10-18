package pe.castillo.beneficiario;

/** Datos de un beneficiario tal como los ven las pantallas. */
public record BeneficiarioFila(
        Long id,
        String nombre,
        String documento,
        String tipoDocumento,
        String banco,
        String cuenta,
        String estado) {

    public static BeneficiarioFila de(Beneficiario b) {
        String tipo = b.getDocumento().length() == 11 ? "RUC" : "DNI";
        return new BeneficiarioFila(
                b.getId(), b.getNombre(), b.getDocumento(), tipo,
                b.getBanco(), b.getCuenta(), b.isActivo() ? "Activo" : "Inactivo");
    }
}

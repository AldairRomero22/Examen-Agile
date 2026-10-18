package pe.castillo.comprobante;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComprobanteRepository extends JpaRepository<Comprobante, Long> {

    /** HU-08 regla 3: ¿ese número de operación ya está aprobado en otro pago? */
    boolean existsByNroOperacionEnviadoAndEstadoAndObligacionIdNot(String nroOperacion, String estado, Long obligacionId);

    /** HU-08 regla 4: ¿ese mismo archivo (misma huella SHA-256) ya está aprobado en otro pago? */
    boolean existsByHashSha256AndEstadoAndObligacionIdNot(String hash, String estado, Long obligacionId);

    /** Datos sin el archivo. */
    Optional<ComprobanteInfo> findProjectedById(Long id);

    /** HU-09: el comprobante aprobado de un pago. */
    Optional<ComprobanteInfo> findFirstByObligacionIdAndEstadoOrderByIdDesc(Long obligacionId, String estado);

    /** HU-09: si no hay aprobado, el último que se envió (observado). */
    Optional<ComprobanteInfo> findFirstByObligacionIdAndEstadoInOrderByIdDesc(Long obligacionId, Collection<String> estados);

    /** Solo los bytes del archivo, para mostrarlo o descargarlo. */
    @Query("select c.archivo from Comprobante c where c.id = :id")
    byte[] archivoDe(@Param("id") Long id);
}

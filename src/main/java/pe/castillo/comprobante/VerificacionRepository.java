package pe.castillo.comprobante;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificacionRepository extends JpaRepository<Verificacion, Long> {

    /** La última verificación de un comprobante. */
    Optional<Verificacion> findFirstByComprobanteIdOrderByIdDesc(Long comprobanteId);
}

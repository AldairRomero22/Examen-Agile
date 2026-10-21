package pe.castillo.prestamo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PrestamoRepository extends JpaRepository<Prestamo, Long> {

    List<Prestamo> findByActivoTrueOrderByFechaDesembolsoDescIdDesc();

    Optional<Prestamo> findByIdAndActivoTrue(Long id);

    boolean existsByNroCreditoIgnoreCaseAndActivoTrue(String nroCredito);
}

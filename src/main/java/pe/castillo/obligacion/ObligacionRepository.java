package pe.castillo.obligacion;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ObligacionRepository extends JpaRepository<Obligacion, Long> {

    List<Obligacion> findByBeneficiarioIdAndActivoTrueOrderByFechaVencimientoAscIdAsc(Long beneficiarioId);

    List<Obligacion> findByActivoTrueAndFechaVencimientoBetweenOrderByFechaVencimientoAscIdAsc(
            LocalDate desde, LocalDate hasta);
}

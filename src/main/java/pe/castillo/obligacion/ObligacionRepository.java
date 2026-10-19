package pe.castillo.obligacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ObligacionRepository extends JpaRepository<Obligacion, Long> {

    Optional<Obligacion> findByIdAndActivoTrue(Long id);

    List<Obligacion> findByBeneficiarioIdAndActivoTrueOrderByFechaVencimientoAscIdAsc(Long beneficiarioId);

    /** Última obligación (por fecha de vencimiento) de un beneficiario: sirve para sugerir concepto y categoría. */
    Optional<Obligacion> findFirstByBeneficiarioIdAndActivoTrueOrderByFechaVencimientoDescIdDesc(Long beneficiarioId);

    /** Evita duplicados: una obligación activa del mismo beneficiario con el mismo monto y el mismo vencimiento. */
    Optional<Obligacion> findFirstByBeneficiarioIdAndMontoAndFechaVencimientoAndActivoTrue(
            Long beneficiarioId, BigDecimal monto, LocalDate fechaVencimiento);

    /** Obligaciones activas de un beneficiario que vencen en un rango (para detectar el mismo concepto en el mismo mes). */
    List<Obligacion> findByBeneficiarioIdAndActivoTrueAndFechaVencimientoBetweenOrderByFechaVencimientoAscIdAsc(
            Long beneficiarioId, LocalDate desde, LocalDate hasta);

    List<Obligacion> findByActivoTrueAndFechaVencimientoBetweenOrderByFechaVencimientoAscIdAsc(
            LocalDate desde, LocalDate hasta);

    /**
     * HU-05: pagos que generan alerta (Por vencer o Vencido), de cualquier mes.
     * Los Pagados, En espera, En verificación y Observados no generan alertas por fecha.
     * Ordenados del más urgente (más atrasado) al menos urgente.
     */
    @Query("""
            select o from Obligacion o
            where o.activo = true
              and o.enEspera = false
              and o.estadoPago = 'PENDIENTE'
              and o.fechaVencimiento <= :hasta
            order by o.fechaVencimiento asc, o.id asc
            """)
    List<Obligacion> buscarUrgentes(@Param("hasta") LocalDate hasta);
}

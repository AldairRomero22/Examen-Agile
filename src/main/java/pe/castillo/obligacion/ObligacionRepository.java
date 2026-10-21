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

    /** Última obligación (por fecha de vencimiento) pagada a alguien: sirve para sugerir concepto y categoría. */
    Optional<Obligacion> findFirstByBeneficiarioNombreIgnoreCaseAndActivoTrueOrderByFechaVencimientoDescIdDesc(
            String beneficiarioNombre);

    /** Evita duplicados: obligaciones activas con el mismo monto y el mismo vencimiento (se compara a quién se paga). */
    List<Obligacion> findByMontoAndFechaVencimientoAndActivoTrue(BigDecimal monto, LocalDate fechaVencimiento);

    /** A quiénes ya se les registró algún pago, para sugerirlos al escribir y reconocerlos en un documento. */
    @Query("select distinct o.beneficiarioNombre from Obligacion o where o.activo = true order by o.beneficiarioNombre")
    List<String> nombresBeneficiarios();

    /** Todos los pagos activos de todos los meses. */
    List<Obligacion> findByActivoTrueOrderByFechaVencimientoAscIdAsc();

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

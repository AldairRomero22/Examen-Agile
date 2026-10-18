package pe.castillo.beneficiario;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BeneficiarioRepository extends JpaRepository<Beneficiario, Long> {

    List<Beneficiario> findByActivoTrueOrderByNombreAsc();

    boolean existsByDocumento(String documento);
}

package pe.torreblanca.backend.repository;

import pe.torreblanca.backend.entity.Boleta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface BoletaRepository extends JpaRepository<Boleta, Integer> {

    Optional<Boleta> findByPagoId(Integer pagoId);

    @Query("SELECT b FROM Boleta b ORDER BY b.fechaEmision DESC")
    List<Boleta> findAllOrdenadas();

    // Boletas del residente por su ID
    @Query("SELECT b FROM Boleta b WHERE b.pago.pagador.id = :usuarioId ORDER BY b.fechaEmision DESC")
    List<Boleta> findByPagadorId(Integer usuarioId);

    // Para generar número único
    long count();
}

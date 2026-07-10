package pe.torreblanca.backend.repository;

import pe.torreblanca.backend.entity.FondoMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.util.List;

public interface FondoMovimientoRepository extends JpaRepository<FondoMovimiento, Integer> {

    @Query("SELECT m FROM FondoMovimiento m WHERE m.proyecto.id = :proyectoId ORDER BY m.fecha DESC, m.createdAt DESC")
    List<FondoMovimiento> findByProyectoId(Integer proyectoId);

    @Query("SELECT m FROM FondoMovimiento m WHERE m.proyecto IS NULL ORDER BY m.fecha DESC, m.createdAt DESC")
    List<FondoMovimiento> findGenerales();

    @Query("SELECT COALESCE(SUM(m.monto),0) FROM FondoMovimiento m WHERE m.proyecto.id = :proyectoId AND m.tipo = :tipo")
    BigDecimal sumByProyectoAndTipo(Integer proyectoId, String tipo);

    @Query("SELECT COALESCE(SUM(m.monto),0) FROM FondoMovimiento m WHERE m.tipo = :tipo")
    BigDecimal sumByTipo(String tipo);
}

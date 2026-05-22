package pe.torreblanca.backend.repository;

import pe.torreblanca.backend.entity.Gasto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface GastoRepository extends JpaRepository<Gasto, Integer> {

    @Query("SELECT g FROM Gasto g WHERE g.mes = :mes AND g.anio = :anio ORDER BY g.fechaGasto DESC")
    List<Gasto> findByMesAndAnio(Integer mes, Integer anio);

    @Query("SELECT g FROM Gasto g ORDER BY g.anio DESC, g.mes DESC, g.fechaGasto DESC")
    List<Gasto> findAllOrdenados();

    @Query("SELECT COALESCE(SUM(g.monto), 0) FROM Gasto g WHERE g.mes = :mes AND g.anio = :anio")
    java.math.BigDecimal sumByMesAndAnio(Integer mes, Integer anio);
}

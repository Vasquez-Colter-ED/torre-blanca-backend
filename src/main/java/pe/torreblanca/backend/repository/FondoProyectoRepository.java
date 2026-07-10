package pe.torreblanca.backend.repository;

import pe.torreblanca.backend.entity.FondoProyecto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface FondoProyectoRepository extends JpaRepository<FondoProyecto, Integer> {

    @Query("SELECT p FROM FondoProyecto p ORDER BY p.createdAt DESC")
    List<FondoProyecto> findAllOrdenado();

    @Query("SELECT COUNT(p) FROM FondoProyecto p WHERE p.estado = 'ACTIVO'")
    long contarActivos();
}

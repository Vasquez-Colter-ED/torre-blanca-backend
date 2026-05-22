package pe.torreblanca.backend.repository;

import pe.torreblanca.backend.entity.CategoriaGasto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CategoriaGastoRepository extends JpaRepository<CategoriaGasto, Integer> {
    List<CategoriaGasto> findByEstadoTrue();
}

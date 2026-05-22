package pe.torreblanca.backend.repository;

import pe.torreblanca.backend.entity.ConfiguracionMantenimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ConfiguracionMantenimientoRepository extends JpaRepository<ConfiguracionMantenimiento, Integer> {
    Optional<ConfiguracionMantenimiento> findByMesAndAnio(Integer mes, Integer anio);
    boolean existsByMesAndAnio(Integer mes, Integer anio);
}

package pe.torreblanca.backend.repository;

import pe.torreblanca.backend.entity.UsuarioPermiso;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UsuarioPermisoRepository extends JpaRepository<UsuarioPermiso, Integer> {
    List<UsuarioPermiso> findByUsuarioIdAndEstadoTrue(Integer usuarioId);
}

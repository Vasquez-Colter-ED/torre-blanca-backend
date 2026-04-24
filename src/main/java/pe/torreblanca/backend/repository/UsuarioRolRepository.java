package pe.torreblanca.backend.repository;

import pe.torreblanca.backend.entity.UsuarioRol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface UsuarioRolRepository extends JpaRepository<UsuarioRol, Integer> {

    // Roles activos vigentes (fecha_fin null o futura)
    @Query("SELECT ur FROM UsuarioRol ur WHERE ur.usuario.id = :usuarioId AND ur.estado = true " +
           "AND (ur.fechaFin IS NULL OR ur.fechaFin >= CURRENT_DATE)")
    List<UsuarioRol> findRolesActivosByUsuarioId(Integer usuarioId);
}

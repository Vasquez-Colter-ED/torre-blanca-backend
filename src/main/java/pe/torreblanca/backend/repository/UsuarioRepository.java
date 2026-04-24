package pe.torreblanca.backend.repository;

import pe.torreblanca.backend.entity.EstadoUsuario;
import pe.torreblanca.backend.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    Optional<Usuario> findByEmail(String email);
    List<Usuario> findByEstado(EstadoUsuario estado);
    boolean existsByEmail(String email);
    boolean existsByDni(String dni);
}

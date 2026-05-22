package pe.torreblanca.backend.repository;

import pe.torreblanca.backend.entity.PropietarioDepartamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface PropietarioDepartamentoRepository extends JpaRepository<PropietarioDepartamento, Integer> {

    @Query("SELECT pd FROM PropietarioDepartamento pd WHERE pd.usuario.id = :usuarioId AND pd.estado = true")
    List<PropietarioDepartamento> findActivosByUsuarioId(Integer usuarioId);

    @Query("SELECT pd FROM PropietarioDepartamento pd WHERE pd.departamento.id = :deptoId AND pd.estado = true")
    Optional<PropietarioDepartamento> findActivoByDepartamentoId(Integer deptoId);
}

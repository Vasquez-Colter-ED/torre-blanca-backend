package pe.torreblanca.backend.repository;

import pe.torreblanca.backend.entity.InquilinoDepartamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface InquilinoDepartamentoRepository extends JpaRepository<InquilinoDepartamento, Integer> {

    @Query("SELECT i FROM InquilinoDepartamento i WHERE i.usuario.id = :usuarioId AND i.estado = true")
    List<InquilinoDepartamento> findActivosByUsuarioId(Integer usuarioId);

    @Query("SELECT i FROM InquilinoDepartamento i WHERE i.departamento.id = :deptoId AND i.estado = true")
    List<InquilinoDepartamento> findActivosByDepartamentoId(Integer deptoId);
}

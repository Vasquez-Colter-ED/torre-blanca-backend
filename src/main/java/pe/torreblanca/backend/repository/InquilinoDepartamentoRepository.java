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

    // Todos los inquilinos que alguna vez tuvo el depto (activos e históricos)
    @Query("SELECT i FROM InquilinoDepartamento i WHERE i.departamento.id = :deptoId ORDER BY i.fechaInicio DESC")
    List<InquilinoDepartamento> findTodosByDepartamentoId(Integer deptoId);
}

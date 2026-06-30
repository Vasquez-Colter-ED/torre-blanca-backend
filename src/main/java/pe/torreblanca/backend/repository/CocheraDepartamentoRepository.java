package pe.torreblanca.backend.repository;

import pe.torreblanca.backend.entity.CocheraDepartamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface CocheraDepartamentoRepository extends JpaRepository<CocheraDepartamento, Integer> {

    @Query("SELECT cd FROM CocheraDepartamento cd WHERE cd.departamento.id = :deptoId AND cd.estado = true")
    List<CocheraDepartamento> findActivasByDepartamentoId(Integer deptoId);

    @Query("SELECT cd FROM CocheraDepartamento cd WHERE cd.cochera.id = :cocheraId AND cd.estado = true")
    List<CocheraDepartamento> findActivasByCocheraId(Integer cocheraId);
}

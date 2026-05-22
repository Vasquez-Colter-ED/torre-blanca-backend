package pe.torreblanca.backend.repository;

import pe.torreblanca.backend.entity.Departamento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DepartamentoRepository extends JpaRepository<Departamento, Integer> {
    List<Departamento> findAllByOrderByNumeroAsc();
    Optional<Departamento> findByNumero(String numero);
}

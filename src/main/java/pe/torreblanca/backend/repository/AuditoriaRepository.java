package pe.torreblanca.backend.repository;

import pe.torreblanca.backend.entity.Auditoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Integer> {

    @Query("SELECT a FROM Auditoria a ORDER BY a.createdAt DESC")
    List<Auditoria> findAllOrdenado();
}

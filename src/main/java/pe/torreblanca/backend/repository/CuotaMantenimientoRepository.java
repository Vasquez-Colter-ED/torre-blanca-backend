package pe.torreblanca.backend.repository;

import pe.torreblanca.backend.entity.CuotaMantenimiento;
import pe.torreblanca.backend.entity.EstadoCuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface CuotaMantenimientoRepository extends JpaRepository<CuotaMantenimiento, Integer> {

    List<CuotaMantenimiento> findByConfiguracionId(Integer configuracionId);

    List<CuotaMantenimiento> findByConfiguracionIdAndEstado(Integer configuracionId, EstadoCuota estado);

    @Query("SELECT c FROM CuotaMantenimiento c WHERE c.configuracion.mes = :mes AND c.configuracion.anio = :anio")
    List<CuotaMantenimiento> findByMesAndAnio(Integer mes, Integer anio);

    @Query("SELECT c FROM CuotaMantenimiento c WHERE c.responsablePago.id = :usuarioId ORDER BY c.configuracion.anio DESC, c.configuracion.mes DESC")
    List<CuotaMantenimiento> findByResponsablePagoId(Integer usuarioId);

    Optional<CuotaMantenimiento> findByDepartamentoIdAndConfiguracionId(Integer departamentoId, Integer configuracionId);

    boolean existsByConfiguracionId(Integer configuracionId);
}

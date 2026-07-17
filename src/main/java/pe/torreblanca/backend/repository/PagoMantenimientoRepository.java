package pe.torreblanca.backend.repository;

import pe.torreblanca.backend.entity.EstadoPago;
import pe.torreblanca.backend.entity.PagoMantenimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface PagoMantenimientoRepository extends JpaRepository<PagoMantenimiento, Integer> {

    List<PagoMantenimiento> findByCuotaId(Integer cuotaId);

    List<PagoMantenimiento> findByEstado(EstadoPago estado);

    List<PagoMantenimiento> findByPagadorId(Integer pagadorId);

    List<PagoMantenimiento> findByLoteId(String loteId);

    @Query("SELECT p FROM PagoMantenimiento p WHERE p.cuota.configuracion.mes = :mes AND p.cuota.configuracion.anio = :anio ORDER BY p.createdAt DESC")
    List<PagoMantenimiento> findByMesAndAnio(Integer mes, Integer anio);

    @Query("SELECT p FROM PagoMantenimiento p WHERE p.estado = 'PENDIENTE_VERIFICACION' ORDER BY p.createdAt ASC")
    List<PagoMantenimiento> findPendientesVerificacion();

    @Query("SELECT p FROM PagoMantenimiento p ORDER BY p.createdAt DESC")
    List<PagoMantenimiento> findAllOrdenadoDesc();

    // Total histórico de todo lo recaudado por mantenimiento (solo pagos ya
    // VERIFICADOS) — se usa para calcular el saldo real del fondo, que debe
    // reflejar el efectivo real disponible en la cuenta de la residencial
    @Query("SELECT COALESCE(SUM(p.monto), 0) FROM PagoMantenimiento p WHERE p.estado = 'VERIFICADO'")
    java.math.BigDecimal sumVerificadoTotal();
}

package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ConfiguracionResponse {
    private Integer id;
    private Integer mes;
    private Integer anio;
    private String tipoCalculo;
    private BigDecimal costoPorM2;
    private BigDecimal totalMensual;
    private BigDecimal montoFijo;
    private String observaciones;
    // true si al menos un departamento ya registró un pago (total o parcial)
    // sobre esta configuración — en ese caso ya no se puede editar, para no
    // alterar retroactivamente lo que esa persona ya pagó
    private boolean tienePagos;
    private int deptosConPago;
}

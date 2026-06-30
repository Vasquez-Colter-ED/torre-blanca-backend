package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ConfigurarMesRequest {
    private Integer mes;
    private Integer anio;
    private BigDecimal costoPorM2;
    private BigDecimal totalMensual;   // para tipoCalculo = PORCENTAJE
    private BigDecimal montoFijo;      // para tipoCalculo = MONTO_FIJO (todos pagan lo mismo)
    private String tipoCalculo;        // COSTO_M2 / PORCENTAJE / MONTO_FIJO
    private BigDecimal totalGastosEstimados;
    private String observaciones;
}

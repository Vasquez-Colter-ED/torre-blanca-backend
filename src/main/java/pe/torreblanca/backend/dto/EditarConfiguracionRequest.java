package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class EditarConfiguracionRequest {
    private String tipoCalculo;          // COSTO_M2 / PORCENTAJE / MONTO_FIJO
    private BigDecimal costoPorM2;       // usado si tipoCalculo = COSTO_M2
    private BigDecimal totalMensual;     // usado si tipoCalculo = PORCENTAJE
    private BigDecimal montoFijo;        // usado si tipoCalculo = MONTO_FIJO
    private BigDecimal totalGastosEstimados;
    private String observaciones;
}

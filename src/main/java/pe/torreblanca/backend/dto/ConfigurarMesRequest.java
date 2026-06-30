package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ConfigurarMesRequest {
    private Integer mes;
    private Integer anio;
    private BigDecimal costoPorM2;
    private BigDecimal totalMensual;   // para tipoCalculo = PORCENTAJE
    private String tipoCalculo;        // COSTO_M2 (default) o PORCENTAJE
    private BigDecimal totalGastosEstimados;
    private String observaciones;
}

package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ConfigurarMesRequest {
    private Integer mes;
    private Integer anio;
    private BigDecimal costoPorM2;
    private BigDecimal totalGastosEstimados;
    private String observaciones;
}

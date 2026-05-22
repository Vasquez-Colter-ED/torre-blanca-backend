package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class EditarConfiguracionRequest {
    private BigDecimal costoPorM2;
    private BigDecimal totalGastosEstimados;
    private String observaciones;
}

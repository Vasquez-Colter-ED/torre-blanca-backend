package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CocheraInfo {
    private Integer asignacionId;  // ID en cocheras_departamentos
    private Integer cocheraId;
    private String  numero;        // E01, E02...
    private BigDecimal porcentaje;
    private BigDecimal metros;
}

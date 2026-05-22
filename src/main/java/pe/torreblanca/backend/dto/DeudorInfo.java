package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class DeudorInfo {
    private String numeroDepartamento;
    private Integer piso;
    private BigDecimal montoPendiente;
    private String residentesNombres;
}

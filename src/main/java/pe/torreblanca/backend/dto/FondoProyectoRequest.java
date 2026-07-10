package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class FondoProyectoRequest {
    private String nombre;
    private String descripcion;
    private BigDecimal metaMonto;
    private String fechaInicio; // yyyy-MM-dd
}

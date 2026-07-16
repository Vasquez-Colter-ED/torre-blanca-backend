package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PagadorInfo {
    private String numeroDepartamento;
    private Integer piso;
    // Quién vive en el depto (puede diferir de quién pagó)
    private String residentesNombres;
    // Quién realmente registró el/los pago(s) verificados de esta cuota
    private String pagadoPorNombre;
    private BigDecimal montoPagado;
    // Si la cuota se pagó con más de un método (ej. parcial en efectivo +
    // resto por transferencia), se guarda como "MULTIPLE"
    private String metodoPago;
    private LocalDateTime fechaPago;
}

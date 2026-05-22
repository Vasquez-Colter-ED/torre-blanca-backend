package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CuotaDetalleResponse {
    private Integer cuotaId;
    private String numeroDepartamento;
    private Integer piso;
    private BigDecimal metrosCuadrados;
    private BigDecimal montoCalculado;
    private String estadoCuota;
    // Todos los usuarios del depto
    private List<String> residentesNombres;
    private List<PagoDetalleResponse> pagos;
}

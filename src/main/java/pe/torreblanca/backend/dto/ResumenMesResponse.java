package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ResumenMesResponse {
    private Integer mes;
    private Integer anio;
    private BigDecimal costoPorM2;
    private BigDecimal totalEsperado;
    private BigDecimal totalRecaudado;
    private BigDecimal totalPendiente;
    private Integer totalDepartamentos;
    private Integer pagados;
    private Integer parciales;
    private Integer pendientes;
    private Integer vencidos;
    private Integer enVerificacion;
    private List<CuotaDetalleResponse> cuotas;
}

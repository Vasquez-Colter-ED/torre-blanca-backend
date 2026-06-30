package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class DepartamentoDetalleResponse {
    private Integer id;
    private String numero;
    private Integer piso;
    private BigDecimal metrosCuadrados;
    private BigDecimal porcentaje;
    private String tipo;
    private String descripcion;
    private String estado;
    private String propietarioNombre;
    private String propietarioEmail;
    private Integer propietarioId;
    private Integer propietarioAsignacionId; // ID del registro en propietarios_departamentos
    private List<InquilinoInfo> inquilinos;
    private List<CocheraInfo> cocheras;
}

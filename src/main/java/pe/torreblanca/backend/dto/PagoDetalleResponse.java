package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PagoDetalleResponse {
    private Integer pagoId;
    private String loteId;
    private String pagadorNombre;
    private BigDecimal monto;
    private String metodoPago;
    private String numeroOperacion;
    private String voucherUrl;
    private String estado;
    private String observaciones;
    private LocalDateTime fechaPago;
    private LocalDateTime fechaVerificacion;
    private String verificadoPorNombre;
    private String verificadoPorCargo;
    private String registradoPor;          // RESIDENTE / DIRECTIVO / SISTEMA
    private String registradoPorNombre;     // qué directivo registró manualmente (si aplica)
    private String registradoPorCargo;      // su cargo en el momento del registro
    // Info del departamento
    private String numeroDepartamento;
    private Integer piso;
    private Integer mes;
    private Integer anio;
}

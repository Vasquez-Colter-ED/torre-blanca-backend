package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AuditoriaPagoResponse {
    private Integer pagoId;
    private String loteId;
    private List<String> loteMesesCubre; // otros meses cubiertos por el mismo comprobante (si aplica)

    // Departamento / cuota
    private String numeroDepartamento;
    private Integer piso;
    private Integer mes;
    private Integer anio;

    // Monto y método
    private BigDecimal monto;
    private String metodoPago;
    private Boolean esPasarela; // true si fue pago con tarjeta vía Mercado Pago

    // Pagador (quién debía el dinero)
    private String pagadorNombre;

    // Registro — quién metió el dato al sistema y cuándo
    private LocalDateTime fechaRegistro;
    private String registradoPor;       // RESIDENTE / DIRECTIVO / SISTEMA
    private String registradoPorNombre; // null si fue el propio residente
    private String registradoPorCargo;  // cargo del directivo en ese momento, si aplica

    // Verificación — quién aprobó/rechazó y cuándo
    private String estado; // PENDIENTE_VERIFICACION / VERIFICADO / RECHAZADO
    private LocalDateTime fechaVerificacion;
    private String verificadoPorNombre;
    private String verificadoPorCargo;

    private String observaciones;
}

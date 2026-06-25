package pe.torreblanca.backend.dto;

import lombok.Data;

@Data
public class PagoMercadoPagoRequest {
    private Integer cuotaId;
    private String token;
    private String email;
    private String metodoPago;
    private Integer cuotas;
    private Integer pagadorId;
}

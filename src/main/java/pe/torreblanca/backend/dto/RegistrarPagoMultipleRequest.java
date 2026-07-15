package pe.torreblanca.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class RegistrarPagoMultipleRequest {
    private List<Integer> cuotaIds;
    private String metodoPago;       // TRANSFERENCIA / EFECTIVO
    private String numeroOperacion;  // opcional, uno solo para todo el lote
    private String voucherUrl;       // un solo comprobante para todo el lote
    private String observaciones;
    private Integer pagadorId;       // solo directivos
}

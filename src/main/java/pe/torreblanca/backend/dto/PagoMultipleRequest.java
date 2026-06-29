package pe.torreblanca.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class PagoMultipleRequest {
    private List<Integer> cuotaIds;
    private String token;
    private String email;
    private String metodoPago;
    private Integer cuotas;
    private Integer pagadorId;
}

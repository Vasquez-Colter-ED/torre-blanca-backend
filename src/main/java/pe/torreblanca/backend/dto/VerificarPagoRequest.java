package pe.torreblanca.backend.dto;

import lombok.Data;

@Data
public class VerificarPagoRequest {
    private String accion; // "APROBAR" o "RECHAZAR"
    private String observaciones;
}

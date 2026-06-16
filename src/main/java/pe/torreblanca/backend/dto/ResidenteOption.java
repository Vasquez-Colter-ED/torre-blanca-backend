package pe.torreblanca.backend.dto;

import lombok.Data;

@Data
public class ResidenteOption {
    private Integer id;
    private String nombre;
    private String tipo; // PROPIETARIO o INQUILINO
}

package pe.torreblanca.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RolInfo {
    private Integer asignacionId; // ID del registro en usuarios_roles (para poder revocarlo)
    private Integer rolId;
    private String nombre;
}

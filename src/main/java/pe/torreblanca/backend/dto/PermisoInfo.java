package pe.torreblanca.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PermisoInfo {
    private Integer asignacionId; // ID del registro en usuarios_permisos (para poder revocarlo)
    private String modulo;
    private String permiso;
}

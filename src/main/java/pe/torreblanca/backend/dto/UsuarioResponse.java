package pe.torreblanca.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class UsuarioResponse {
    private Integer id;
    private String nombre;
    private String apellido;
    private String dni;
    private String email;
    private String telefono;
    private String estado;
    private List<RolInfo> roles;
    private List<PermisoInfo> permisosExtra;
}

package pe.torreblanca.backend.dto;

import lombok.Data;

@Data
public class CrearUsuarioRequest {
    private String nombre;
    private String apellido;
    private String dni;
    private String email;
    private String telefono;
    private String password;
    // Cargo directivo opcional (PRESIDENTE/SECRETARIO/TESORERO)
    private Integer cargoDirectivoId;
    // Asignación al departamento (opcional — se puede crear un usuario
    // sin depto, por ejemplo para personal administrativo)
    private Integer departamentoId;
    private String tipoResidencia; // "PROPIETARIO" o "INQUILINO"
    // Para mantener compatibilidad con código anterior
    public Integer getRolId() { return cargoDirectivoId; }
}

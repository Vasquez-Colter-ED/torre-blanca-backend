package pe.torreblanca.backend.dto;
import lombok.Data;
@Data
public class NuevaPasswordRequest {
    private String email;
    private String nuevaPassword;
}

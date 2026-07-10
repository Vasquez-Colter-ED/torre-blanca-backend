package pe.torreblanca.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import pe.torreblanca.backend.dto.AuditoriaGeneralResponse;
import pe.torreblanca.backend.entity.Auditoria;
import pe.torreblanca.backend.repository.AuditoriaRepository;
import pe.torreblanca.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// ══════════════════════════════════════════════════════════════════
// Registro de auditoría general del sistema (tabla `auditoria`).
//
// Este servicio es de "mejor esfuerzo": si por algún motivo falla al
// guardar el registro de auditoría, NUNCA debe tumbar la operación
// real que se estaba auditando (crear un gasto, editar un usuario,
// etc.). Por eso todo está envuelto en try/catch dentro de registrar().
// ══════════════════════════════════════════════════════════════════
@Service
public class AuditoriaService {

    @Autowired private AuditoriaRepository auditoriaRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    public void registrar(Integer usuarioId, String accion, String tablaAfectada, Integer registroId,
                           Map<String, Object> datosAnteriores, Map<String, Object> datosNuevos) {
        try {
            Auditoria a = new Auditoria();
            if (usuarioId != null) usuarioRepository.findById(usuarioId).ifPresent(a::setUsuario);
            a.setAccion(accion);
            a.setTablaAfectada(tablaAfectada);
            a.setRegistroId(registroId);
            if (datosAnteriores != null && !datosAnteriores.isEmpty())
                a.setDatosAnteriores(mapper.writeValueAsString(datosAnteriores));
            if (datosNuevos != null && !datosNuevos.isEmpty())
                a.setDatosNuevos(mapper.writeValueAsString(datosNuevos));
            auditoriaRepository.save(a);
        } catch (Exception e) {
            System.err.println("No se pudo registrar auditoría (" + accion + "): " + e.getMessage());
        }
    }

    // Sobrecarga para acciones simples sin datos antes/después
    public void registrar(Integer usuarioId, String accion, String tablaAfectada, Integer registroId) {
        registrar(usuarioId, accion, tablaAfectada, registroId, null, null);
    }

    public List<AuditoriaGeneralResponse> listar() {
        return auditoriaRepository.findAllOrdenado().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    private AuditoriaGeneralResponse toResponse(Auditoria a) {
        AuditoriaGeneralResponse r = new AuditoriaGeneralResponse();
        r.setId(a.getId());
        r.setFecha(a.getCreatedAt());
        if (a.getUsuario() != null) {
            r.setUsuarioId(a.getUsuario().getId());
            r.setUsuarioNombre(a.getUsuario().getNombre() + " " + a.getUsuario().getApellido());
        }
        r.setAccion(a.getAccion());
        r.setTablaAfectada(a.getTablaAfectada());
        r.setRegistroId(a.getRegistroId());
        r.setDatosAnteriores(a.getDatosAnteriores());
        r.setDatosNuevos(a.getDatosNuevos());
        return r;
    }
}

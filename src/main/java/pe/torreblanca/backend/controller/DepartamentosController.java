package pe.torreblanca.backend.controller;

import pe.torreblanca.backend.dto.*;
import pe.torreblanca.backend.repository.UsuarioRepository;
import pe.torreblanca.backend.security.JwtUtil;
import pe.torreblanca.backend.service.PagosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/departamentos")
@CrossOrigin(origins = "*")
public class DepartamentosController {

    @Autowired private PagosService pagosService;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UsuarioRepository usuarioRepository;

    private Integer getSolicitanteId(String authHeader) {
        String email = jwtUtil.getEmailFromToken(authHeader.substring(7));
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado")).getId();
    }

    @GetMapping
    public ResponseEntity<List<DepartamentoDetalleResponse>> listar() {
        return ResponseEntity.ok(pagosService.listarDepartamentos());
    }

    @PostMapping("/asignar-propietario")
    public ResponseEntity<?> asignarPropietario(@RequestBody AsignarDepartamentoRequest request,
                                                @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(pagosService.asignarPropietario(request, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @PostMapping("/asignar-inquilino")
    public ResponseEntity<?> asignarInquilino(@RequestBody AsignarInquilinoRequest request,
                                              @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(pagosService.asignarInquilino(request, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @DeleteMapping("/propietario/{id}")
    public ResponseEntity<?> quitarPropietario(@PathVariable Integer id,
                                               @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(pagosService.quitarPropietario(id, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @DeleteMapping("/inquilino/{id}")
    public ResponseEntity<?> quitarInquilino(@PathVariable Integer id,
                                             @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(pagosService.quitarInquilino(id, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }
}

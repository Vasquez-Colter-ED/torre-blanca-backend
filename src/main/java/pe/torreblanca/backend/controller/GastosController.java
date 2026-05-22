package pe.torreblanca.backend.controller;

import pe.torreblanca.backend.dto.*;
import pe.torreblanca.backend.entity.CategoriaGasto;
import pe.torreblanca.backend.repository.UsuarioRepository;
import pe.torreblanca.backend.security.JwtUtil;
import pe.torreblanca.backend.service.GastosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/gastos")
@CrossOrigin(origins = "*")
public class GastosController {

    @Autowired private GastosService gastosService;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UsuarioRepository usuarioRepository;

    private Integer getSolicitanteId(String auth) {
        String email = jwtUtil.getEmailFromToken(auth.substring(7));
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado")).getId();
    }

    @GetMapping
    public ResponseEntity<List<GastoResponse>> listarTodos() {
        return ResponseEntity.ok(gastosService.listarTodos());
    }

    @GetMapping("/mes/{anio}/{mes}")
    public ResponseEntity<List<GastoResponse>> listarPorMes(@PathVariable Integer anio,
                                                             @PathVariable Integer mes) {
        return ResponseEntity.ok(gastosService.listarPorMes(mes, anio));
    }

    @GetMapping("/categorias")
    public ResponseEntity<List<CategoriaGasto>> listarCategorias() {
        return ResponseEntity.ok(gastosService.listarCategorias());
    }

    @GetMapping("/total/{anio}/{mes}")
    public ResponseEntity<?> totalPorMes(@PathVariable Integer anio, @PathVariable Integer mes) {
        return ResponseEntity.ok(gastosService.totalPorMes(mes, anio));
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody GastoRequest request,
                                   @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(gastosService.crear(request, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> editar(@PathVariable Integer id,
                                    @RequestBody GastoRequest request,
                                    @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(gastosService.editar(id, request, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Integer id,
                                      @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(gastosService.eliminar(id, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }
}

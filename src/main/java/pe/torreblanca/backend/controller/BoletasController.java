package pe.torreblanca.backend.controller;

import pe.torreblanca.backend.dto.BoletaResponse;
import pe.torreblanca.backend.repository.UsuarioRepository;
import pe.torreblanca.backend.repository.UsuarioRolRepository;
import pe.torreblanca.backend.security.JwtUtil;
import pe.torreblanca.backend.service.BoletasService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/boletas")
@CrossOrigin(origins = "*")
public class BoletasController {

    @Autowired private BoletasService boletasService;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioRolRepository usuarioRolRepository;

    private Integer getSolicitanteId(String auth) {
        String email = jwtUtil.getEmailFromToken(auth.substring(7));
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado")).getId();
    }

    private boolean esDirectivo(Integer usuarioId) {
        return usuarioRolRepository.findRolesActivosByUsuarioId(usuarioId)
                .stream().anyMatch(ur -> ur.getRol().getEsDirectivo());
    }

    // Directivos ven todas las boletas, residentes ven las suyas
    @GetMapping
    public ResponseEntity<List<BoletaResponse>> listar(@RequestHeader("Authorization") String auth) {
        Integer id = getSolicitanteId(auth);
        if (esDirectivo(id)) return ResponseEntity.ok(boletasService.listarTodas());
        return ResponseEntity.ok(boletasService.misBoletas(id));
    }

    @GetMapping("/mias")
    public ResponseEntity<List<BoletaResponse>> misBoletas(@RequestHeader("Authorization") String auth) {
        return ResponseEntity.ok(boletasService.misBoletas(getSolicitanteId(auth)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Integer id) {
        try { return ResponseEntity.ok(boletasService.obtenerPorId(id)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }
}

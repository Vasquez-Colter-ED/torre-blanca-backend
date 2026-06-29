package pe.torreblanca.backend.controller;

import pe.torreblanca.backend.dto.PagoMercadoPagoRequest;
import pe.torreblanca.backend.dto.PagoMultipleRequest;
import pe.torreblanca.backend.repository.UsuarioRepository;
import pe.torreblanca.backend.security.JwtUtil;
import pe.torreblanca.backend.service.MercadoPagoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mercadopago")
@CrossOrigin(origins = "*")
public class MercadoPagoController {

    @Autowired private MercadoPagoService mercadoPagoService;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UsuarioRepository usuarioRepository;

    private Integer getSolicitanteId(String authHeader) {
        String email = jwtUtil.getEmailFromToken(authHeader.substring(7));
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado")).getId();
    }

    @PostMapping("/pagar")
    public ResponseEntity<?> pagar(@RequestBody PagoMercadoPagoRequest request,
                                   @RequestHeader("Authorization") String auth) {
        try {
            return ResponseEntity.ok(mercadoPagoService.procesarPago(request, getSolicitanteId(auth)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/pagar-multiple")
    public ResponseEntity<?> pagarMultiple(@RequestBody PagoMultipleRequest request,
                                           @RequestHeader("Authorization") String auth) {
        try {
            return ResponseEntity.ok(mercadoPagoService.procesarPagoMultiple(request, getSolicitanteId(auth)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}

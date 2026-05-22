package pe.torreblanca.backend.controller;

import pe.torreblanca.backend.service.ReportesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reportes")
@CrossOrigin(origins = "*")
public class ReportesController {

    @Autowired private ReportesService reportesService;

    @GetMapping("/mes/{anio}/{mes}")
    public ResponseEntity<?> reporteMes(@PathVariable Integer anio, @PathVariable Integer mes) {
        try { return ResponseEntity.ok(reportesService.reporteMes(mes, anio)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @GetMapping("/anio/{anio}")
    public ResponseEntity<?> reporteAnio(@PathVariable Integer anio) {
        try { return ResponseEntity.ok(reportesService.reporteAnio(anio)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }
}

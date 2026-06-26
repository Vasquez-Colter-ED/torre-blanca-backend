package pe.torreblanca.backend.service;

import pe.torreblanca.backend.dto.BoletaResponse;
import pe.torreblanca.backend.entity.*;
import pe.torreblanca.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BoletasService {

    @Autowired private BoletaRepository boletaRepository;
    @Autowired private PagoMantenimientoRepository pagoRepository;
    @Autowired private UsuarioRolRepository usuarioRolRepository;

    // Genera boleta automáticamente al aprobar un pago
    public Boleta generarBoleta(PagoMantenimiento pago, Usuario emitidaPor) {
        // Si ya tiene boleta no generar otra
        if (boletaRepository.findByPagoId(pago.getId()).isPresent()) {
            return boletaRepository.findByPagoId(pago.getId()).get();
        }

        String numero = generarNumeroBoleta(pago);

        Boleta boleta = new Boleta();
        boleta.setPago(pago);
        boleta.setNumeroBoleta(numero);
        boleta.setFechaEmision(LocalDateTime.now());
        boleta.setEmitidaPor(emitidaPor);

        return boletaRepository.save(boleta);
    }

    // Listar todas las boletas (directivos)
    public List<BoletaResponse> listarTodas() {
        return boletaRepository.findAllOrdenadas().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    // Boletas del residente logueado
    public List<BoletaResponse> misBoletas(Integer usuarioId) {
        return boletaRepository.findByPagadorId(usuarioId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    // Obtener boleta por ID
    public BoletaResponse obtenerPorId(Integer id) {
        Boleta boleta = boletaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Boleta no encontrada"));
        return toResponse(boleta);
    }

    private String generarNumeroBoleta(PagoMantenimiento pago) {
        String anio = String.valueOf(pago.getCuota().getConfiguracion().getAnio());
        String mes  = String.format("%02d", pago.getCuota().getConfiguracion().getMes());
        String depto = pago.getCuota().getDepartamento().getNumero();
        long count = boletaRepository.count() + 1;
        String seq = String.format("%04d", count);
        return "TB-" + anio + mes + "-" + depto + "-" + seq;
    }

    private boolean esDirectivo(Integer usuarioId) {
        return usuarioRolRepository.findRolesActivosByUsuarioId(usuarioId)
                .stream().anyMatch(ur -> ur.getRol().getEsDirectivo());
    }

    private BoletaResponse toResponse(Boleta b) {
        BoletaResponse r = new BoletaResponse();
        r.setId(b.getId());
        r.setNumeroBoleta(b.getNumeroBoleta());
        r.setFechaEmision(b.getFechaEmision());

        PagoMantenimiento pago = b.getPago();
        r.setPagadorNombre(pago.getPagador().getNombre() + " " + pago.getPagador().getApellido());
        r.setNumeroDepartamento(pago.getCuota().getDepartamento().getNumero());
        r.setPiso(pago.getCuota().getDepartamento().getPiso());
        r.setMonto(pago.getMonto());
        r.setMetodoPago(pago.getMetodoPago().name());
        r.setNumeroOperacion(pago.getNumeroOperacion());
        r.setFechaPago(pago.getFechaPago());
        r.setVoucherUrl(pago.getVoucherUrl());
        r.setMes(pago.getCuota().getConfiguracion().getMes());
        r.setAnio(pago.getCuota().getConfiguracion().getAnio());

        if (b.getEmitidaPor() != null)
            r.setEmitidaPorNombre(b.getEmitidaPor().getNombre() + " " + b.getEmitidaPor().getApellido());

        return r;
    }
}

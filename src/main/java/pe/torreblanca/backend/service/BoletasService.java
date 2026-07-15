package pe.torreblanca.backend.service;

import pe.torreblanca.backend.dto.BoletaResponse;
import pe.torreblanca.backend.entity.*;
import pe.torreblanca.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BoletasService {

    private static final String[] NOMBRES_MESES_BOLETA = {
        "Enero","Febrero","Marzo","Abril","Mayo","Junio","Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"
    };

    @Autowired private BoletaRepository boletaRepository;
    @Autowired private PagoMantenimientoRepository pagoRepository;
    @Autowired private UsuarioRolRepository usuarioRolRepository;
    @Autowired private PropietarioDepartamentoRepository propietarioDeptoRepository;
    @Autowired private InquilinoDepartamentoRepository inquilinoDeptoRepository;

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

    // Boletas visibles para un residente: todas las de SU departamento
    // (sin importar quién pagó cada una — padre, hijo, quien sea) desde
    // el mes en que se vinculó a ese depto en adelante. Así, si otra
    // persona del mismo departamento pagó una cuota, también la ve —
    // y sabe quién la pagó porque la boleta ya trae el nombre del pagador.
    public List<BoletaResponse> misBoletas(Integer usuarioId) {
        Map<Integer, LocalDate> deptosConFecha = new LinkedHashMap<>();
        propietarioDeptoRepository.findActivosByUsuarioId(usuarioId).forEach(pd ->
                deptosConFecha.put(pd.getDepartamento().getId(), pd.getFechaInicio()));
        inquilinoDeptoRepository.findActivosByUsuarioId(usuarioId).forEach(inq ->
                deptosConFecha.put(inq.getDepartamento().getId(), inq.getFechaInicio()));
        if (deptosConFecha.isEmpty()) return List.of();

        return boletaRepository.findAllOrdenadas().stream()
                .filter(b -> {
                    Integer deptoId = b.getPago().getCuota().getDepartamento().getId();
                    LocalDate desde = deptosConFecha.get(deptoId);
                    if (desde == null) return false; // no vinculado a ese depto
                    int mes  = b.getPago().getCuota().getConfiguracion().getMes();
                    int anio = b.getPago().getCuota().getConfiguracion().getAnio();
                    return anio > desde.getYear() || (anio == desde.getYear() && mes >= desde.getMonthValue());
                })
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
        r.setComision(pago.getComision());
        r.setMetodoPago(pago.getMetodoPago().name());
        r.setNumeroOperacion(pago.getNumeroOperacion());
        r.setFechaPago(pago.getFechaPago());
        r.setVoucherUrl(pago.getVoucherUrl());
        r.setMes(pago.getCuota().getConfiguracion().getMes());
        r.setAnio(pago.getCuota().getConfiguracion().getAnio());

        if (pago.getLoteId() != null) {
            List<String> otrosMeses = pagoRepository.findByLoteId(pago.getLoteId()).stream()
                    .filter(x -> !x.getId().equals(pago.getId()))
                    .map(x -> NOMBRES_MESES_BOLETA[x.getCuota().getConfiguracion().getMes() - 1] + " " + x.getCuota().getConfiguracion().getAnio())
                    .collect(Collectors.toList());
            r.setPagadoJuntoCon(otrosMeses);
        }

        if (b.getEmitidaPor() != null)
            r.setEmitidaPorNombre(b.getEmitidaPor().getNombre() + " " + b.getEmitidaPor().getApellido());

        return r;
    }
}

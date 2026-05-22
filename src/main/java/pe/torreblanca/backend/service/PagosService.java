package pe.torreblanca.backend.service;

import pe.torreblanca.backend.dto.*;
import pe.torreblanca.backend.entity.*;
import pe.torreblanca.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PagosService {

    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private ConfiguracionMantenimientoRepository configuracionRepository;
    @Autowired private CuotaMantenimientoRepository cuotaRepository;
    @Autowired private PagoMantenimientoRepository pagoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PropietarioDepartamentoRepository propietarioDeptoRepository;
    @Autowired private UsuarioRolRepository usuarioRolRepository;

    // ── Departamentos ─────────────────────────────────────────────────

    public List<Departamento> listarDepartamentos() {
        return departamentoRepository.findAllByOrderByNumeroAsc();
    }

    public MensajeResponse asignarUsuarioADepartamento(AsignarDepartamentoRequest request, Integer adminId) {
        verificarDirectivo(adminId);

        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Departamento departamento = departamentoRepository.findById(request.getDepartamentoId())
                .orElseThrow(() -> new RuntimeException("Departamento no encontrado"));

        // Desactivar asignación anterior del mismo departamento
        propietarioDeptoRepository.findActivoByDepartamentoId(departamento.getId())
                .ifPresent(pd -> { pd.setEstado(false); propietarioDeptoRepository.save(pd); });

        PropietarioDepartamento nueva = new PropietarioDepartamento();
        nueva.setUsuario(usuario);
        nueva.setDepartamento(departamento);
        nueva.setFechaInicio(LocalDate.now());
        nueva.setEstado(true);
        propietarioDeptoRepository.save(nueva);

        return new MensajeResponse("Usuario asignado al departamento " + departamento.getNumero(), true);
    }

    // ── Configuración mensual ─────────────────────────────────────────

    public MensajeResponse configurarMesYGenerarCuotas(ConfigurarMesRequest request, Integer adminId) {
        verificarDirectivo(adminId);

        if (configuracionRepository.existsByMesAndAnio(request.getMes(), request.getAnio()))
            throw new RuntimeException("Ya existe configuración para " + request.getMes() + "/" + request.getAnio());

        // Crear configuración
        ConfiguracionMantenimiento config = new ConfiguracionMantenimiento();
        config.setMes(request.getMes());
        config.setAnio(request.getAnio());
        config.setCostoPorM2(request.getCostoPorM2());
        config.setTotalGastosEstimados(request.getTotalGastosEstimados());
        config.setObservaciones(request.getObservaciones());
        config.setCreadoPor(adminId);
        ConfiguracionMantenimiento savedConfig = configuracionRepository.save(config);

        // Generar cuotas para los 32 departamentos automáticamente
        List<Departamento> departamentos = departamentoRepository.findAll();
        LocalDate vencimiento = LocalDate.of(request.getAnio(), request.getMes(), 28);

        for (Departamento depto : departamentos) {
            BigDecimal monto = depto.getMetrosCuadrados().multiply(request.getCostoPorM2());

            CuotaMantenimiento cuota = new CuotaMantenimiento();
            cuota.setDepartamento(depto);
            cuota.setConfiguracion(savedConfig);
            cuota.setMontoCalculado(monto);
            cuota.setEstado(EstadoCuota.PENDIENTE);
            cuota.setFechaVencimiento(vencimiento);

            // Asignar responsable de pago (propietario actual del depto)
            propietarioDeptoRepository.findActivoByDepartamentoId(depto.getId())
                    .ifPresent(pd -> cuota.setResponsablePago(pd.getUsuario()));

            cuotaRepository.save(cuota);
        }

        return new MensajeResponse("Configuración creada y " + departamentos.size() + " cuotas generadas para " + request.getMes() + "/" + request.getAnio(), true);
    }

    // ── Resumen del mes ───────────────────────────────────────────────

    public ResumenMesResponse obtenerResumenMes(Integer mes, Integer anio) {
        ConfiguracionMantenimiento config = configuracionRepository.findByMesAndAnio(mes, anio)
                .orElseThrow(() -> new RuntimeException("No hay configuración para " + mes + "/" + anio));

        List<CuotaMantenimiento> cuotas = cuotaRepository.findByConfiguracionId(config.getId());

        BigDecimal totalEsperado = cuotas.stream()
                .map(CuotaMantenimiento::getMontoCalculado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRecaudado = cuotas.stream()
                .filter(c -> c.getEstado() == EstadoCuota.PAGADO)
                .map(CuotaMantenimiento::getMontoCalculado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pagados   = cuotas.stream().filter(c -> c.getEstado() == EstadoCuota.PAGADO).count();
        long pendientes= cuotas.stream().filter(c -> c.getEstado() == EstadoCuota.PENDIENTE).count();
        long vencidos  = cuotas.stream().filter(c -> c.getEstado() == EstadoCuota.VENCIDO).count();

        ResumenMesResponse resumen = new ResumenMesResponse();
        resumen.setMes(mes);
        resumen.setAnio(anio);
        resumen.setCostoPorM2(config.getCostoPorM2());
        resumen.setTotalEsperado(totalEsperado);
        resumen.setTotalRecaudado(totalRecaudado);
        resumen.setTotalPendiente(totalEsperado.subtract(totalRecaudado));
        resumen.setTotalDepartamentos(cuotas.size());
        resumen.setPagados((int) pagados);
        resumen.setPendientes((int) pendientes);
        resumen.setVencidos((int) vencidos);
        resumen.setCuotas(cuotas.stream().map(this::toCuotaDetalle).collect(Collectors.toList()));

        return resumen;
    }

    // ── Cuotas del residente logueado ─────────────────────────────────

    public List<CuotaDetalleResponse> obtenerMisCuotas(Integer usuarioId) {
        return cuotaRepository.findByResponsablePagoId(usuarioId).stream()
                .map(this::toCuotaDetalle)
                .collect(Collectors.toList());
    }

    // ── Registrar pago ────────────────────────────────────────────────

    public MensajeResponse registrarPago(RegistrarPagoRequest request, Integer pagadorId, boolean esDirectivo) {
        CuotaMantenimiento cuota = cuotaRepository.findById(request.getCuotaId())
                .orElseThrow(() -> new RuntimeException("Cuota no encontrada"));

        if (cuota.getEstado() == EstadoCuota.PAGADO)
            throw new RuntimeException("Esta cuota ya está pagada");

        // Si no es directivo, solo puede pagar su propia cuota
        if (!esDirectivo && cuota.getResponsablePago() != null &&
                !cuota.getResponsablePago().getId().equals(pagadorId))
            throw new RuntimeException("No puedes registrar el pago de otro residente");

        Usuario pagador = usuarioRepository.findById(pagadorId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        PagoMantenimiento pago = new PagoMantenimiento();
        pago.setCuota(cuota);
        pago.setPagador(pagador);
        pago.setMonto(request.getMonto());
        pago.setFechaPago(LocalDateTime.now());
        pago.setMetodoPago(MetodoPago.valueOf(request.getMetodoPago()));
        pago.setNumeroOperacion(request.getNumeroOperacion());
        pago.setVoucherUrl(request.getVoucherUrl());
        pago.setObservaciones(request.getObservaciones());
        pago.setEstado(EstadoPago.PENDIENTE_VERIFICACION);
        pago.setRegistradoPor(esDirectivo ? "DIRECTIVO" : "RESIDENTE");

        pagoRepository.save(pago);
        return new MensajeResponse("Pago registrado. Pendiente de verificación por la directiva.", true);
    }

    // ── Pagos pendientes de verificación ─────────────────────────────

    public List<PagoDetalleResponse> obtenerPendientesVerificacion() {
        return pagoRepository.findPendientesVerificacion().stream()
                .map(this::toPagoDetalle)
                .collect(Collectors.toList());
    }

    // ── Verificar pago ────────────────────────────────────────────────

    public MensajeResponse verificarPago(Integer pagoId, VerificarPagoRequest request, Integer adminId) {
        verificarDirectivo(adminId);

        PagoMantenimiento pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado"));

        Usuario admin = usuarioRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin no encontrado"));

        if ("APROBAR".equals(request.getAccion())) {
            pago.setEstado(EstadoPago.VERIFICADO);
            pago.getCuota().setEstado(EstadoCuota.PAGADO);
            cuotaRepository.save(pago.getCuota());
        } else if ("RECHAZAR".equals(request.getAccion())) {
            pago.setEstado(EstadoPago.RECHAZADO);
        } else {
            throw new RuntimeException("Acción inválida. Use APROBAR o RECHAZAR");
        }

        pago.setVerificadoPor(admin);
        pago.setFechaVerificacion(LocalDateTime.now());
        if (request.getObservaciones() != null) pago.setObservaciones(request.getObservaciones());

        pagoRepository.save(pago);
        return new MensajeResponse("Pago " + ("APROBAR".equals(request.getAccion()) ? "aprobado" : "rechazado") + " correctamente", true);
    }

    // ── Listar configuraciones disponibles ────────────────────────────

    public List<ConfiguracionMantenimiento> listarConfiguraciones() {
        return configuracionRepository.findAll();
    }

    // ── Helpers privados ──────────────────────────────────────────────

    private boolean esDirectivo(Integer usuarioId) {
        return usuarioRolRepository.findRolesActivosByUsuarioId(usuarioId)
                .stream().anyMatch(ur -> ur.getRol().getEsDirectivo());
    }

    private void verificarDirectivo(Integer usuarioId) {
        if (!esDirectivo(usuarioId))
            throw new RuntimeException("No tienes permisos para esta acción");
    }

    private CuotaDetalleResponse toCuotaDetalle(CuotaMantenimiento c) {
        CuotaDetalleResponse r = new CuotaDetalleResponse();
        r.setCuotaId(c.getId());
        r.setNumeroDepartamento(c.getDepartamento().getNumero());
        r.setPiso(c.getDepartamento().getPiso());
        r.setMetrosCuadrados(c.getDepartamento().getMetrosCuadrados());
        r.setMontoCalculado(c.getMontoCalculado());
        r.setEstadoCuota(c.getEstado().name());
        if (c.getResponsablePago() != null) {
            r.setResponsableNombre(c.getResponsablePago().getNombre() + " " + c.getResponsablePago().getApellido());
            r.setResponsableEmail(c.getResponsablePago().getEmail());
        }
        r.setPagos(pagoRepository.findByCuotaId(c.getId()).stream()
                .map(this::toPagoDetalle).collect(Collectors.toList()));
        return r;
    }

    private PagoDetalleResponse toPagoDetalle(PagoMantenimiento p) {
        PagoDetalleResponse r = new PagoDetalleResponse();
        r.setPagoId(p.getId());
        r.setPagadorNombre(p.getPagador().getNombre() + " " + p.getPagador().getApellido());
        r.setMonto(p.getMonto());
        r.setMetodoPago(p.getMetodoPago().name());
        r.setNumeroOperacion(p.getNumeroOperacion());
        r.setVoucherUrl(p.getVoucherUrl());
        r.setEstado(p.getEstado().name());
        r.setObservaciones(p.getObservaciones());
        r.setFechaPago(p.getFechaPago());
        r.setFechaVerificacion(p.getFechaVerificacion());
        if (p.getVerificadoPor() != null)
            r.setVerificadoPorNombre(p.getVerificadoPor().getNombre() + " " + p.getVerificadoPor().getApellido());
        return r;
    }
}

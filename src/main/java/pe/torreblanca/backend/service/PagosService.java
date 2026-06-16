package pe.torreblanca.backend.service;

import pe.torreblanca.backend.dto.*;
import pe.torreblanca.backend.entity.*;
import pe.torreblanca.backend.repository.*;
import pe.torreblanca.backend.util.ValidacionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PagosService {

    private static final int MAX_INQUILINOS_POR_DEPTO = 5;

    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private ConfiguracionMantenimientoRepository configuracionRepository;
    @Autowired private CuotaMantenimientoRepository cuotaRepository;
    @Autowired private PagoMantenimientoRepository pagoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PropietarioDepartamentoRepository propietarioDeptoRepository;
    @Autowired private InquilinoDepartamentoRepository inquilinoDeptoRepository;
    @Autowired private UsuarioRolRepository usuarioRolRepository;
    @Autowired private BoletasService boletasService;

    // ── Departamentos ─────────────────────────────────────────────────

    public List<DepartamentoDetalleResponse> listarDepartamentos() {
        return departamentoRepository.findAllByOrderByNumeroAsc().stream()
                .map(this::toDepartamentoDetalle).collect(Collectors.toList());
    }

    public MensajeResponse asignarPropietario(AsignarDepartamentoRequest request, Integer adminId) {
        verificarDirectivo(adminId);
        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Departamento departamento = departamentoRepository.findById(request.getDepartamentoId())
                .orElseThrow(() -> new RuntimeException("Departamento no encontrado"));
        propietarioDeptoRepository.findActivoByDepartamentoId(departamento.getId())
                .ifPresent(pd -> { pd.setEstado(false); propietarioDeptoRepository.save(pd); });
        PropietarioDepartamento nueva = new PropietarioDepartamento();
        nueva.setUsuario(usuario); nueva.setDepartamento(departamento);
        nueva.setFechaInicio(LocalDate.now()); nueva.setEstado(true);
        propietarioDeptoRepository.save(nueva);
        return new MensajeResponse("Propietario asignado al departamento " + departamento.getNumero(), true);
    }

    public MensajeResponse asignarInquilino(AsignarInquilinoRequest request, Integer adminId) {
        verificarDirectivo(adminId);
        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Departamento departamento = departamentoRepository.findById(request.getDepartamentoId())
                .orElseThrow(() -> new RuntimeException("Departamento no encontrado"));
        Usuario propietario = usuarioRepository.findById(request.getPropietarioId())
                .orElseThrow(() -> new RuntimeException("Propietario no encontrado"));

        // Límite máximo de inquilinos por departamento
        long actuales = inquilinoDeptoRepository.findActivosByDepartamentoId(departamento.getId()).size();
        if (actuales >= MAX_INQUILINOS_POR_DEPTO)
            throw new RuntimeException("El departamento " + departamento.getNumero() +
                    " ya alcanzó el máximo de " + MAX_INQUILINOS_POR_DEPTO + " inquilinos permitidos");

        InquilinoDepartamento inq = new InquilinoDepartamento();
        inq.setUsuario(usuario); inq.setDepartamento(departamento);
        inq.setPropietario(propietario); inq.setFechaInicio(LocalDate.now()); inq.setEstado(true);
        inquilinoDeptoRepository.save(inq);
        return new MensajeResponse("Inquilino asignado al departamento " + departamento.getNumero(), true);
    }

    public MensajeResponse quitarInquilino(Integer inquilinoDeptoId, Integer adminId) {
        verificarDirectivo(adminId);
        InquilinoDepartamento inq = inquilinoDeptoRepository.findById(inquilinoDeptoId)
                .orElseThrow(() -> new RuntimeException("Registro no encontrado"));
        inq.setEstado(false); inq.setFechaFin(LocalDate.now());
        inquilinoDeptoRepository.save(inq);
        return new MensajeResponse("Inquilino removido del departamento", true);
    }

    // Lista de residentes de UN departamento específico — usado para el
    // selector de "quién realizó el pago" cuando un directivo registra
    // un pago manualmente (efectivo, etc.)
    public List<ResidenteOption> listarResidentesDeDepto(Integer departamentoId) {
        List<ResidenteOption> lista = new ArrayList<>();

        propietarioDeptoRepository.findActivoByDepartamentoId(departamentoId).ifPresent(pd -> {
            ResidenteOption r = new ResidenteOption();
            r.setId(pd.getUsuario().getId());
            r.setNombre(pd.getUsuario().getNombre() + " " + pd.getUsuario().getApellido());
            r.setTipo("PROPIETARIO");
            lista.add(r);
        });

        inquilinoDeptoRepository.findActivosByDepartamentoId(departamentoId).forEach(i -> {
            ResidenteOption r = new ResidenteOption();
            r.setId(i.getUsuario().getId());
            r.setNombre(i.getUsuario().getNombre() + " " + i.getUsuario().getApellido());
            r.setTipo("INQUILINO");
            lista.add(r);
        });

        return lista;
    }

    // ── Configuración mensual ─────────────────────────────────────────

    public MensajeResponse configurarMesYGenerarCuotas(ConfigurarMesRequest request, Integer adminId) {
        verificarDirectivo(adminId);
        if (configuracionRepository.existsByMesAndAnio(request.getMes(), request.getAnio()))
            throw new RuntimeException("Ya existe configuración para " + request.getMes() + "/" + request.getAnio());

        ValidacionUtil.validarTextoLibre(request.getObservaciones(), "Las observaciones");

        ConfiguracionMantenimiento config = new ConfiguracionMantenimiento();
        config.setMes(request.getMes()); config.setAnio(request.getAnio());
        config.setCostoPorM2(request.getCostoPorM2());
        config.setTotalGastosEstimados(request.getTotalGastosEstimados());
        config.setObservaciones(request.getObservaciones());
        config.setCreadoPor(adminId);
        ConfiguracionMantenimiento savedConfig = configuracionRepository.save(config);
        List<Departamento> departamentos = departamentoRepository.findAll();
        LocalDate vencimiento = LocalDate.of(request.getAnio(), request.getMes(), 28);
        for (Departamento depto : departamentos) {
            BigDecimal monto = depto.getMetrosCuadrados().multiply(request.getCostoPorM2());
            CuotaMantenimiento cuota = new CuotaMantenimiento();
            cuota.setDepartamento(depto); cuota.setConfiguracion(savedConfig);
            cuota.setMontoCalculado(monto); cuota.setEstado(EstadoCuota.PENDIENTE);
            cuota.setFechaVencimiento(vencimiento);
            cuotaRepository.save(cuota);
        }
        return new MensajeResponse("Configuración creada y " + departamentos.size() + " cuotas generadas", true);
    }

    public MensajeResponse editarConfiguracion(Integer configId, EditarConfiguracionRequest request, Integer adminId) {
        verificarDirectivo(adminId);

        ValidacionUtil.validarTextoLibre(request.getObservaciones(), "Las observaciones");

        ConfiguracionMantenimiento config = configuracionRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("Configuración no encontrada"));
        if (request.getCostoPorM2() != null) {
            config.setCostoPorM2(request.getCostoPorM2());
            List<CuotaMantenimiento> cuotas = cuotaRepository.findByConfiguracionId(configId);
            for (CuotaMantenimiento cuota : cuotas) {
                cuota.setMontoCalculado(cuota.getDepartamento().getMetrosCuadrados().multiply(request.getCostoPorM2()));
                cuotaRepository.save(cuota);
            }
        }
        if (request.getTotalGastosEstimados() != null) config.setTotalGastosEstimados(request.getTotalGastosEstimados());
        if (request.getObservaciones() != null) config.setObservaciones(request.getObservaciones());
        configuracionRepository.save(config);
        return new MensajeResponse("Configuración actualizada correctamente", true);
    }

    public MensajeResponse eliminarConfiguracion(Integer configId, Integer adminId) {
        verificarDirectivo(adminId);
        ConfiguracionMantenimiento config = configuracionRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("Configuración no encontrada"));
        List<CuotaMantenimiento> cuotas = cuotaRepository.findByConfiguracionId(configId);
        for (CuotaMantenimiento cuota : cuotas) {
            pagoRepository.findByCuotaId(cuota.getId()).forEach(pagoRepository::delete);
            cuotaRepository.delete(cuota);
        }
        configuracionRepository.delete(config);
        return new MensajeResponse("Configuración y cuotas eliminadas correctamente", true);
    }

    // ── Resumen del mes ───────────────────────────────────────────────

    public ResumenMesResponse obtenerResumenMes(Integer mes, Integer anio) {
        ConfiguracionMantenimiento config = configuracionRepository.findByMesAndAnio(mes, anio)
                .orElseThrow(() -> new RuntimeException("No hay configuración para " + mes + "/" + anio));
        List<CuotaMantenimiento> cuotas = cuotaRepository.findByConfiguracionId(config.getId());
        BigDecimal totalEsperado  = cuotas.stream().map(CuotaMantenimiento::getMontoCalculado).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRecaudado = cuotas.stream().filter(c -> c.getEstado() == EstadoCuota.PAGADO).map(CuotaMantenimiento::getMontoCalculado).reduce(BigDecimal.ZERO, BigDecimal::add);
        long pagados    = cuotas.stream().filter(c -> c.getEstado() == EstadoCuota.PAGADO).count();
        long pendientes = cuotas.stream().filter(c -> c.getEstado() == EstadoCuota.PENDIENTE).count();
        long vencidos   = cuotas.stream().filter(c -> c.getEstado() == EstadoCuota.VENCIDO).count();
        ResumenMesResponse resumen = new ResumenMesResponse();
        resumen.setMes(mes); resumen.setAnio(anio);
        resumen.setCostoPorM2(config.getCostoPorM2());
        resumen.setTotalEsperado(totalEsperado); resumen.setTotalRecaudado(totalRecaudado);
        resumen.setTotalPendiente(totalEsperado.subtract(totalRecaudado));
        resumen.setTotalDepartamentos(cuotas.size());
        resumen.setPagados((int) pagados); resumen.setPendientes((int) pendientes); resumen.setVencidos((int) vencidos);
        resumen.setCuotas(cuotas.stream().map(this::toCuotaDetalle).collect(Collectors.toList()));
        return resumen;
    }

    // ── Cuotas del usuario ────────────────────────────────────────────

    public List<CuotaDetalleResponse> obtenerMisCuotas(Integer usuarioId) {
        List<Integer> deptoIds = obtenerDeptosDeUsuario(usuarioId);
        List<CuotaMantenimiento> cuotas = new ArrayList<>();
        for (Integer deptoId : deptoIds) cuotas.addAll(cuotaRepository.findByDepartamentoId(deptoId));
        return cuotas.stream()
                .sorted((a, b) -> {
                    int c = b.getConfiguracion().getAnio().compareTo(a.getConfiguracion().getAnio());
                    return c != 0 ? c : b.getConfiguracion().getMes().compareTo(a.getConfiguracion().getMes());
                })
                .map(this::toCuotaDetalle).collect(Collectors.toList());
    }

    // ── Registrar pago ────────────────────────────────────────────────
    // Si el solicitante es directivo, DEBE especificar pagadorId (quién
    // realizó realmente el pago). Ya no hay fallback silencioso al
    // directivo logueado — eso era la causa del bug de la boleta.
    public MensajeResponse registrarPago(RegistrarPagoRequest request, Integer solicitanteId, boolean esDirectivo) {
        CuotaMantenimiento cuota = cuotaRepository.findById(request.getCuotaId())
                .orElseThrow(() -> new RuntimeException("Cuota no encontrada"));
        if (cuota.getEstado() == EstadoCuota.PAGADO)
            throw new RuntimeException("Esta cuota ya está pagada");

        ValidacionUtil.validarTextoLibre(request.getNumeroOperacion(), "El número de operación");
        ValidacionUtil.validarTextoLibre(request.getObservaciones(), "Las observaciones");

        Integer pagadorRealId;
        if (esDirectivo) {
            if (request.getPagadorId() == null)
                throw new RuntimeException("Debes seleccionar quién realizó el pago");
            pagadorRealId = request.getPagadorId();
        } else {
            pagadorRealId = solicitanteId;
            List<Integer> misDeptos = obtenerDeptosDeUsuario(solicitanteId);
            if (!misDeptos.contains(cuota.getDepartamento().getId()))
                throw new RuntimeException("No puedes registrar el pago de otro departamento");
        }

        Usuario pagador = usuarioRepository.findById(pagadorRealId)
                .orElseThrow(() -> new RuntimeException("Pagador no encontrado"));

        PagoMantenimiento pago = new PagoMantenimiento();
        pago.setCuota(cuota); pago.setPagador(pagador); pago.setMonto(request.getMonto());
        pago.setFechaPago(LocalDateTime.now());
        pago.setMetodoPago(MetodoPago.valueOf(request.getMetodoPago()));
        pago.setNumeroOperacion(request.getNumeroOperacion());
        pago.setVoucherUrl(request.getVoucherUrl());
        pago.setObservaciones(request.getObservaciones());
        pago.setEstado(EstadoPago.PENDIENTE_VERIFICACION);
        pago.setRegistradoPor(esDirectivo ? "DIRECTIVO" : "RESIDENTE");
        pagoRepository.save(pago);

        String nombre = pagador.getNombre() + " " + pagador.getApellido();
        return new MensajeResponse("Pago registrado a nombre de " + nombre + ". Pendiente de verificación.", true);
    }

    public MensajeResponse verificarPago(Integer pagoId, VerificarPagoRequest request, Integer adminId) {
        verificarDirectivo(adminId);
        PagoMantenimiento pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado"));
        Usuario admin = usuarioRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin no encontrado"));

        ValidacionUtil.validarTextoLibre(request.getObservaciones(), "Las observaciones");

        if ("APROBAR".equals(request.getAccion())) {
            pago.setEstado(EstadoPago.VERIFICADO);
            pago.getCuota().setEstado(EstadoCuota.PAGADO);
            cuotaRepository.save(pago.getCuota());
            pago.setVerificadoPor(admin); pago.setFechaVerificacion(LocalDateTime.now());
            if (request.getObservaciones() != null) pago.setObservaciones(request.getObservaciones());
            pagoRepository.save(pago);
            boletasService.generarBoleta(pago, admin);
            return new MensajeResponse("Pago aprobado y boleta generada automáticamente", true);
        } else if ("RECHAZAR".equals(request.getAccion())) {
            pago.setEstado(EstadoPago.RECHAZADO);
            pago.setVerificadoPor(admin); pago.setFechaVerificacion(LocalDateTime.now());
            if (request.getObservaciones() != null) pago.setObservaciones(request.getObservaciones());
            pagoRepository.save(pago);
            return new MensajeResponse("Pago rechazado correctamente", true);
        } else {
            throw new RuntimeException("Acción inválida. Use APROBAR o RECHAZAR");
        }
    }

    public List<PagoDetalleResponse> obtenerPendientesVerificacion() {
        return pagoRepository.findPendientesVerificacion().stream()
                .map(this::toPagoDetalle).collect(Collectors.toList());
    }

    public List<ConfiguracionMantenimiento> listarConfiguraciones() {
        return configuracionRepository.findAll();
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private List<Integer> obtenerDeptosDeUsuario(Integer usuarioId) {
        List<Integer> ids = new ArrayList<>();
        propietarioDeptoRepository.findActivosByUsuarioId(usuarioId).forEach(pd -> ids.add(pd.getDepartamento().getId()));
        inquilinoDeptoRepository.findActivosByUsuarioId(usuarioId).forEach(id -> ids.add(id.getDepartamento().getId()));
        return ids;
    }

    private List<String> obtenerResidentesDeDepto(Integer deptoId) {
        List<String> nombres = new ArrayList<>();
        propietarioDeptoRepository.findActivoByDepartamentoId(deptoId)
                .ifPresent(pd -> nombres.add(pd.getUsuario().getNombre() + " " + pd.getUsuario().getApellido() + " (Propietario)"));
        inquilinoDeptoRepository.findActivosByDepartamentoId(deptoId)
                .forEach(i -> nombres.add(i.getUsuario().getNombre() + " " + i.getUsuario().getApellido() + " (Inquilino)"));
        return nombres;
    }

    private boolean esDirectivo(Integer usuarioId) {
        return usuarioRolRepository.findRolesActivosByUsuarioId(usuarioId)
                .stream().anyMatch(ur -> ur.getRol().getEsDirectivo());
    }

    private void verificarDirectivo(Integer usuarioId) {
        if (!esDirectivo(usuarioId)) throw new RuntimeException("No tienes permisos para esta acción");
    }

    private DepartamentoDetalleResponse toDepartamentoDetalle(Departamento d) {
        DepartamentoDetalleResponse r = new DepartamentoDetalleResponse();
        r.setId(d.getId()); r.setNumero(d.getNumero());
        r.setPiso(d.getPiso()); r.setMetrosCuadrados(d.getMetrosCuadrados());
        r.setEstado(d.getEstado().name());
        propietarioDeptoRepository.findActivoByDepartamentoId(d.getId()).ifPresent(pd -> {
            r.setPropietarioId(pd.getUsuario().getId());
            r.setPropietarioNombre(pd.getUsuario().getNombre() + " " + pd.getUsuario().getApellido());
            r.setPropietarioEmail(pd.getUsuario().getEmail());
        });
        r.setInquilinos(inquilinoDeptoRepository.findActivosByDepartamentoId(d.getId()).stream()
                .map(i -> { InquilinoInfo ii = new InquilinoInfo(); ii.setAsignacionId(i.getId());
                    ii.setUsuarioId(i.getUsuario().getId());
                    ii.setNombre(i.getUsuario().getNombre() + " " + i.getUsuario().getApellido());
                    ii.setEmail(i.getUsuario().getEmail()); return ii; })
                .collect(Collectors.toList()));
        return r;
    }

    private CuotaDetalleResponse toCuotaDetalle(CuotaMantenimiento c) {
        CuotaDetalleResponse r = new CuotaDetalleResponse();
        r.setCuotaId(c.getId());
        r.setDepartamentoId(c.getDepartamento().getId());
        r.setNumeroDepartamento(c.getDepartamento().getNumero());
        r.setPiso(c.getDepartamento().getPiso());
        r.setMetrosCuadrados(c.getDepartamento().getMetrosCuadrados());
        r.setMontoCalculado(c.getMontoCalculado());
        r.setEstadoCuota(c.getEstado().name());
        r.setResidentesNombres(obtenerResidentesDeDepto(c.getDepartamento().getId()));
        r.setPagos(pagoRepository.findByCuotaId(c.getId()).stream()
                .map(this::toPagoDetalle).collect(Collectors.toList()));
        return r;
    }

    private PagoDetalleResponse toPagoDetalle(PagoMantenimiento p) {
        PagoDetalleResponse r = new PagoDetalleResponse();
        r.setPagoId(p.getId());
        r.setPagadorNombre(p.getPagador().getNombre() + " " + p.getPagador().getApellido());
        r.setMonto(p.getMonto()); r.setMetodoPago(p.getMetodoPago().name());
        r.setNumeroOperacion(p.getNumeroOperacion()); r.setVoucherUrl(p.getVoucherUrl());
        r.setEstado(p.getEstado().name()); r.setObservaciones(p.getObservaciones());
        r.setFechaPago(p.getFechaPago()); r.setFechaVerificacion(p.getFechaVerificacion());
        if (p.getVerificadoPor() != null)
            r.setVerificadoPorNombre(p.getVerificadoPor().getNombre() + " " + p.getVerificadoPor().getApellido());
        r.setNumeroDepartamento(p.getCuota().getDepartamento().getNumero());
        r.setPiso(p.getCuota().getDepartamento().getPiso());
        return r;
    }
}

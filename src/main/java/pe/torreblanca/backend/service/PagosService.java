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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PagosService {

    private static final int MAX_INQUILINOS_POR_DEPTO = 5;

    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private ConfiguracionMantenimientoRepository configuracionRepository;
    @Autowired private CocheraDepartamentoRepository cocheraDeptoRepository;
    @Autowired private CuotaMantenimientoRepository cuotaRepository;
    @Autowired private PagoMantenimientoRepository pagoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PropietarioDepartamentoRepository propietarioDeptoRepository;
    @Autowired private InquilinoDepartamentoRepository inquilinoDeptoRepository;
    @Autowired private UsuarioRolRepository usuarioRolRepository;
    @Autowired private BoletasService boletasService;
    @Autowired private AuditoriaService auditoriaService;

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
        Map<String, Object> datosProp = new LinkedHashMap<>();
        datosProp.put("departamento", departamento.getNumero());
        datosProp.put("usuario", usuario.getNombre() + " " + usuario.getApellido());
        auditoriaService.registrar(adminId, "Propietario asignado", "propietarios_departamentos", nueva.getId(), null, datosProp);
        return new MensajeResponse("Propietario asignado al departamento " + departamento.getNumero(), true);
    }

    public MensajeResponse asignarInquilino(AsignarInquilinoRequest request, Integer adminId) {
        verificarDirectivo(adminId);
        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Departamento departamento = departamentoRepository.findById(request.getDepartamentoId())
                .orElseThrow(() -> new RuntimeException("Departamento no encontrado"));

        // El propietario es opcional: puede que el depto todavía no tenga uno
        // asignado, y aun así se puede registrar un inquilino igual
        Usuario propietario = null;
        if (request.getPropietarioId() != null) {
            propietario = usuarioRepository.findById(request.getPropietarioId())
                    .orElseThrow(() -> new RuntimeException("Propietario no encontrado"));
        }

        // Límite máximo de inquilinos por departamento
        long actuales = inquilinoDeptoRepository.findActivosByDepartamentoId(departamento.getId()).size();
        if (actuales >= MAX_INQUILINOS_POR_DEPTO)
            throw new RuntimeException("El departamento " + departamento.getNumero() +
                    " ya alcanzó el máximo de " + MAX_INQUILINOS_POR_DEPTO + " inquilinos permitidos");

        InquilinoDepartamento inq = new InquilinoDepartamento();
        inq.setUsuario(usuario); inq.setDepartamento(departamento);
        inq.setPropietario(propietario); inq.setFechaInicio(LocalDate.now()); inq.setEstado(true);
        inquilinoDeptoRepository.save(inq);
        Map<String, Object> datosInq = new LinkedHashMap<>();
        datosInq.put("departamento", departamento.getNumero());
        datosInq.put("usuario", usuario.getNombre() + " " + usuario.getApellido());
        auditoriaService.registrar(adminId, "Inquilino asignado", "inquilinos_departamentos", inq.getId(), null, datosInq);
        return new MensajeResponse("Inquilino asignado al departamento " + departamento.getNumero(), true);
    }

    public MensajeResponse quitarPropietario(Integer propietarioDeptoId, Integer adminId) {
        verificarDirectivo(adminId);
        PropietarioDepartamento pd = propietarioDeptoRepository.findById(propietarioDeptoId)
                .orElseThrow(() -> new RuntimeException("Registro no encontrado"));
        pd.setEstado(false); pd.setFechaFin(LocalDate.now());
        propietarioDeptoRepository.save(pd);
        auditoriaService.registrar(adminId, "Propietario desvinculado", "propietarios_departamentos", propietarioDeptoId);
        return new MensajeResponse("Propietario desvinculado del departamento", true);
    }

    public MensajeResponse quitarInquilino(Integer inquilinoDeptoId, Integer adminId) {
        verificarDirectivo(adminId);
        InquilinoDepartamento inq = inquilinoDeptoRepository.findById(inquilinoDeptoId)
                .orElseThrow(() -> new RuntimeException("Registro no encontrado"));
        inq.setEstado(false); inq.setFechaFin(LocalDate.now());
        inquilinoDeptoRepository.save(inq);
        auditoriaService.registrar(adminId, "Inquilino removido", "inquilinos_departamentos", inquilinoDeptoId);
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

        String tipo = request.getTipoCalculo() != null ? request.getTipoCalculo() : "COSTO_M2";

        if ("PORCENTAJE".equals(tipo)) {
            if (request.getTotalMensual() == null || request.getTotalMensual().compareTo(BigDecimal.ZERO) <= 0)
                throw new RuntimeException("El monto total mensual debe ser mayor a cero");
        } else if ("MONTO_FIJO".equals(tipo)) {
            if (request.getMontoFijo() == null || request.getMontoFijo().compareTo(BigDecimal.ZERO) <= 0)
                throw new RuntimeException("El monto fijo por departamento debe ser mayor a cero");
        } else {
            if (request.getCostoPorM2() == null || request.getCostoPorM2().compareTo(BigDecimal.ZERO) <= 0)
                throw new RuntimeException("El costo por m² debe ser mayor a cero");
        }

        ValidacionUtil.validarTextoLibre(request.getObservaciones(), "Las observaciones");

        ConfiguracionMantenimiento config = new ConfiguracionMantenimiento();
        config.setMes(request.getMes()); config.setAnio(request.getAnio());
        // costo_por_m2 es NOT NULL en BD por diseño original — si se usa
        // otra fórmula no aplica, guardamos 0 para no romper el insert
        config.setCostoPorM2(request.getCostoPorM2() != null ? request.getCostoPorM2() : BigDecimal.ZERO);
        config.setTotalMensual(request.getTotalMensual());
        config.setMontoFijo(request.getMontoFijo());
        config.setTipoCalculo(tipo);
        config.setTotalGastosEstimados(request.getTotalGastosEstimados());
        config.setObservaciones(request.getObservaciones());
        config.setCreadoPor(adminId);
        ConfiguracionMantenimiento savedConfig = configuracionRepository.save(config);

        // Solo generar cuotas para departamentos (no estacionamientos)
        List<Departamento> departamentos = departamentoRepository.findAll().stream()
                .filter(d -> !"ESTACIONAMIENTO".equals(d.getTipo()))
                .collect(java.util.stream.Collectors.toList());

        LocalDate vencimiento = LocalDate.of(request.getAnio(), request.getMes(), 28);
        for (Departamento depto : departamentos) {
            BigDecimal monto;
            if ("PORCENTAJE".equals(tipo)) {
                // Suma el porcentaje del depto + sus cocheras asignadas
                BigDecimal pctDepto = depto.getPorcentaje() != null ? depto.getPorcentaje() : BigDecimal.ZERO;
                BigDecimal pctCocheras = cocheraDeptoRepository.findActivasByDepartamentoId(depto.getId())
                        .stream()
                        .map(cd -> cd.getCochera().getPorcentaje() != null ? cd.getCochera().getPorcentaje() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal pctTotal = pctDepto.add(pctCocheras);
                monto = pctTotal.divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP)
                        .multiply(request.getTotalMensual());
            } else if ("MONTO_FIJO".equals(tipo)) {
                // Todos los departamentos pagan exactamente el mismo monto
                monto = request.getMontoFijo();
            } else {
                // Método clásico: metros × costo_m2
                monto = depto.getMetrosCuadrados() != null
                        ? depto.getMetrosCuadrados().multiply(request.getCostoPorM2())
                        : BigDecimal.ZERO;
            }
            monto = monto.setScale(2, java.math.RoundingMode.HALF_UP);
            CuotaMantenimiento cuota = new CuotaMantenimiento();
            cuota.setDepartamento(depto); cuota.setConfiguracion(savedConfig);
            cuota.setMontoCalculado(monto); cuota.setEstado(EstadoCuota.PENDIENTE);
            cuota.setFechaVencimiento(vencimiento);
            cuotaRepository.save(cuota);
        }

        Map<String, Object> datosConfig = new LinkedHashMap<>();
        datosConfig.put("mes", request.getMes());
        datosConfig.put("anio", request.getAnio());
        datosConfig.put("tipoCalculo", tipo);
        datosConfig.put("deptosAfectados", departamentos.size());
        auditoriaService.registrar(adminId, "Configuración de mantenimiento creada", "configuracion_mantenimiento", savedConfig.getId(), null, datosConfig);

        return new MensajeResponse("Configuración creada y " + departamentos.size() + " cuotas generadas", true);
    }

    public MensajeResponse editarConfiguracion(Integer configId, EditarConfiguracionRequest request, Integer adminId) {
        verificarDirectivo(adminId);

        ValidacionUtil.validarTextoLibre(request.getObservaciones(), "Las observaciones");

        ConfiguracionMantenimiento config = configuracionRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("Configuración no encontrada"));

        // No se puede editar si algún departamento ya registró un pago (total
        // o parcial) con los montos actuales — cambiar la fórmula después
        // alteraría retroactivamente lo que esa persona ya pagó
        List<CuotaMantenimiento> cuotas = cuotaRepository.findByConfiguracionId(configId);
        long conPago = cuotas.stream()
                .filter(c -> c.getMontoPagado() != null && c.getMontoPagado().compareTo(BigDecimal.ZERO) > 0)
                .count();
        if (conPago > 0)
            throw new RuntimeException("No puedes editar esta configuración: " + conPago +
                    (conPago > 1 ? " departamentos ya registraron pagos" : " departamento ya registró un pago") +
                    " con estos montos. Cambiar la fórmula ahora alteraría lo que ya pagaron.");

        Map<String, Object> antesConfig = new LinkedHashMap<>();
        antesConfig.put("tipoCalculo", config.getTipoCalculo());
        antesConfig.put("costoPorM2", config.getCostoPorM2());
        antesConfig.put("totalMensual", config.getTotalMensual());
        antesConfig.put("montoFijo", config.getMontoFijo());

        // Si no se especifica tipoCalculo, se mantiene el que ya tenía la config
        String tipo = request.getTipoCalculo() != null ? request.getTipoCalculo() : config.getTipoCalculo();

        if ("PORCENTAJE".equals(tipo)) {
            if (request.getTotalMensual() == null || request.getTotalMensual().compareTo(BigDecimal.ZERO) <= 0)
                throw new RuntimeException("El monto total mensual debe ser mayor a cero");
        } else if ("MONTO_FIJO".equals(tipo)) {
            if (request.getMontoFijo() == null || request.getMontoFijo().compareTo(BigDecimal.ZERO) <= 0)
                throw new RuntimeException("El monto fijo por departamento debe ser mayor a cero");
        } else {
            if (request.getCostoPorM2() == null || request.getCostoPorM2().compareTo(BigDecimal.ZERO) <= 0)
                throw new RuntimeException("El costo por m² debe ser mayor a cero");
        }

        config.setTipoCalculo(tipo);
        if (request.getCostoPorM2()   != null) config.setCostoPorM2(request.getCostoPorM2());
        if (request.getTotalMensual() != null) config.setTotalMensual(request.getTotalMensual());
        if (request.getMontoFijo()    != null) config.setMontoFijo(request.getMontoFijo());

        if (request.getTotalGastosEstimados() != null) {
            if (request.getTotalGastosEstimados().compareTo(BigDecimal.ZERO) < 0)
                throw new RuntimeException("El total de gastos estimados no puede ser negativo");
            config.setTotalGastosEstimados(request.getTotalGastosEstimados());
        }
        if (request.getObservaciones() != null) config.setObservaciones(request.getObservaciones());
        configuracionRepository.save(config);

        // Recalcula TODAS las cuotas de ese mes según la fórmula vigente
        // (sea la misma de antes o una nueva, si el directivo la cambió).
        // Ya se validó arriba que ninguna tiene pagos, así que es seguro.
        for (CuotaMantenimiento cuota : cuotas) {
            Departamento depto = cuota.getDepartamento();
            BigDecimal monto;
            if ("PORCENTAJE".equals(tipo)) {
                BigDecimal pctDepto = depto.getPorcentaje() != null ? depto.getPorcentaje() : BigDecimal.ZERO;
                BigDecimal pctCocheras = cocheraDeptoRepository.findActivasByDepartamentoId(depto.getId())
                        .stream()
                        .map(cd -> cd.getCochera().getPorcentaje() != null ? cd.getCochera().getPorcentaje() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal pctTotal = pctDepto.add(pctCocheras);
                monto = pctTotal.divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP)
                        .multiply(config.getTotalMensual());
            } else if ("MONTO_FIJO".equals(tipo)) {
                monto = config.getMontoFijo();
            } else {
                monto = depto.getMetrosCuadrados() != null
                        ? depto.getMetrosCuadrados().multiply(config.getCostoPorM2())
                        : BigDecimal.ZERO;
            }
            cuota.setMontoCalculado(monto.setScale(2, java.math.RoundingMode.HALF_UP));
            cuotaRepository.save(cuota);
        }

        Map<String, Object> despuesConfig = new LinkedHashMap<>();
        despuesConfig.put("tipoCalculo", config.getTipoCalculo());
        despuesConfig.put("costoPorM2", config.getCostoPorM2());
        despuesConfig.put("totalMensual", config.getTotalMensual());
        despuesConfig.put("montoFijo", config.getMontoFijo());
        auditoriaService.registrar(adminId, "Configuración de mantenimiento editada", "configuracion_mantenimiento", configId, antesConfig, despuesConfig);

        return new MensajeResponse("Configuración actualizada y " + cuotas.size() + " cuotas recalculadas", true);
    }

    public MensajeResponse eliminarConfiguracion(Integer configId, Integer adminId) {
        verificarDirectivo(adminId);
        ConfiguracionMantenimiento config = configuracionRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("Configuración no encontrada"));
        Map<String, Object> antesElim = new LinkedHashMap<>();
        antesElim.put("mes", config.getMes());
        antesElim.put("anio", config.getAnio());
        antesElim.put("tipoCalculo", config.getTipoCalculo());
        List<CuotaMantenimiento> cuotas = cuotaRepository.findByConfiguracionId(configId);
        for (CuotaMantenimiento cuota : cuotas) {
            pagoRepository.findByCuotaId(cuota.getId()).forEach(pagoRepository::delete);
            cuotaRepository.delete(cuota);
        }
        configuracionRepository.delete(config);
        auditoriaService.registrar(adminId, "Configuración de mantenimiento eliminada", "configuracion_mantenimiento", configId, antesElim, null);
        return new MensajeResponse("Configuración y cuotas eliminadas correctamente", true);
    }

    // ── Resumen del mes ───────────────────────────────────────────────

    public ResumenMesResponse obtenerResumenMes(Integer mes, Integer anio) {
        ConfiguracionMantenimiento config = configuracionRepository.findByMesAndAnio(mes, anio)
                .orElseThrow(() -> new RuntimeException("No hay configuración para " + mes + "/" + anio));
        List<CuotaMantenimiento> cuotas = cuotaRepository.findByConfiguracionId(config.getId());
        BigDecimal totalEsperado  = cuotas.stream().map(CuotaMantenimiento::getMontoCalculado).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRecaudado = cuotas.stream()
                .map(c -> c.getMontoPagado() != null ? c.getMontoPagado() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long pagados    = cuotas.stream().filter(c -> c.getEstado() == EstadoCuota.PAGADO).count();
        long parciales  = cuotas.stream().filter(c -> c.getEstado() == EstadoCuota.PARCIAL).count();
        long pendientes = cuotas.stream().filter(c -> c.getEstado() == EstadoCuota.PENDIENTE).count();
        long vencidos   = cuotas.stream().filter(c -> c.getEstado() == EstadoCuota.VENCIDO).count();
        long enVerificacion = cuotas.stream()
                .filter(c -> pagoRepository.findByCuotaId(c.getId()).stream()
                        .anyMatch(p -> p.getEstado() == EstadoPago.PENDIENTE_VERIFICACION))
                .count();
        ResumenMesResponse resumen = new ResumenMesResponse();
        resumen.setMes(mes); resumen.setAnio(anio);
        resumen.setCostoPorM2(config.getCostoPorM2());
        resumen.setTotalEsperado(totalEsperado); resumen.setTotalRecaudado(totalRecaudado);
        resumen.setTotalPendiente(totalEsperado.subtract(totalRecaudado));
        resumen.setTotalDepartamentos(cuotas.size());
        resumen.setPagados((int) pagados); resumen.setParciales((int) parciales); resumen.setPendientes((int) pendientes); resumen.setVencidos((int) vencidos);
        resumen.setEnVerificacion((int) enVerificacion);
        resumen.setCuotas(cuotas.stream().map(this::toCuotaDetalle).collect(Collectors.toList()));
        return resumen;
    }

    // ── Cuotas del usuario ────────────────────────────────────────────
    // Solo muestra las cuotas desde el mes en que la persona se vinculó
    // al departamento (como propietario o inquilino) en adelante — así
    // un inquilino/propietario nuevo no hereda deudas ni crédito de
    // períodos anteriores a su llegada.
    public List<CuotaDetalleResponse> obtenerMisCuotas(Integer usuarioId) {
        Map<Integer, LocalDate> deptosConFecha = obtenerDeptosConFechaDeUsuario(usuarioId);
        List<CuotaMantenimiento> cuotas = new ArrayList<>();
        for (Map.Entry<Integer, LocalDate> entry : deptosConFecha.entrySet()) {
            LocalDate desde = entry.getValue();
            cuotaRepository.findByDepartamentoId(entry.getKey()).stream()
                    .filter(c -> {
                        if (desde == null) return true; // por si alguna asignación vieja no tiene fecha
                        int cAnio = c.getConfiguracion().getAnio();
                        int cMes  = c.getConfiguracion().getMes();
                        // Incluye el mes exacto en que se vinculó, excluye los anteriores
                        return cAnio > desde.getYear() || (cAnio == desde.getYear() && cMes >= desde.getMonthValue());
                    })
                    .forEach(cuotas::add);
        }
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
            throw new RuntimeException("Esta cuota ya está pagada en su totalidad");
        if (request.getMonto() == null || request.getMonto().compareTo(BigDecimal.ZERO) <= 0)
            throw new RuntimeException("El monto debe ser mayor a cero");

        BigDecimal yaPagado = cuota.getMontoPagado() != null ? cuota.getMontoPagado() : BigDecimal.ZERO;
        BigDecimal saldoPendiente = cuota.getMontoCalculado().subtract(yaPagado);
        if (request.getMonto().compareTo(saldoPendiente) > 0)
            throw new RuntimeException("El monto ingresado (S/ " + request.getMonto() +
                    ") supera el saldo pendiente de esta cuota (S/ " + saldoPendiente.setScale(2, java.math.RoundingMode.HALF_UP) + ")");

        // No permitir un segundo pago mientras el anterior sigue sin verificar —
        // evita sobre-pagos acumulados antes de que el directivo revise nada
        boolean tienePagoPendiente = pagoRepository.findByCuotaId(cuota.getId()).stream()
                .anyMatch(p -> p.getEstado() == EstadoPago.PENDIENTE_VERIFICACION);
        if (tienePagoPendiente) {
            String msgBloqueo = esDirectivo
                ? "Este departamento tiene un pago pendiente de verificación. Apruébalo o recházalo primero para poder registrar uno nuevo."
                : "Ya enviaste un pago para esta cuota que está pendiente de verificación. Espera a que el directivo lo apruebe o rechace antes de enviar otro.";
            throw new RuntimeException(msgBloqueo);
        }

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

        // Snapshot de auditoría: si fue un directivo quién registró el pago
        // manualmente, guardamos quién fue exactamente, con qué nombre y
        // qué cargo tenía en ESE momento (los directivos cambian cada periodo)
        if (esDirectivo) {
            Usuario registrante = usuarioRepository.findById(solicitanteId).orElse(null);
            if (registrante != null) {
                pago.setRegistradoPorUsuario(registrante);
                pago.setRegistradoPorNombre(registrante.getNombre() + " " + registrante.getApellido());
                pago.setRegistradoPorCargo(obtenerCargoActual(solicitanteId));
            }
        }
        pagoRepository.save(pago);

        String nombre = pagador.getNombre() + " " + pagador.getApellido();
        boolean esParcial = request.getMonto().compareTo(saldoPendiente) < 0;
        String msg = esParcial
            ? "Pago parcial de S/ " + request.getMonto() + " registrado a nombre de " + nombre +
              ". Quedará un saldo pendiente de S/ " + saldoPendiente.subtract(request.getMonto()).setScale(2, java.math.RoundingMode.HALF_UP) +
              " una vez verificado. Pendiente de verificación."
            : "Pago registrado a nombre de " + nombre + ". Pendiente de verificación.";
        return new MensajeResponse(msg, true);
    }

    // ── Registrar pago MÚLTIPLE (Transferencia/Efectivo) ──────────────
    // A diferencia del pago individual, NO admite parcial: se paga el saldo
    // exacto de cada cuota seleccionada, con un solo comprobante y un solo
    // método para todas. Se agrupan con un loteId para que el directivo las
    // apruebe/rechace juntas más adelante.
    public MensajeResponse registrarPagoMultiple(RegistrarPagoMultipleRequest request, Integer solicitanteId, boolean esDirectivo) {
        if (request.getCuotaIds() == null || request.getCuotaIds().isEmpty())
            throw new RuntimeException("Debes seleccionar al menos una cuota");
        if (!List.of("TRANSFERENCIA", "EFECTIVO").contains(request.getMetodoPago()))
            throw new RuntimeException("Método de pago inválido para pago múltiple");

        List<CuotaMantenimiento> cuotas = cuotaRepository.findAllById(request.getCuotaIds());
        if (cuotas.size() != request.getCuotaIds().size())
            throw new RuntimeException("Una o más cuotas no fueron encontradas");

        for (CuotaMantenimiento c : cuotas) {
            if (c.getEstado() == EstadoCuota.PAGADO)
                throw new RuntimeException("La cuota de " + c.getConfiguracion().getMes() + "/" + c.getConfiguracion().getAnio() + " ya está pagada");
            boolean tienePendiente = pagoRepository.findByCuotaId(c.getId()).stream()
                    .anyMatch(p -> p.getEstado() == EstadoPago.PENDIENTE_VERIFICACION);
            if (tienePendiente)
                throw new RuntimeException("La cuota de " + c.getConfiguracion().getMes() + "/" + c.getConfiguracion().getAnio() +
                        " ya tiene un pago pendiente de verificación. Resuélvelo antes de incluirla en un pago múltiple.");
        }

        ValidacionUtil.validarTextoLibre(request.getNumeroOperacion(), "El número de operación");
        ValidacionUtil.validarTextoLibre(request.getObservaciones(), "Las observaciones");

        Integer pagadorId;
        if (esDirectivo) {
            if (request.getPagadorId() == null)
                throw new RuntimeException("Debes seleccionar quién realizó el pago");
            pagadorId = request.getPagadorId();
        } else {
            pagadorId = solicitanteId;
            List<Integer> misDeptos = obtenerDeptosDeUsuario(solicitanteId);
            for (CuotaMantenimiento c : cuotas) {
                if (!misDeptos.contains(c.getDepartamento().getId()))
                    throw new RuntimeException("No puedes registrar el pago de otro departamento");
            }
        }
        Usuario pagador = usuarioRepository.findById(pagadorId)
                .orElseThrow(() -> new RuntimeException("Pagador no encontrado"));

        String loteId = UUID.randomUUID().toString();
        BigDecimal total = BigDecimal.ZERO;

        for (CuotaMantenimiento cuota : cuotas) {
            BigDecimal yaPagado = cuota.getMontoPagado() != null ? cuota.getMontoPagado() : BigDecimal.ZERO;
            BigDecimal saldoPendiente = cuota.getMontoCalculado().subtract(yaPagado);
            total = total.add(saldoPendiente);

            PagoMantenimiento pago = new PagoMantenimiento();
            pago.setCuota(cuota); pago.setPagador(pagador); pago.setMonto(saldoPendiente);
            pago.setFechaPago(LocalDateTime.now());
            pago.setMetodoPago(MetodoPago.valueOf(request.getMetodoPago()));
            pago.setNumeroOperacion(request.getNumeroOperacion());
            pago.setLoteId(loteId);
            pago.setVoucherUrl(request.getVoucherUrl());
            pago.setObservaciones(request.getObservaciones());
            pago.setEstado(EstadoPago.PENDIENTE_VERIFICACION);
            pago.setRegistradoPor(esDirectivo ? "DIRECTIVO" : "RESIDENTE");
            if (esDirectivo) {
                Usuario registrante = usuarioRepository.findById(solicitanteId).orElse(null);
                if (registrante != null) {
                    pago.setRegistradoPorUsuario(registrante);
                    pago.setRegistradoPorNombre(registrante.getNombre() + " " + registrante.getApellido());
                    pago.setRegistradoPorCargo(obtenerCargoActual(solicitanteId));
                }
            }
            pagoRepository.save(pago);
        }

        String nombreP = pagador.getNombre() + " " + pagador.getApellido();
        return new MensajeResponse("Pago de S/ " + total.setScale(2, java.math.RoundingMode.HALF_UP) +
                " registrado a nombre de " + nombreP + " (" + cuotas.size() + " cuota" + (cuotas.size() > 1 ? "s" : "") +
                "). Pendiente de verificación.", true);
    }

    public MensajeResponse verificarPago(Integer pagoId, VerificarPagoRequest request, Integer adminId) {
        verificarDirectivo(adminId);
        PagoMantenimiento pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado"));
        Usuario admin = usuarioRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin no encontrado"));

        verificarNoAutoVerificacion(pago, adminId);

        if ("RECHAZAR".equals(request.getAccion()) && (request.getObservaciones() == null || request.getObservaciones().trim().isEmpty()))
            throw new RuntimeException("Debes indicar el motivo del rechazo");
        ValidacionUtil.validarTextoLibre(request.getObservaciones(), "Las observaciones");

        String resultado = aplicarVerificacion(pago, request.getAccion(), request.getObservaciones(), admin);
        return new MensajeResponse(resultado, true);
    }

    // ── Verificar un LOTE completo (pago múltiple) de una sola vez ───
    public MensajeResponse verificarLote(String loteId, VerificarPagoRequest request, Integer adminId) {
        verificarDirectivo(adminId);
        Usuario admin = usuarioRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin no encontrado"));

        if ("RECHAZAR".equals(request.getAccion()) && (request.getObservaciones() == null || request.getObservaciones().trim().isEmpty()))
            throw new RuntimeException("Debes indicar el motivo del rechazo");
        ValidacionUtil.validarTextoLibre(request.getObservaciones(), "Las observaciones");

        List<PagoMantenimiento> pagosDelLote = pagoRepository.findByLoteId(loteId).stream()
                .filter(p -> p.getEstado() == EstadoPago.PENDIENTE_VERIFICACION)
                .collect(Collectors.toList());
        if (pagosDelLote.isEmpty())
            throw new RuntimeException("Este lote no tiene pagos pendientes de verificar (puede que ya haya sido procesado)");

        for (PagoMantenimiento pago : pagosDelLote) {
            verificarNoAutoVerificacion(pago, adminId);
        }

        for (PagoMantenimiento pago : pagosDelLote) {
            aplicarVerificacion(pago, request.getAccion(), request.getObservaciones(), admin);
        }

        String accionTxt = "APROBAR".equals(request.getAccion()) ? "aprobado" : "rechazado";
        return new MensajeResponse("Lote de " + pagosDelLote.size() + " cuota" + (pagosDelLote.size() > 1 ? "s" : "") +
                " " + accionTxt + " correctamente", true);
    }

    // Un directivo NO puede aprobar/rechazar un pago que él mismo hizo (como
    // residente) ni uno que él mismo registró manualmente a nombre de otro
    // — principio de doble verificación: quién registra el movimiento no
    // puede ser también quien lo aprueba.
    private void verificarNoAutoVerificacion(PagoMantenimiento pago, Integer adminId) {
        boolean esElMismoPagador = pago.getPagador() != null && pago.getPagador().getId().equals(adminId);
        boolean esQuienLoRegistro = pago.getRegistradoPorUsuario() != null && pago.getRegistradoPorUsuario().getId().equals(adminId);
        if (esElMismoPagador || esQuienLoRegistro) {
            throw new RuntimeException("No puedes aprobar ni rechazar un pago que tú mismo pagaste o registraste. Debe hacerlo otro directivo.");
        }
    }

    // Aplica APROBAR/RECHAZAR a un pago individual — usado tanto por
    // verificarPago (uno solo) como por verificarLote (varios de una vez)
    private String aplicarVerificacion(PagoMantenimiento pago, String accion, String observaciones, Usuario admin) {
        if ("APROBAR".equals(accion)) {
            pago.setEstado(EstadoPago.VERIFICADO);

            CuotaMantenimiento cuota = pago.getCuota();
            BigDecimal yaPagado = cuota.getMontoPagado() != null ? cuota.getMontoPagado() : BigDecimal.ZERO;
            BigDecimal nuevoAcumulado = yaPagado.add(pago.getMonto());
            cuota.setMontoPagado(nuevoAcumulado);

            if (nuevoAcumulado.compareTo(cuota.getMontoCalculado()) >= 0) {
                cuota.setEstado(EstadoCuota.PAGADO);
            } else {
                cuota.setEstado(EstadoCuota.PARCIAL);
            }
            cuotaRepository.save(cuota);

            pago.setVerificadoPor(admin); pago.setFechaVerificacion(LocalDateTime.now());
            pago.setVerificadoPorCargo(obtenerCargoActual(admin.getId()));
            if (observaciones != null) pago.setObservaciones(observaciones);
            pagoRepository.save(pago);
            boletasService.generarBoleta(pago, admin);

            return cuota.getEstado() == EstadoCuota.PAGADO
                ? "Pago aprobado y boleta generada automáticamente. Cuota completada."
                : "Pago parcial aprobado y boleta generada. Saldo pendiente: S/ " +
                  cuota.getMontoCalculado().subtract(nuevoAcumulado).setScale(2, java.math.RoundingMode.HALF_UP);
        } else if ("RECHAZAR".equals(accion)) {
            pago.setEstado(EstadoPago.RECHAZADO);
            pago.setVerificadoPor(admin); pago.setFechaVerificacion(LocalDateTime.now());
            pago.setVerificadoPorCargo(obtenerCargoActual(admin.getId()));
            if (observaciones != null) pago.setObservaciones(observaciones);
            pagoRepository.save(pago);
            return "Pago rechazado correctamente";
        } else {
            throw new RuntimeException("Acción inválida. Use APROBAR o RECHAZAR");
        }
    }

    public List<PagoDetalleResponse> obtenerPendientesVerificacion() {
        return pagoRepository.findPendientesVerificacion().stream()
                .map(this::toPagoDetalle).collect(Collectors.toList());
    }

    public List<ConfiguracionResponse> listarConfiguraciones() {
        return configuracionRepository.findAll().stream()
                .sorted((a, b) -> {
                    // OJO: comparar Integers con != compara el OBJETO, no el valor —
                    // hay que restar (o usar .equals) para comparar el número real
                    int cmpAnio = a.getAnio() - b.getAnio();
                    return cmpAnio != 0 ? cmpAnio : a.getMes() - b.getMes();
                })
                .map(c -> {
                    ConfiguracionResponse r = new ConfiguracionResponse();
                    r.setId(c.getId()); r.setMes(c.getMes()); r.setAnio(c.getAnio());
                    r.setTipoCalculo(c.getTipoCalculo());
                    r.setCostoPorM2(c.getCostoPorM2()); r.setTotalMensual(c.getTotalMensual()); r.setMontoFijo(c.getMontoFijo());
                    r.setObservaciones(c.getObservaciones());
                    List<CuotaMantenimiento> cuotas = cuotaRepository.findByConfiguracionId(c.getId());
                    long conPago = cuotas.stream()
                            .filter(cu -> cu.getMontoPagado() != null && cu.getMontoPagado().compareTo(BigDecimal.ZERO) > 0)
                            .count();
                    r.setTienePagos(conPago > 0);
                    r.setDeptosConPago((int) conPago);
                    return r;
                }).collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private List<Integer> obtenerDeptosDeUsuario(Integer usuarioId) {
        List<Integer> ids = new ArrayList<>();
        propietarioDeptoRepository.findActivosByUsuarioId(usuarioId).forEach(pd -> ids.add(pd.getDepartamento().getId()));
        inquilinoDeptoRepository.findActivosByUsuarioId(usuarioId).forEach(id -> ids.add(id.getDepartamento().getId()));
        return ids;
    }

    // Igual que arriba, pero también devuelve desde cuándo está vinculado a
    // cada depto — se usa para no mostrarle cuotas de antes de su llegada
    private Map<Integer, LocalDate> obtenerDeptosConFechaDeUsuario(Integer usuarioId) {
        Map<Integer, LocalDate> map = new LinkedHashMap<>();
        propietarioDeptoRepository.findActivosByUsuarioId(usuarioId).forEach(pd ->
                map.put(pd.getDepartamento().getId(), pd.getFechaInicio()));
        inquilinoDeptoRepository.findActivosByUsuarioId(usuarioId).forEach(inq ->
                map.put(inq.getDepartamento().getId(), inq.getFechaInicio()));
        return map;
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

    // Devuelve el cargo directivo actual del usuario (PRESIDENTE/SECRETARIO/TESORERO)
    // o null si no tiene ninguno. Se usa para tomar una "foto" del cargo en el
    // momento exacto de la acción, ya que los directivos cambian cada cierto tiempo.
    private String obtenerCargoActual(Integer usuarioId) {
        return usuarioRolRepository.findRolesActivosByUsuarioId(usuarioId).stream()
                .filter(ur -> ur.getRol().getEsDirectivo())
                .map(ur -> ur.getRol().getNombre())
                .findFirst().orElse(null);
    }

    private void verificarDirectivo(Integer usuarioId) {
        if (!esDirectivo(usuarioId)) throw new RuntimeException("No tienes permisos para esta acción");
    }

    private DepartamentoDetalleResponse toDepartamentoDetalle(Departamento d) {
        DepartamentoDetalleResponse r = new DepartamentoDetalleResponse();
        r.setId(d.getId()); r.setNumero(d.getNumero());
        r.setPiso(d.getPiso()); r.setMetrosCuadrados(d.getMetrosCuadrados());
        r.setPorcentaje(d.getPorcentaje());
        r.setTipo(d.getTipo() != null ? d.getTipo() : "DEPARTAMENTO");
        r.setDescripcion(d.getDescripcion());
        r.setEstado(d.getEstado().name());
        propietarioDeptoRepository.findActivoByDepartamentoId(d.getId()).ifPresent(pd -> {
            r.setPropietarioId(pd.getUsuario().getId());
            r.setPropietarioAsignacionId(pd.getId());
            r.setPropietarioNombre(pd.getUsuario().getNombre() + " " + pd.getUsuario().getApellido());
            r.setPropietarioEmail(pd.getUsuario().getEmail());
        });
        r.setInquilinos(inquilinoDeptoRepository.findActivosByDepartamentoId(d.getId()).stream()
                .map(i -> { InquilinoInfo ii = new InquilinoInfo(); ii.setAsignacionId(i.getId());
                    ii.setUsuarioId(i.getUsuario().getId());
                    ii.setNombre(i.getUsuario().getNombre() + " " + i.getUsuario().getApellido());
                    ii.setEmail(i.getUsuario().getEmail()); return ii; })
                .collect(Collectors.toList()));
        r.setCocheras(cocheraDeptoRepository.findActivasByDepartamentoId(d.getId()).stream()
                .map(cd -> { CocheraInfo ci = new CocheraInfo();
                    ci.setAsignacionId(cd.getId());
                    ci.setCocheraId(cd.getCochera().getId());
                    ci.setNumero(cd.getCochera().getNumero());
                    ci.setPorcentaje(cd.getCochera().getPorcentaje());
                    ci.setMetros(cd.getCochera().getMetrosCuadrados()); return ci; })
                .collect(Collectors.toList()));
        return r;
    }

    // ── Asignar cochera a departamento ────────────────────────────────
    public MensajeResponse asignarCochera(Integer cocheraId, Integer deptoId, Integer adminId) {
        verificarDirectivo(adminId);
        Departamento cochera = departamentoRepository.findById(cocheraId)
                .orElseThrow(() -> new RuntimeException("Cochera no encontrada"));
        if (!"ESTACIONAMIENTO".equals(cochera.getTipo()))
            throw new RuntimeException("El elemento seleccionado no es un estacionamiento");
        Departamento depto = departamentoRepository.findById(deptoId)
                .orElseThrow(() -> new RuntimeException("Departamento no encontrado"));
        if (!cocheraDeptoRepository.findActivasByCocheraId(cocheraId).isEmpty())
            throw new RuntimeException("Esta cochera ya está asignada a otro departamento");
        CocheraDepartamento cd = new CocheraDepartamento();
        cd.setCochera(cochera); cd.setDepartamento(depto);
        cd.setFechaInicio(LocalDate.now()); cd.setEstado(true);
        cocheraDeptoRepository.save(cd);
        auditoriaService.registrar(adminId, "Cochera asignada", "cocheras_departamentos", cd.getId());
        return new MensajeResponse("Cochera " + cochera.getNumero() + " asignada al Depto " + depto.getNumero(), true);
    }

    // ── Quitar cochera de departamento ────────────────────────────────
    public MensajeResponse quitarCochera(Integer asignacionId, Integer adminId) {
        verificarDirectivo(adminId);
        CocheraDepartamento cd = cocheraDeptoRepository.findById(asignacionId)
                .orElseThrow(() -> new RuntimeException("Asignación no encontrada"));
        cd.setEstado(false); cd.setFechaFin(LocalDate.now());
        cocheraDeptoRepository.save(cd);
        auditoriaService.registrar(adminId, "Cochera desvinculada", "cocheras_departamentos", asignacionId);
        return new MensajeResponse("Cochera desvinculada del departamento", true);
    }

    private CuotaDetalleResponse toCuotaDetalle(CuotaMantenimiento c) {
        CuotaDetalleResponse r = new CuotaDetalleResponse();
        r.setCuotaId(c.getId());
        r.setDepartamentoId(c.getDepartamento().getId());
        r.setNumeroDepartamento(c.getDepartamento().getNumero());
        r.setPiso(c.getDepartamento().getPiso());
        r.setMetrosCuadrados(c.getDepartamento().getMetrosCuadrados());
        r.setMontoCalculado(c.getMontoCalculado());
        BigDecimal pagado = c.getMontoPagado() != null ? c.getMontoPagado() : BigDecimal.ZERO;
        r.setMontoPagado(pagado);
        r.setSaldoPendiente(c.getMontoCalculado().subtract(pagado).setScale(2, java.math.RoundingMode.HALF_UP));
        r.setMes(c.getConfiguracion().getMes());
        r.setAnio(c.getConfiguracion().getAnio());
        r.setEstadoCuota(c.getEstado().name());
        r.setResidentesNombres(obtenerResidentesDeDepto(c.getDepartamento().getId()));
        r.setPagos(pagoRepository.findByCuotaId(c.getId()).stream()
                .map(this::toPagoDetalle).collect(Collectors.toList()));
        return r;
    }

    private PagoDetalleResponse toPagoDetalle(PagoMantenimiento p) {
        PagoDetalleResponse r = new PagoDetalleResponse();
        r.setPagoId(p.getId());
        r.setLoteId(p.getLoteId());
        r.setPagadorNombre(p.getPagador().getNombre() + " " + p.getPagador().getApellido());
        r.setMonto(p.getMonto()); r.setMetodoPago(p.getMetodoPago().name());
        r.setNumeroOperacion(p.getNumeroOperacion()); r.setVoucherUrl(p.getVoucherUrl());
        r.setEstado(p.getEstado().name()); r.setObservaciones(p.getObservaciones());
        r.setFechaPago(p.getFechaPago()); r.setFechaVerificacion(p.getFechaVerificacion());
        if (p.getVerificadoPor() != null)
            r.setVerificadoPorNombre(p.getVerificadoPor().getNombre() + " " + p.getVerificadoPor().getApellido());
        r.setVerificadoPorCargo(p.getVerificadoPorCargo());
        r.setRegistradoPor(p.getRegistradoPor());
        r.setRegistradoPorNombre(p.getRegistradoPorNombre());
        r.setRegistradoPorCargo(p.getRegistradoPorCargo());
        r.setNumeroDepartamento(p.getCuota().getDepartamento().getNumero());
        r.setPiso(p.getCuota().getDepartamento().getPiso());
        r.setMes(p.getCuota().getConfiguracion().getMes());
        r.setAnio(p.getCuota().getConfiguracion().getAnio());
        return r;
    }
}

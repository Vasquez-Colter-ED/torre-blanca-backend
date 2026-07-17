package pe.torreblanca.backend.service;

import pe.torreblanca.backend.dto.*;
import pe.torreblanca.backend.entity.*;
import pe.torreblanca.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FondoService {

    @Autowired private FondoProyectoRepository proyectoRepository;
    @Autowired private FondoMovimientoRepository movimientoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioRolRepository usuarioRolRepository;
    @Autowired private GastoRepository gastoRepository;
    @Autowired private CategoriaGastoRepository categoriaGastoRepository;
    @Autowired private PagoMantenimientoRepository pagoRepository;
    @Autowired private AuditoriaService auditoriaService;

    private static final String CATEGORIA_FONDO = "Contingencia";

    // Misma ventana de gracia que Gastos: pasado este plazo, un movimiento
    // (o el proyecto al que pertenece) ya pudo estar reflejado en un reporte
    // mensual revisado — eliminarlo después reescribiría historia financiera cerrada
    private static final int DIAS_LIMITE_MODIFICAR = 7;

    // ── Proyectos ────────────────────────────────────────────────────

    public List<FondoProyectoResponse> listarProyectos() {
        return proyectoRepository.findAllOrdenado().stream()
                .map(this::toProyectoResponse).collect(Collectors.toList());
    }

    public FondoProyectoResponse crearProyecto(FondoProyectoRequest request, Integer adminId) {
        verificarDirectivo(adminId);
        if (request.getNombre() == null || request.getNombre().trim().isEmpty())
            throw new RuntimeException("El nombre del proyecto es obligatorio");
        if (request.getFechaInicio() == null)
            throw new RuntimeException("La fecha de inicio es obligatoria");
        if (request.getMetaMonto() != null && request.getMetaMonto().compareTo(BigDecimal.ZERO) < 0)
            throw new RuntimeException("La meta no puede ser negativa");

        FondoProyecto p = new FondoProyecto();
        p.setNombre(request.getNombre());
        p.setDescripcion(request.getDescripcion());
        p.setMetaMonto(request.getMetaMonto());
        p.setFechaInicio(LocalDate.parse(request.getFechaInicio()));
        p.setEstado("ACTIVO");
        p.setCreadoPor(adminId);
        FondoProyecto guardado = proyectoRepository.save(p);

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("nombre", guardado.getNombre());
        datos.put("meta", guardado.getMetaMonto());
        auditoriaService.registrar(adminId, "Proyecto de fondo creado", "fondo_proyectos", guardado.getId(), null, datos);

        return toProyectoResponse(guardado);
    }

    public FondoProyectoResponse cambiarEstadoProyecto(Integer id, String nuevoEstado, Integer adminId) {
        verificarDirectivo(adminId);
        if (!List.of("ACTIVO", "CERRADO", "CANCELADO").contains(nuevoEstado))
            throw new RuntimeException("Estado inválido");
        FondoProyecto p = proyectoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proyecto no encontrado"));
        String estadoAnterior = p.getEstado();
        p.setEstado(nuevoEstado);
        p.setFechaCierre(("CERRADO".equals(nuevoEstado) || "CANCELADO".equals(nuevoEstado)) ? LocalDate.now() : null);
        FondoProyecto guardado = proyectoRepository.save(p);

        auditoriaService.registrar(adminId, "Proyecto de fondo: " + estadoAnterior + " → " + nuevoEstado,
                "fondo_proyectos", id);

        return toProyectoResponse(guardado);
    }

    // Solo se puede eliminar un proyecto si nunca se le registró ningún
    // movimiento (ingreso o retiro) — por ejemplo si se creó de prueba o por
    // error. Si ya tiene movimientos, borrar el proyecto dejaría huérfanos
    // esos registros financieros, así que se bloquea para no romper el
    // historial contable.
    public MensajeResponse eliminarProyecto(Integer id, Integer adminId) {
        verificarDirectivo(adminId);
        FondoProyecto p = proyectoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proyecto no encontrado"));

        List<FondoMovimiento> movimientos = movimientoRepository.findByProyectoId(id);
        if (!movimientos.isEmpty())
            throw new RuntimeException("No se puede eliminar: este proyecto ya tiene " + movimientos.size() +
                    " movimiento" + (movimientos.size() > 1 ? "s" : "") + " registrado" + (movimientos.size() > 1 ? "s" : "") +
                    ". Puedes cancelarlo en su lugar.");

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("nombre", p.getNombre());
        proyectoRepository.delete(p);
        auditoriaService.registrar(adminId, "Proyecto de fondo eliminado", "fondo_proyectos", id, datos, null);

        return new MensajeResponse("Proyecto eliminado correctamente", true);
    }

    // ── Movimientos ──────────────────────────────────────────────────

    public List<FondoMovimientoResponse> listarMovimientosDeProyecto(Integer proyectoId) {
        return movimientoRepository.findByProyectoId(proyectoId).stream()
                .map(this::toMovimientoResponse).collect(Collectors.toList());
    }

    public List<FondoMovimientoResponse> listarMovimientosGenerales() {
        return movimientoRepository.findGenerales().stream()
                .map(this::toMovimientoResponse).collect(Collectors.toList());
    }

    public FondoMovimientoResponse registrarMovimiento(FondoMovimientoRequest request, Integer adminId) {
        verificarDirectivo(adminId);

        if (!List.of("INGRESO", "RETIRO").contains(request.getTipo()))
            throw new RuntimeException("El tipo debe ser INGRESO o RETIRO");
        if (request.getMonto() == null || request.getMonto().compareTo(BigDecimal.ZERO) <= 0)
            throw new RuntimeException("El monto debe ser mayor a cero");
        if (request.getFecha() == null)
            throw new RuntimeException("La fecha es obligatoria");
        if (LocalDate.parse(request.getFecha()).isAfter(LocalDate.now()))
            throw new RuntimeException("La fecha no puede ser futura");
        // El concepto es opcional (se autocompleta si no lo llenan), pero el
        // comprobante SÍ es obligatorio siempre — es la evidencia real de que
        // el dinero efectivamente entró o salió, algo más importante para la
        // auditoría que una descripción de texto libre
        if (request.getComprobanteUrl() == null || request.getComprobanteUrl().trim().isEmpty())
            throw new RuntimeException("La foto del comprobante es obligatoria");

        Usuario admin = usuarioRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        FondoProyecto proyecto = null;
        if (request.getProyectoId() != null) {
            proyecto = proyectoRepository.findById(request.getProyectoId())
                    .orElseThrow(() -> new RuntimeException("Proyecto no encontrado"));
            // Un proyecto ya completado o cancelado no debe seguir recibiendo
            // movimientos — si hacía falta reactivarlo habría que reabrirlo
            // explícitamente primero, no simplemente seguir agregando montos
            if (!"ACTIVO".equals(proyecto.getEstado()))
                throw new RuntimeException("Este proyecto ya está " +
                        ("CERRADO".equals(proyecto.getEstado()) ? "completado" : "cancelado") +
                        " y no admite nuevos movimientos");
        }

        // El retiro se valida contra el saldo REAL del fondo (la única cuenta
        // de banco que existe), no contra el saldo propio del proyecto. Esto
        // es a propósito: para arrancar una actividad (ej. una pollada) casi
        // siempre hay que retirar dinero PRIMERO (comprar insumos) antes de
        // que el proyecto haya recibido ningún ingreso propio. El fondo
        // general le "presta" ese monto al proyecto, y el saldo del proyecto
        // puede quedar en negativo temporalmente (le debe al fondo) hasta que
        // entren los ingresos de la actividad. Lo único que nunca puede
        // pasar es que el FONDO TOTAL quede en negativo, porque esa sí es
        // plata que físicamente no existe.
        if ("RETIRO".equals(request.getTipo())) {
            BigDecimal saldoDisponible = calcularSaldoTotal();
            if (request.getMonto().compareTo(saldoDisponible) > 0)
                throw new RuntimeException("El retiro (S/ " + request.getMonto() + ") supera el saldo disponible del fondo (S/ " +
                        saldoDisponible.setScale(2, java.math.RoundingMode.HALF_UP) + ")");
        }

        // Si no escribieron concepto, se autocompleta con algo legible en vez
        // de dejarlo vacío en las tablas y el Excel
        String concepto = (request.getConcepto() == null || request.getConcepto().trim().isEmpty())
                ? (proyecto != null ? proyecto.getNombre() : "Fondo general") + " — " + ("INGRESO".equals(request.getTipo()) ? "Ingreso" : "Retiro")
                : request.getConcepto();

        // Si es RETIRO, se crea automáticamente el Gasto correspondiente
        // en el módulo de Gastos, categoría "Contingencia"
        Integer gastoId = null;
        if ("RETIRO".equals(request.getTipo())) {
            CategoriaGasto categoria = obtenerOCrearCategoriaFondo();
            LocalDate fecha = LocalDate.parse(request.getFecha());

            Gasto gasto = new Gasto();
            gasto.setCategoria(categoria);
            gasto.setDescripcion(proyecto != null ? proyecto.getNombre() + " — " + concepto : concepto);
            gasto.setMonto(request.getMonto());
            gasto.setFechaGasto(fecha);
            gasto.setMes(fecha.getMonthValue());
            gasto.setAnio(fecha.getYear());
            gasto.setComprobanteUrl(request.getComprobanteUrl());
            gasto.setRegistradoPor(admin);
            gastoId = gastoRepository.save(gasto).getId();
        }

        FondoMovimiento m = new FondoMovimiento();
        m.setProyecto(proyecto);
        m.setTipo(request.getTipo());
        m.setMonto(request.getMonto());
        m.setConcepto(concepto);
        m.setFecha(LocalDate.parse(request.getFecha()));
        m.setComprobanteUrl(request.getComprobanteUrl());
        m.setGastoId(gastoId);
        m.setRegistradoPor(admin);
        FondoMovimiento guardado = movimientoRepository.save(m);

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipo", request.getTipo());
        datos.put("monto", request.getMonto());
        datos.put("concepto", concepto);
        if (proyecto != null) datos.put("proyecto", proyecto.getNombre());
        auditoriaService.registrar(adminId,
                "RETIRO".equals(request.getTipo()) ? "Retiro registrado en fondo" : "Ingreso registrado en fondo",
                "fondo_movimientos", guardado.getId(), null, datos);

        return toMovimientoResponse(guardado);
    }

    public MensajeResponse eliminarMovimiento(Integer id, Integer adminId) {
        verificarDirectivo(adminId);
        FondoMovimiento m = movimientoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Movimiento no encontrado"));

        // Si el movimiento pertenece a un proyecto que ya no está ACTIVO
        // (Completado o Cancelado), no se puede eliminar — borrar un ingreso
        // o retiro de un proyecto ya cerrado alteraría silenciosamente su
        // historial y el saldo del fondo después de que se dio por terminado
        if (m.getProyecto() != null && !"ACTIVO".equals(m.getProyecto().getEstado()))
            throw new RuntimeException("Este movimiento pertenece a un proyecto ya " +
                    ("CERRADO".equals(m.getProyecto().getEstado()) ? "completado" : "cancelado") +
                    ". No se puede eliminar para preservar su historial.");

        // Ventana de gracia — igual que en Gastos
        long dias = java.time.temporal.ChronoUnit.DAYS.between(m.getCreatedAt(), java.time.LocalDateTime.now());
        if (dias > DIAS_LIMITE_MODIFICAR)
            throw new RuntimeException("Este movimiento se registró hace " + dias + " días y ya no se puede eliminar " +
                    "(el límite es " + DIAS_LIMITE_MODIFICAR + " días, para no alterar reportes ya cerrados).");

        // Si tenía un Gasto vinculado (retiro), se elimina también para no dejar duplicado
        if (m.getGastoId() != null) {
            gastoRepository.findById(m.getGastoId()).ifPresent(gastoRepository::delete);
        }

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipo", m.getTipo());
        datos.put("monto", m.getMonto());
        datos.put("concepto", m.getConcepto());
        movimientoRepository.delete(m);
        auditoriaService.registrar(adminId, "Movimiento de fondo eliminado", "fondo_movimientos", id, datos, null);

        return new MensajeResponse("Movimiento eliminado correctamente", true);
    }

    // ── Resumen general ──────────────────────────────────────────────

    // El saldo del fondo ya NO es solo la suma de sus propios movimientos:
    // ahora refleja el efectivo real de la residencial, sumando todo lo
    // recaudado en mantenimiento (módulo Pagos) y los ingresos extra propios
    // del fondo (pollada, donaciones, el ajuste de apertura inicial que se
    // registra como un "Ingreso" general), y restando TODOS los gastos
    // (módulo Gastos) — los retiros del fondo ya están ahí adentro porque
    // cada retiro genera automáticamente su propio Gasto en categoría
    // "Contingencia", así que no se restan dos veces.
    private BigDecimal calcularSaldoTotal() {
        BigDecimal pagosMantenimiento = pagoRepository.sumVerificadoTotal();
        BigDecimal ingresosFondo      = movimientoRepository.sumByTipo("INGRESO");
        BigDecimal gastosTotal        = gastoRepository.sumTotal();
        return pagosMantenimiento.add(ingresosFondo).subtract(gastosTotal);
    }

    // Expuesto para que otros módulos (como Gastos) puedan validar que un
    // nuevo gasto no deje el fondo en negativo, sin importar por qué módulo
    // se registre el egreso — el fondo debe comportarse como una única
    // cuenta bancaria real.
    public BigDecimal calcularSaldoDisponible() {
        return calcularSaldoTotal();
    }

    public FondoResumenResponse resumenGeneral() {
        BigDecimal pagosMantenimiento = pagoRepository.sumVerificadoTotal();
        BigDecimal ingresosFondo      = movimientoRepository.sumByTipo("INGRESO");
        BigDecimal retiradoFondo      = movimientoRepository.sumByTipo("RETIRO");
        BigDecimal gastosTotal        = gastoRepository.sumTotal();

        FondoResumenResponse r = new FondoResumenResponse();
        r.setTotalPagosMantenimiento(pagosMantenimiento);
        r.setTotalIngresosFondo(ingresosFondo);
        r.setTotalGastos(gastosTotal);
        r.setTotalIngresado(ingresosFondo);
        r.setTotalRetirado(retiradoFondo);
        r.setSaldoTotal(pagosMantenimiento.add(ingresosFondo).subtract(gastosTotal));
        r.setProyectosActivos(proyectoRepository.contarActivos());
        return r;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private CategoriaGasto obtenerOCrearCategoriaFondo() {
        return categoriaGastoRepository.findByNombre(CATEGORIA_FONDO)
                .orElseGet(() -> {
                    CategoriaGasto c = new CategoriaGasto();
                    c.setNombre(CATEGORIA_FONDO);
                    c.setDescripcion("Retiros del Fondo de Contingencia");
                    c.setEstado(true);
                    return categoriaGastoRepository.save(c);
                });
    }

    private boolean esDirectivo(Integer usuarioId) {
        return usuarioRolRepository.findRolesActivosByUsuarioId(usuarioId)
                .stream().anyMatch(ur -> ur.getRol().getEsDirectivo());
    }

    private void verificarDirectivo(Integer usuarioId) {
        if (!esDirectivo(usuarioId))
            throw new RuntimeException("No tienes permisos para esta acción");
    }

    private FondoProyectoResponse toProyectoResponse(FondoProyecto p) {
        FondoProyectoResponse r = new FondoProyectoResponse();
        r.setId(p.getId());
        r.setNombre(p.getNombre());
        r.setDescripcion(p.getDescripcion());
        r.setMetaMonto(p.getMetaMonto());
        r.setEstado(p.getEstado());
        r.setFechaInicio(p.getFechaInicio());
        r.setFechaCierre(p.getFechaCierre());
        if (p.getCreadoPor() != null)
            usuarioRepository.findById(p.getCreadoPor())
                    .ifPresent(u -> r.setCreadoPorNombre(u.getNombre() + " " + u.getApellido()));

        BigDecimal ingresado = movimientoRepository.sumByProyectoAndTipo(p.getId(), "INGRESO");
        BigDecimal retirado  = movimientoRepository.sumByProyectoAndTipo(p.getId(), "RETIRO");
        r.setTotalIngresado(ingresado);
        r.setTotalRetirado(retirado);
        r.setSaldo(ingresado.subtract(retirado));
        r.setTieneMovimientos(ingresado.compareTo(BigDecimal.ZERO) > 0 || retirado.compareTo(BigDecimal.ZERO) > 0);
        return r;
    }

    private FondoMovimientoResponse toMovimientoResponse(FondoMovimiento m) {
        FondoMovimientoResponse r = new FondoMovimientoResponse();
        r.setId(m.getId());
        if (m.getProyecto() != null) {
            r.setProyectoId(m.getProyecto().getId());
            r.setProyectoNombre(m.getProyecto().getNombre());
        }
        r.setTipo(m.getTipo());
        r.setMonto(m.getMonto());
        r.setConcepto(m.getConcepto());
        r.setFecha(m.getFecha());
        r.setComprobanteUrl(m.getComprobanteUrl());
        r.setGastoId(m.getGastoId());
        if (m.getRegistradoPor() != null)
            r.setRegistradoPorNombre(m.getRegistradoPor().getNombre() + " " + m.getRegistradoPor().getApellido());

        boolean proyectoNoActivo = m.getProyecto() != null && !"ACTIVO".equals(m.getProyecto().getEstado());
        long dias = java.time.temporal.ChronoUnit.DAYS.between(m.getCreatedAt(), java.time.LocalDateTime.now());
        r.setPuedeEliminar(!proyectoNoActivo && dias <= DIAS_LIMITE_MODIFICAR);
        return r;
    }
}

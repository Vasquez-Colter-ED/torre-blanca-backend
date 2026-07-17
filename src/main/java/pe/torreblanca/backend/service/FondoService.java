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
        if (request.getConcepto() == null || request.getConcepto().trim().isEmpty())
            throw new RuntimeException("El concepto es obligatorio");
        if (request.getFecha() == null)
            throw new RuntimeException("La fecha es obligatoria");
        if (LocalDate.parse(request.getFecha()).isAfter(LocalDate.now()))
            throw new RuntimeException("La fecha no puede ser futura");

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

        // Un retiro nunca puede dejar el saldo (del proyecto, o general si no
        // pertenece a ninguno) en negativo — el fondo debe comportarse como
        // una cuenta bancaria real, que no puede sobregirarse
        if ("RETIRO".equals(request.getTipo())) {
            BigDecimal saldoDisponible = proyecto != null
                    ? movimientoRepository.sumByProyectoAndTipo(proyecto.getId(), "INGRESO")
                        .subtract(movimientoRepository.sumByProyectoAndTipo(proyecto.getId(), "RETIRO"))
                    : calcularSaldoTotal();
            if (request.getMonto().compareTo(saldoDisponible) > 0)
                throw new RuntimeException("El retiro (S/ " + request.getMonto() + ") supera el saldo disponible " +
                        (proyecto != null ? "del proyecto" : "del fondo") + " (S/ " + saldoDisponible.setScale(2, java.math.RoundingMode.HALF_UP) + ")");
        }

        // Si es RETIRO, se crea automáticamente el Gasto correspondiente
        // en el módulo de Gastos, categoría "Contingencia"
        Integer gastoId = null;
        if ("RETIRO".equals(request.getTipo())) {
            CategoriaGasto categoria = obtenerOCrearCategoriaFondo();
            LocalDate fecha = LocalDate.parse(request.getFecha());

            Gasto gasto = new Gasto();
            gasto.setCategoria(categoria);
            gasto.setDescripcion(proyecto != null ? proyecto.getNombre() + " — " + request.getConcepto() : request.getConcepto());
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
        m.setConcepto(request.getConcepto());
        m.setFecha(LocalDate.parse(request.getFecha()));
        m.setComprobanteUrl(request.getComprobanteUrl());
        m.setGastoId(gastoId);
        m.setRegistradoPor(admin);
        FondoMovimiento guardado = movimientoRepository.save(m);

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipo", request.getTipo());
        datos.put("monto", request.getMonto());
        datos.put("concepto", request.getConcepto());
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
        return r;
    }
}

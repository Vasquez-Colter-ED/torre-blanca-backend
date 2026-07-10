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

        Usuario admin = usuarioRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        FondoProyecto proyecto = null;
        if (request.getProyectoId() != null) {
            proyecto = proyectoRepository.findById(request.getProyectoId())
                    .orElseThrow(() -> new RuntimeException("Proyecto no encontrado"));
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

    public FondoResumenResponse resumenGeneral() {
        BigDecimal ingresado = movimientoRepository.sumByTipo("INGRESO");
        BigDecimal retirado  = movimientoRepository.sumByTipo("RETIRO");
        FondoResumenResponse r = new FondoResumenResponse();
        r.setTotalIngresado(ingresado);
        r.setTotalRetirado(retirado);
        r.setSaldoTotal(ingresado.subtract(retirado));
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

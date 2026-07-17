package pe.torreblanca.backend.service;

import pe.torreblanca.backend.dto.*;
import pe.torreblanca.backend.entity.*;
import pe.torreblanca.backend.repository.*;
import pe.torreblanca.backend.util.ValidacionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GastosService {

    // Ventana de gracia para corregir un error recién registrado (typo en el
    // monto, categoría equivocada, etc.). Pasado este plazo, el gasto ya
    // pudo haber sido incluido en un reporte mensual revisado por el
    // directorio (o el docente) y editar/eliminarlo reescribiría historia
    // financiera ya cerrada — en su lugar, cualquier corrección tardía debe
    // hacerse con un nuevo movimiento que quede trazado en Auditoría.
    private static final int DIAS_LIMITE_MODIFICAR = 7;

    @Autowired private GastoRepository gastoRepository;
    @Autowired private CategoriaGastoRepository categoriaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioRolRepository usuarioRolRepository;
    @Autowired private FondoMovimientoRepository fondoMovimientoRepository;
    @Autowired private FondoService fondoService;
    @Autowired private AuditoriaService auditoriaService;

    public List<GastoResponse> listarTodos() {
        return gastoRepository.findAllOrdenados().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public List<GastoResponse> listarPorMes(Integer mes, Integer anio) {
        return gastoRepository.findByMesAndAnio(mes, anio).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public List<CategoriaGasto> listarCategorias() {
        return categoriaRepository.findByEstadoTrue();
    }

    public BigDecimal totalPorMes(Integer mes, Integer anio) {
        return gastoRepository.sumByMesAndAnio(mes, anio);
    }

    public GastoResponse crear(GastoRequest request, Integer adminId) {
        verificarDirectivo(adminId);

        ValidacionUtil.validarTextoLibreRequerido(request.getDescripcion(), "La descripción");
        if (request.getMonto() == null || request.getMonto().compareTo(BigDecimal.ZERO) <= 0)
            throw new RuntimeException("El monto debe ser mayor a cero");

        LocalDate fecha = LocalDate.parse(request.getFechaGasto());
        if (fecha.isAfter(LocalDate.now()))
            throw new RuntimeException("La fecha del gasto no puede ser futura");

        // El fondo de la residencial (el saldo real de la cuenta) nunca puede
        // quedar en negativo, sin importar desde qué módulo se registre el
        // egreso — así que todo gasto nuevo se valida contra el efectivo
        // realmente disponible
        BigDecimal saldoDisponible = fondoService.calcularSaldoDisponible();
        if (request.getMonto().compareTo(saldoDisponible) > 0)
            throw new RuntimeException("Este gasto (S/ " + request.getMonto() + ") supera el saldo disponible del fondo (S/ " +
                    saldoDisponible.setScale(2, java.math.RoundingMode.HALF_UP) + "). No se puede registrar.");

        CategoriaGasto categoria = categoriaRepository.findById(request.getCategoriaId())
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));
        Usuario admin = usuarioRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Gasto gasto = new Gasto();
        gasto.setCategoria(categoria);
        gasto.setDescripcion(request.getDescripcion());
        gasto.setMonto(request.getMonto());
        gasto.setFechaGasto(fecha);
        gasto.setMes(fecha.getMonthValue());
        gasto.setAnio(fecha.getYear());
        gasto.setComprobanteUrl(request.getComprobanteUrl());
        gasto.setRegistradoPor(admin);

        Gasto guardado = gastoRepository.save(gasto);

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("categoria", categoria.getNombre());
        datos.put("monto", guardado.getMonto());
        datos.put("fechaGasto", guardado.getFechaGasto().toString());
        auditoriaService.registrar(adminId, "Gasto registrado", "gastos", guardado.getId(), null, datos);

        return toResponse(guardado);
    }

    public GastoResponse editar(Integer id, GastoRequest request, Integer adminId) {
        verificarDirectivo(adminId);

        ValidacionUtil.validarTextoLibreRequerido(request.getDescripcion(), "La descripción");
        if (request.getMonto() == null || request.getMonto().compareTo(BigDecimal.ZERO) <= 0)
            throw new RuntimeException("El monto debe ser mayor a cero");

        Gasto gasto = gastoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Gasto no encontrado"));

        // Si este gasto nació de un retiro del Fondo de Contingencia, no se
        // puede editar desde acá — se rompe el vínculo con el monto/fecha del
        // movimiento original. Debe editarse (eliminarse y volver a registrar)
        // desde el módulo Fondo.
        if (fondoMovimientoRepository.findByGastoId(id).isPresent())
            throw new RuntimeException("Este gasto pertenece a un retiro del Fondo de Contingencia. Edítalo desde el módulo Fondo.");

        verificarDentroDePlazo(gasto);

        CategoriaGasto categoria = categoriaRepository.findById(request.getCategoriaId())
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));

        LocalDate fecha = LocalDate.parse(request.getFechaGasto());
        if (fecha.isAfter(LocalDate.now()))
            throw new RuntimeException("La fecha del gasto no puede ser futura");

        // Si el monto sube, el excedente frente al monto anterior también
        // tiene que caber en el saldo disponible del fondo
        BigDecimal incremento = request.getMonto().subtract(gasto.getMonto());
        if (incremento.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal saldoDisponible = fondoService.calcularSaldoDisponible();
            if (incremento.compareTo(saldoDisponible) > 0)
                throw new RuntimeException("El nuevo monto supera el saldo disponible del fondo (S/ " +
                        saldoDisponible.setScale(2, java.math.RoundingMode.HALF_UP) + "). No se puede guardar.");
        }

        Map<String, Object> antes = new LinkedHashMap<>();
        antes.put("categoria", gasto.getCategoria().getNombre());
        antes.put("monto", gasto.getMonto());
        antes.put("fechaGasto", gasto.getFechaGasto().toString());

        gasto.setCategoria(categoria);
        gasto.setDescripcion(request.getDescripcion());
        gasto.setMonto(request.getMonto());
        gasto.setFechaGasto(fecha);
        gasto.setMes(fecha.getMonthValue());
        gasto.setAnio(fecha.getYear());
        gasto.setComprobanteUrl(request.getComprobanteUrl());

        Gasto guardado = gastoRepository.save(gasto);

        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("categoria", categoria.getNombre());
        despues.put("monto", guardado.getMonto());
        despues.put("fechaGasto", guardado.getFechaGasto().toString());
        auditoriaService.registrar(adminId, "Gasto editado", "gastos", guardado.getId(), antes, despues);

        return toResponse(guardado);
    }

    public MensajeResponse eliminar(Integer id, Integer adminId) {
        verificarDirectivo(adminId);
        Gasto gasto = gastoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Gasto no encontrado"));

        // Mismo resguardo que en editar(): si viene de un retiro del Fondo,
        // debe eliminarse desde ahí para no dejar el movimiento huérfano
        if (fondoMovimientoRepository.findByGastoId(id).isPresent())
            throw new RuntimeException("Este gasto pertenece a un retiro del Fondo de Contingencia. Elíminalo desde el módulo Fondo.");

        verificarDentroDePlazo(gasto);

        Map<String, Object> antes = new LinkedHashMap<>();
        antes.put("categoria", gasto.getCategoria().getNombre());
        antes.put("monto", gasto.getMonto());
        antes.put("fechaGasto", gasto.getFechaGasto().toString());

        gastoRepository.delete(gasto);
        auditoriaService.registrar(adminId, "Gasto eliminado", "gastos", id, antes, null);
        return new MensajeResponse("Gasto eliminado correctamente", true);
    }

    private void verificarDentroDePlazo(Gasto gasto) {
        long dias = java.time.temporal.ChronoUnit.DAYS.between(gasto.getCreatedAt(), java.time.LocalDateTime.now());
        if (dias > DIAS_LIMITE_MODIFICAR)
            throw new RuntimeException("Este gasto se registró hace " + dias + " días y ya no se puede modificar (el límite es " +
                    DIAS_LIMITE_MODIFICAR + " días, para no alterar reportes ya cerrados). Si fue un error, registra un gasto o ajuste nuevo que quede trazado en Auditoría.");
    }

    private boolean esDirectivo(Integer usuarioId) {
        return usuarioRolRepository.findRolesActivosByUsuarioId(usuarioId)
                .stream().anyMatch(ur -> ur.getRol().getEsDirectivo());
    }

    private void verificarDirectivo(Integer usuarioId) {
        if (!esDirectivo(usuarioId))
            throw new RuntimeException("No tienes permisos para esta acción");
    }

    private GastoResponse toResponse(Gasto g) {
        GastoResponse r = new GastoResponse();
        r.setId(g.getId());
        r.setCategoria(g.getCategoria().getNombre());
        r.setDescripcion(g.getDescripcion());
        r.setMonto(g.getMonto());
        r.setFechaGasto(g.getFechaGasto().toString());
        r.setMes(g.getMes());
        r.setAnio(g.getAnio());
        r.setComprobanteUrl(g.getComprobanteUrl());
        if (g.getRegistradoPor() != null)
            r.setRegistradoPorNombre(g.getRegistradoPor().getNombre() + " " + g.getRegistradoPor().getApellido());

        boolean esDeFondo = fondoMovimientoRepository.findByGastoId(g.getId()).isPresent();
        long dias = java.time.temporal.ChronoUnit.DAYS.between(g.getCreatedAt(), java.time.LocalDateTime.now());
        r.setPuedeEliminar(!esDeFondo && dias <= DIAS_LIMITE_MODIFICAR);
        return r;
    }
}

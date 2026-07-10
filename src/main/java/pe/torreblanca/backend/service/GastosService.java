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

    @Autowired private GastoRepository gastoRepository;
    @Autowired private CategoriaGastoRepository categoriaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioRolRepository usuarioRolRepository;
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

        CategoriaGasto categoria = categoriaRepository.findById(request.getCategoriaId())
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));
        Usuario admin = usuarioRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        LocalDate fecha = LocalDate.parse(request.getFechaGasto());

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
        CategoriaGasto categoria = categoriaRepository.findById(request.getCategoriaId())
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));

        Map<String, Object> antes = new LinkedHashMap<>();
        antes.put("categoria", gasto.getCategoria().getNombre());
        antes.put("monto", gasto.getMonto());
        antes.put("fechaGasto", gasto.getFechaGasto().toString());

        LocalDate fecha = LocalDate.parse(request.getFechaGasto());
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

        Map<String, Object> antes = new LinkedHashMap<>();
        antes.put("categoria", gasto.getCategoria().getNombre());
        antes.put("monto", gasto.getMonto());
        antes.put("fechaGasto", gasto.getFechaGasto().toString());

        gastoRepository.delete(gasto);
        auditoriaService.registrar(adminId, "Gasto eliminado", "gastos", id, antes, null);
        return new MensajeResponse("Gasto eliminado correctamente", true);
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
        return r;
    }
}

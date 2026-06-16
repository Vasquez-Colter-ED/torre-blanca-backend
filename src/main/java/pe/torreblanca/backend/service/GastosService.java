package pe.torreblanca.backend.service;

import pe.torreblanca.backend.dto.*;
import pe.torreblanca.backend.entity.*;
import pe.torreblanca.backend.repository.*;
import pe.torreblanca.backend.util.ValidacionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GastosService {

    @Autowired private GastoRepository gastoRepository;
    @Autowired private CategoriaGastoRepository categoriaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioRolRepository usuarioRolRepository;

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

        return toResponse(gastoRepository.save(gasto));
    }

    public GastoResponse editar(Integer id, GastoRequest request, Integer adminId) {
        verificarDirectivo(adminId);

        ValidacionUtil.validarTextoLibreRequerido(request.getDescripcion(), "La descripción");

        Gasto gasto = gastoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Gasto no encontrado"));
        CategoriaGasto categoria = categoriaRepository.findById(request.getCategoriaId())
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));

        LocalDate fecha = LocalDate.parse(request.getFechaGasto());
        gasto.setCategoria(categoria);
        gasto.setDescripcion(request.getDescripcion());
        gasto.setMonto(request.getMonto());
        gasto.setFechaGasto(fecha);
        gasto.setMes(fecha.getMonthValue());
        gasto.setAnio(fecha.getYear());
        gasto.setComprobanteUrl(request.getComprobanteUrl());

        return toResponse(gastoRepository.save(gasto));
    }

    public MensajeResponse eliminar(Integer id, Integer adminId) {
        verificarDirectivo(adminId);
        Gasto gasto = gastoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Gasto no encontrado"));
        gastoRepository.delete(gasto);
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

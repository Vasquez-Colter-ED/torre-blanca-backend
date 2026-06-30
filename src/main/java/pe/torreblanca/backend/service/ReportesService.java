package pe.torreblanca.backend.service;

import pe.torreblanca.backend.dto.*;
import pe.torreblanca.backend.entity.*;
import pe.torreblanca.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportesService {

    @Autowired private ConfiguracionMantenimientoRepository configuracionRepository;
    @Autowired private CuotaMantenimientoRepository cuotaRepository;
    @Autowired private GastoRepository gastoRepository;
    @Autowired private PropietarioDepartamentoRepository propietarioDeptoRepository;
    @Autowired private InquilinoDepartamentoRepository inquilinoDeptoRepository;
    @Autowired private PagoMantenimientoRepository pagoRepository;

    private static final String[] NOMBRES_MESES = {
        "Ene","Feb","Mar","Abr","May","Jun","Jul","Ago","Sep","Oct","Nov","Dic"
    };

    // ── Reporte mensual ───────────────────────────────────────────────

    public ReportesMesResponse reporteMes(Integer mes, Integer anio) {
        ReportesMesResponse r = new ReportesMesResponse();
        r.setMes(mes); r.setAnio(anio);

        Optional<ConfiguracionMantenimiento> configOpt = configuracionRepository.findByMesAndAnio(mes, anio);

        BigDecimal recaudado  = BigDecimal.ZERO;
        BigDecimal esperado   = BigDecimal.ZERO;
        int pagados = 0, total = 0;
        List<DeudorInfo> deudores = new ArrayList<>();

        if (configOpt.isPresent()) {
            List<CuotaMantenimiento> cuotas = cuotaRepository.findByConfiguracionId(configOpt.get().getId());
            total = cuotas.size();
            for (CuotaMantenimiento c : cuotas) {
                esperado = esperado.add(c.getMontoCalculado());
                if (c.getEstado() == EstadoCuota.PAGADO) {
                    recaudado = recaudado.add(c.getMontoCalculado());
                    pagados++;
                } else {
                    DeudorInfo d = new DeudorInfo();
                    d.setNumeroDepartamento(c.getDepartamento().getNumero());
                    d.setPiso(c.getDepartamento().getPiso());
                    d.setMontoPendiente(c.getMontoCalculado());
                    d.setResidentesNombres(obtenerResidentesDeDepto(c.getDepartamento().getId()));
                    deudores.add(d);
                }
            }
        }

        BigDecimal gastos = gastoRepository.sumByMesAndAnio(mes, anio);

        // Gastos por categoría
        Map<String, BigDecimal> porCategoria = new LinkedHashMap<>();
        gastoRepository.findByMesAndAnio(mes, anio).forEach(g -> {
            String cat = g.getCategoria().getNombre();
            porCategoria.merge(cat, g.getMonto(), BigDecimal::add);
        });

        r.setTotalRecaudado(recaudado);
        r.setTotalEsperado(esperado);
        r.setTotalGastos(gastos);
        r.setBalance(recaudado.subtract(gastos));
        r.setDeptosPagados(pagados);
        r.setDeptosTotal(total);
        r.setDeudores(deudores);
        r.setGastosPorCategoria(porCategoria);

        return r;
    }

    // ── Reporte anual ─────────────────────────────────────────────────

    public ReportesAnioResponse reporteAnio(Integer anio) {
        ReportesAnioResponse r = new ReportesAnioResponse();
        r.setAnio(anio);

        List<ReportesAnioResponse.DatoMensual> datos = new ArrayList<>();
        BigDecimal totalRecAnio   = BigDecimal.ZERO;
        BigDecimal totalGastAnio  = BigDecimal.ZERO;

        for (int m = 1; m <= 12; m++) {
            Optional<ConfiguracionMantenimiento> configOpt = configuracionRepository.findByMesAndAnio(m, anio);

            BigDecimal recMes  = BigDecimal.ZERO;
            BigDecimal gastMes = gastoRepository.sumByMesAndAnio(m, anio);
            int pagados = 0, total = 0;

            if (configOpt.isPresent()) {
                List<CuotaMantenimiento> cuotas = cuotaRepository.findByConfiguracionId(configOpt.get().getId());
                total = cuotas.size();
                for (CuotaMantenimiento c : cuotas) {
                    if (c.getEstado() == EstadoCuota.PAGADO) {
                        recMes = recMes.add(c.getMontoCalculado());
                        pagados++;
                    }
                }
            }

            ReportesAnioResponse.DatoMensual dato = new ReportesAnioResponse.DatoMensual();
            dato.setMes(NOMBRES_MESES[m - 1]);
            dato.setRecaudado(recMes);
            dato.setGastos(gastMes);
            dato.setBalance(recMes.subtract(gastMes));
            dato.setPagados(pagados);
            dato.setTotal(total);
            datos.add(dato);

            totalRecAnio  = totalRecAnio.add(recMes);
            totalGastAnio = totalGastAnio.add(gastMes);
        }

        r.setDatosMensuales(datos);
        r.setTotalRecaudadoAnio(totalRecAnio);
        r.setTotalGastosAnio(totalGastAnio);
        r.setBalanceAnio(totalRecAnio.subtract(totalGastAnio));

        return r;
    }

    // ── Helper ────────────────────────────────────────────────────────

    // ── Auditoría de pagos ───────────────────────────────────────────
    // Lista cronológica de TODOS los movimientos de pago (registro y
    // verificación), con nombre y cargo exacto de quién hizo cada acción
    // en el momento en que la hizo. Si se pasan mes/anio, filtra solo
    // los pagos de cuotas de ese periodo; si no, devuelve todo el historial.
    public List<AuditoriaPagoResponse> obtenerAuditoria(Integer mes, Integer anio) {
        List<PagoMantenimiento> pagos = (mes != null && anio != null)
                ? pagoRepository.findByMesAndAnio(mes, anio)
                : pagoRepository.findAllOrdenadoDesc();

        return pagos.stream().map(p -> {
            AuditoriaPagoResponse a = new AuditoriaPagoResponse();
            a.setPagoId(p.getId());
            a.setNumeroDepartamento(p.getCuota().getDepartamento().getNumero());
            a.setPiso(p.getCuota().getDepartamento().getPiso());
            a.setMes(p.getCuota().getConfiguracion().getMes());
            a.setAnio(p.getCuota().getConfiguracion().getAnio());
            a.setMonto(p.getMonto());
            a.setMetodoPago(p.getMetodoPago().name());
            a.setEsPasarela(p.getObservaciones() != null && p.getObservaciones().toLowerCase().contains("mercado pago"));
            a.setPagadorNombre(p.getPagador().getNombre() + " " + p.getPagador().getApellido());

            a.setFechaRegistro(p.getFechaPago() != null ? p.getFechaPago() : p.getCreatedAt());
            a.setRegistradoPor(p.getRegistradoPor());
            a.setRegistradoPorNombre(p.getRegistradoPorNombre());
            a.setRegistradoPorCargo(p.getRegistradoPorCargo());

            a.setEstado(p.getEstado().name());
            a.setFechaVerificacion(p.getFechaVerificacion());
            if (p.getVerificadoPor() != null)
                a.setVerificadoPorNombre(p.getVerificadoPor().getNombre() + " " + p.getVerificadoPor().getApellido());
            a.setVerificadoPorCargo(p.getVerificadoPorCargo());

            a.setObservaciones(p.getObservaciones());
            return a;
        }).collect(Collectors.toList());
    }

    private String obtenerResidentesDeDepto(Integer deptoId) {
        List<String> nombres = new ArrayList<>();
        propietarioDeptoRepository.findActivoByDepartamentoId(deptoId)
                .ifPresent(pd -> nombres.add(pd.getUsuario().getNombre() + " " + pd.getUsuario().getApellido()));
        inquilinoDeptoRepository.findActivosByDepartamentoId(deptoId)
                .forEach(i -> nombres.add(i.getUsuario().getNombre() + " " + i.getUsuario().getApellido()));
        return nombres.isEmpty() ? "Sin asignar" : String.join(", ", nombres);
    }
}

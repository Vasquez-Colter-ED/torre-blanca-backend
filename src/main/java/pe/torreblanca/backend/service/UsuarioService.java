package pe.torreblanca.backend.service;

import pe.torreblanca.backend.dto.*;
import pe.torreblanca.backend.entity.*;
import pe.torreblanca.backend.repository.*;
import pe.torreblanca.backend.util.ValidacionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UsuarioService {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioRolRepository usuarioRolRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioPermisoRepository usuarioPermisoRepository;
    @Autowired private ModuloRepository moduloRepository;
    @Autowired private PermisoRepository permisoRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PropietarioDepartamentoRepository propietarioDeptoRepository;
    @Autowired private InquilinoDepartamentoRepository inquilinoDeptoRepository;
    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private AuditoriaService auditoriaService;

    public List<UsuarioResponse> listarTodos() {
        return usuarioRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    public UsuarioResponse obtenerPorId(Integer id) {
        return toResponse(usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado")));
    }

    public UsuarioResponse crear(CrearUsuarioRequest request, Integer adminId) {
        verificarDirectivo(adminId, "crear usuarios");

        // Validaciones básicas
        ValidacionUtil.validarNombre(request.getNombre(), "El nombre");
        ValidacionUtil.validarNombre(request.getApellido(), "El apellido");

        boolean tieneEmail = request.getEmail() != null && !request.getEmail().trim().isEmpty();
        boolean tieneDni   = request.getDni()   != null && !request.getDni().trim().isEmpty();
        if (!tieneEmail && !tieneDni)
            throw new RuntimeException("Debe ingresar al menos un correo electrónico o un DNI para que el usuario pueda iniciar sesión");

        if (tieneEmail) {
            ValidacionUtil.validarEmail(request.getEmail());
            if (usuarioRepository.existsByEmail(request.getEmail()))
                throw new RuntimeException("El correo electrónico ya está registrado");
        }
        if (tieneDni) {
            ValidacionUtil.validarDni(request.getDni());
            if (usuarioRepository.existsByDni(request.getDni()))
                throw new RuntimeException("El DNI ya está registrado");
        }
        if (request.getTelefono() != null && !request.getTelefono().trim().isEmpty())
            ValidacionUtil.validarTelefono(request.getTelefono());

        // Departamento obligatorio
        if (request.getDepartamentoId() == null)
            throw new RuntimeException("Debe asignar un departamento al usuario");
        String tipo = request.getTipoResidencia() != null ? request.getTipoResidencia() : "PROPIETARIO";
        if (!tipo.equals("PROPIETARIO") && !tipo.equals("INQUILINO"))
            throw new RuntimeException("El tipo de residencia debe ser PROPIETARIO o INQUILINO");

        Departamento depto = departamentoRepository.findById(request.getDepartamentoId())
                .orElseThrow(() -> new RuntimeException("Departamento no encontrado"));

        // Validar disponibilidad del departamento
        if (tipo.equals("PROPIETARIO") && propietarioDeptoRepository.findActivoByDepartamentoId(depto.getId()).isPresent())
            throw new RuntimeException("El departamento " + depto.getNumero() + " ya tiene un propietario activo. Primero debe desvincularlo.");
        if (tipo.equals("INQUILINO")) {
            long actuales = inquilinoDeptoRepository.findActivosByDepartamentoId(depto.getId()).size();
            if (actuales >= 5)
                throw new RuntimeException("El departamento " + depto.getNumero() + " ya tiene 5 inquilinos, que es el máximo permitido");
        }

        // Cargo directivo opcional — solo roles con es_directivo = true
        if (request.getCargoDirectivoId() != null) {
            Rol rol = rolRepository.findById(request.getCargoDirectivoId())
                    .orElseThrow(() -> new RuntimeException("Cargo directivo no encontrado"));
            if (!rol.getEsDirectivo())
                throw new RuntimeException("El cargo seleccionado no es un cargo directivo válido");
        }

        // Crear usuario
        Usuario nuevo = new Usuario();
        nuevo.setNombre(request.getNombre());
        nuevo.setApellido(request.getApellido());
        nuevo.setDni(tieneDni ? request.getDni().trim() : null);
        nuevo.setEmail(tieneEmail ? request.getEmail().trim() : null);
        nuevo.setTelefono(request.getTelefono());
        nuevo.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        nuevo.setEstado(EstadoUsuario.ACTIVO);
        Usuario guardado = usuarioRepository.save(nuevo);

        // Asignar al departamento según tipo
        if (tipo.equals("PROPIETARIO")) {
            PropietarioDepartamento pd = new PropietarioDepartamento();
            pd.setUsuario(guardado); pd.setDepartamento(depto);
            pd.setFechaInicio(LocalDate.now()); pd.setEstado(true);
            propietarioDeptoRepository.save(pd);
        } else {
            // Para inquilino buscamos al propietario activo del depto
            propietarioDeptoRepository.findActivoByDepartamentoId(depto.getId()).ifPresent(propDep -> {
                InquilinoDepartamento inq = new InquilinoDepartamento();
                inq.setUsuario(guardado); inq.setDepartamento(depto);
                inq.setPropietario(propDep.getUsuario());
                inq.setFechaInicio(LocalDate.now()); inq.setEstado(true);
                inquilinoDeptoRepository.save(inq);
            });
            if (propietarioDeptoRepository.findActivoByDepartamentoId(depto.getId()).isEmpty()) {
                // Sin propietario aún, lo guardamos igual (propietario_id null)
                InquilinoDepartamento inq = new InquilinoDepartamento();
                inq.setUsuario(guardado); inq.setDepartamento(depto);
                inq.setFechaInicio(LocalDate.now()); inq.setEstado(true);
                inquilinoDeptoRepository.save(inq);
            }
        }

        // Asignar cargo directivo si se indicó
        if (request.getCargoDirectivoId() != null)
            asignarRol(guardado.getId(), request.getCargoDirectivoId(), adminId);

        Map<String, Object> datosNuevos = new LinkedHashMap<>();
        datosNuevos.put("nombre", guardado.getNombre() + " " + guardado.getApellido());
        datosNuevos.put("email", guardado.getEmail());
        datosNuevos.put("dni", guardado.getDni());
        datosNuevos.put("departamento", depto.getNumero());
        datosNuevos.put("tipoResidencia", tipo);
        auditoriaService.registrar(adminId, "Usuario creado", "usuarios", guardado.getId(), null, datosNuevos);

        return toResponse(guardado);
    }

    public UsuarioResponse editar(Integer id, EditarUsuarioRequest request, Integer solicitanteId) {
        boolean esAdmin = esDirectivo(solicitanteId);
        if (!esAdmin && !id.equals(solicitanteId))
            throw new RuntimeException("Solo puedes editar tu propio perfil");

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Map<String, Object> antes = new LinkedHashMap<>();
        antes.put("nombre", usuario.getNombre() + " " + usuario.getApellido());
        antes.put("telefono", usuario.getTelefono());
        antes.put("dni", usuario.getDni());
        antes.put("email", usuario.getEmail());

        if (request.getNombre() != null) {
            ValidacionUtil.validarNombre(request.getNombre(), "El nombre");
            usuario.setNombre(request.getNombre());
        }
        if (request.getApellido() != null) {
            ValidacionUtil.validarNombre(request.getApellido(), "El apellido");
            usuario.setApellido(request.getApellido());
        }
        if (request.getTelefono() != null) {
            ValidacionUtil.validarTelefono(request.getTelefono());
            usuario.setTelefono(request.getTelefono());
        }
        if (request.getDni() != null) {
            ValidacionUtil.validarDni(request.getDni());
            usuario.setDni(request.getDni());
        }
        if (request.getEmail() != null && !request.getEmail().equals(usuario.getEmail())) {
            ValidacionUtil.validarEmail(request.getEmail());
            if (usuarioRepository.existsByEmail(request.getEmail()))
                throw new RuntimeException("El email ya está en uso");
            usuario.setEmail(request.getEmail());
        }
        // La contraseña NO se valida contra caracteres especiales — se permiten para mayor seguridad
        if (request.getNuevaPassword() != null && !request.getNuevaPassword().isBlank()) {
            if (!esAdmin && !id.equals(solicitanteId))
                throw new RuntimeException("No puedes cambiar la contraseña de otro usuario");
            usuario.setPasswordHash(passwordEncoder.encode(request.getNuevaPassword()));
        }

        // Solo directivos pueden cambiar rol
        Integer rolIdEfectivo = request.rolIdEfectivo();
        if (rolIdEfectivo != null && esAdmin) {
            if (rolIdEfectivo == 0) {
                // Señal explícita: quitar el cargo directivo actual del usuario
                usuarioRolRepository.findRolesActivosByUsuarioId(id).stream()
                        .filter(ur -> ur.getRol().getEsDirectivo())
                        .findFirst()
                        .ifPresent(ur -> revocarRol(id, ur.getRol().getId(), solicitanteId));
            } else {
                asignarRol(id, rolIdEfectivo, solicitanteId);
            }
        }

        // Solo directivos pueden cambiar departamento y tipo
        if (request.getDepartamentoId() != null && esAdmin) {
            Departamento depto = departamentoRepository.findById(request.getDepartamentoId()).orElse(null);
            if (depto != null) {
                String tipo = request.getTipoResidencia() != null ? request.getTipoResidencia() : "PROPIETARIO";
                if ("INQUILINO".equals(tipo)) {
                    // Verificar que haya propietario en el depto
                    long actuales = inquilinoDeptoRepository.findActivosByDepartamentoId(depto.getId()).size();
                    if (actuales >= 5) throw new RuntimeException("El departamento ya alcanzó el máximo de inquilinos");
                    // Buscar propietario activo del depto
                    propietarioDeptoRepository.findActivoByDepartamentoId(depto.getId()).ifPresent(propDep -> {
                        InquilinoDepartamento inq = new InquilinoDepartamento();
                        inq.setUsuario(usuario); inq.setDepartamento(depto);
                        inq.setPropietario(propDep.getUsuario());
                        inq.setFechaInicio(LocalDate.now()); inq.setEstado(true);
                        inquilinoDeptoRepository.save(inq);
                    });
                } else {
                    // Asignar como propietario
                    propietarioDeptoRepository.findActivoByDepartamentoId(depto.getId())
                            .ifPresent(pd -> { pd.setEstado(false); propietarioDeptoRepository.save(pd); });
                    PropietarioDepartamento pd = new PropietarioDepartamento();
                    pd.setUsuario(usuario); pd.setDepartamento(depto);
                    pd.setFechaInicio(LocalDate.now()); pd.setEstado(true);
                    propietarioDeptoRepository.save(pd);
                }
            }
        }

        Usuario guardado = usuarioRepository.save(usuario);

        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("nombre", guardado.getNombre() + " " + guardado.getApellido());
        despues.put("telefono", guardado.getTelefono());
        despues.put("dni", guardado.getDni());
        despues.put("email", guardado.getEmail());
        auditoriaService.registrar(solicitanteId, "Usuario editado", "usuarios", guardado.getId(), antes, despues);

        return toResponse(guardado);
    }

    public MensajeResponse desactivar(Integer id, Integer solicitanteId) {
        verificarDirectivo(solicitanteId, "desactivar usuarios");
        if (id.equals(solicitanteId)) throw new RuntimeException("No puedes desactivar tu propia cuenta");
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (esDirectivo(id)) throw new RuntimeException("No puedes desactivar a un directivo");

        // Al desactivar, se desvincula automáticamente de todos los departamentos
        // donde figure como propietario o inquilino activo. El historial de pagos,
        // boletas y recibos NO se toca — sigue intacto para efectos de Reportes.
        List<String> deptosPropietario = new ArrayList<>();
        propietarioDeptoRepository.findActivosByUsuarioId(id).forEach(pd -> {
            deptosPropietario.add(pd.getDepartamento().getNumero());
            pd.setEstado(false); pd.setFechaFin(LocalDate.now());
            propietarioDeptoRepository.save(pd);
        });
        List<String> deptosInquilino = new ArrayList<>();
        inquilinoDeptoRepository.findActivosByUsuarioId(id).forEach(inq -> {
            deptosInquilino.add(inq.getDepartamento().getNumero());
            inq.setEstado(false); inq.setFechaFin(LocalDate.now());
            inquilinoDeptoRepository.save(inq);
        });

        usuario.setEstado(EstadoUsuario.INACTIVO);
        usuarioRepository.save(usuario);

        Map<String, Object> datosDesvinculacion = new LinkedHashMap<>();
        if (!deptosPropietario.isEmpty()) datosDesvinculacion.put("desvinculadoComoPropietarioDe", deptosPropietario);
        if (!deptosInquilino.isEmpty())  datosDesvinculacion.put("desvinculadoComoInquilinoDe", deptosInquilino);
        auditoriaService.registrar(solicitanteId, "Usuario desactivado", "usuarios", id, null,
                datosDesvinculacion.isEmpty() ? null : datosDesvinculacion);
        return new MensajeResponse("Usuario desactivado correctamente", true);
    }

    public MensajeResponse reactivar(Integer id, Integer solicitanteId) {
        verificarDirectivo(solicitanteId, "reactivar usuarios");
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuarioRepository.save(usuario);
        auditoriaService.registrar(solicitanteId, "Usuario reactivado", "usuarios", id);
        return new MensajeResponse("Usuario reactivado correctamente", true);
    }

    public MensajeResponse asignarRol(Integer usuarioId, Integer rolId, Integer adminId) {
        verificarDirectivo(adminId, "asignar roles");
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (usuario.getEstado() != EstadoUsuario.ACTIVO)
            throw new RuntimeException("No puedes asignar un cargo a un usuario inactivo. Actívalo primero.");
        Rol rol = rolRepository.findById(rolId)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        // Si el usuario ya tenía un cargo directivo distinto y se le va a cambiar
        // (no a quitar, sino a reemplazar por otro rol), verificar que el cargo
        // anterior no quede sin nadie
        List<UsuarioRol> rolesActuales = usuarioRolRepository.findRolesActivosByUsuarioId(usuarioId);
        for (UsuarioRol ur : rolesActuales) {
            boolean esElMismoRol = ur.getRol().getId().equals(rolId);
            if (!esElMismoRol && ur.getRol().getEsDirectivo()) {
                long totalConEseCargo = usuarioRolRepository.countActivosByRolId(ur.getRol().getId());
                if (totalConEseCargo <= 1) {
                    throw new RuntimeException("No puedes reemplazar el cargo de " + ur.getRol().getNombre() +
                            " de este usuario porque quedaría sin nadie en ese puesto. Primero asigna ese cargo a otra persona.");
                }
            }
        }

        usuarioRolRepository.findRolesActivosByUsuarioId(usuarioId).stream()
                .filter(ur -> ur.getRol().getId().equals(rolId))
                .forEach(ur -> { ur.setEstado(false); usuarioRolRepository.save(ur); });

        UsuarioRol nuevoRol = new UsuarioRol();
        nuevoRol.setUsuario(usuario); nuevoRol.setRol(rol);
        nuevoRol.setFechaInicio(LocalDate.now()); nuevoRol.setEstado(true);
        nuevoRol.setAsignadoPor(adminId);
        if (rol.getEsDirectivo()) nuevoRol.setFechaFin(LocalDate.now().plusYears(4));
        usuarioRolRepository.save(nuevoRol);
        eliminarPermisosExtra(usuarioId);
        auditoriaService.registrar(adminId, "Rol asignado: " + rol.getNombre(), "usuarios_roles", usuarioId);
        return new MensajeResponse("Rol asignado y permisos extra reiniciados", true);
    }

    public MensajeResponse revocarRol(Integer usuarioId, Integer rolId, Integer adminId) {
        verificarDirectivo(adminId, "revocar roles");
        Rol rol = rolRepository.findById(rolId)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        // No permitir quitar un cargo directivo si es el único que lo tiene activo
        if (rol.getEsDirectivo()) {
            long totalConEseCargo = usuarioRolRepository.countActivosByRolId(rolId);
            if (totalConEseCargo <= 1) {
                throw new RuntimeException("No puedes quitar el cargo de " + rol.getNombre() +
                        " porque es el único activo. Primero asigna ese cargo a otra persona antes de revocarlo.");
            }
        }

        usuarioRolRepository.findRolesActivosByUsuarioId(usuarioId).stream()
                .filter(ur -> ur.getRol().getId().equals(rolId))
                .forEach(ur -> { ur.setEstado(false); usuarioRolRepository.save(ur); });
        eliminarPermisosExtra(usuarioId);
        auditoriaService.registrar(adminId, "Rol revocado: " + rol.getNombre(), "usuarios_roles", usuarioId);
        return new MensajeResponse("Rol revocado y permisos extra eliminados", true);
    }

    public MensajeResponse asignarPermiso(Integer usuarioId, AsignarPermisoRequest request, Integer adminId) {
        verificarDirectivo(adminId, "asignar permisos");
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Modulo modulo = moduloRepository.findById(request.getModuloId())
                .orElseThrow(() -> new RuntimeException("Módulo no encontrado"));
        Permiso permiso = permisoRepository.findById(request.getPermisoId())
                .orElseThrow(() -> new RuntimeException("Permiso no encontrado"));
        UsuarioPermiso up = new UsuarioPermiso();
        up.setUsuario(usuario); up.setModulo(modulo); up.setPermiso(permiso);
        up.setOtorgadoPor(adminId); up.setFechaOtorgado(LocalDateTime.now()); up.setEstado(true);
        usuarioPermisoRepository.save(up);
        Map<String, Object> datosPermiso = new LinkedHashMap<>();
        datosPermiso.put("modulo", modulo.getNombre());
        datosPermiso.put("permiso", permiso.getNombre());
        auditoriaService.registrar(adminId, "Permiso extra otorgado", "usuarios_permisos", usuarioId, null, datosPermiso);
        return new MensajeResponse("Permiso asignado correctamente", true);
    }

    public MensajeResponse revocarPermiso(Integer asignacionId, Integer adminId) {
        verificarDirectivo(adminId, "revocar permisos");
        UsuarioPermiso up = usuarioPermisoRepository.findById(asignacionId)
                .orElseThrow(() -> new RuntimeException("Permiso no encontrado"));
        up.setEstado(false);
        usuarioPermisoRepository.save(up);
        auditoriaService.registrar(adminId, "Permiso extra revocado", "usuarios_permisos", up.getUsuario().getId());
        return new MensajeResponse("Permiso revocado correctamente", true);
    }

    public MensajeResponse restablecerPermisos(Integer usuarioId, Integer adminId) {
        verificarDirectivo(adminId, "restablecer permisos");
        eliminarPermisosExtra(usuarioId);
        return new MensajeResponse("Permisos restablecidos al default del rol", true);
    }

    public List<Rol>     listarRoles()    { return rolRepository.findAll(); }
    public List<Modulo>  listarModulos()  { return moduloRepository.findAll(); }
    public List<Permiso> listarPermisos() { return permisoRepository.findAll(); }

    private boolean esDirectivo(Integer usuarioId) {
        return usuarioRolRepository.findRolesActivosByUsuarioId(usuarioId)
                .stream().anyMatch(ur -> ur.getRol().getEsDirectivo());
    }

    private void verificarDirectivo(Integer usuarioId, String accion) {
        if (!esDirectivo(usuarioId))
            throw new RuntimeException("No tienes permisos para " + accion);
    }

    private void eliminarPermisosExtra(Integer usuarioId) {
        usuarioPermisoRepository.findByUsuarioIdAndEstadoTrue(usuarioId)
                .forEach(up -> { up.setEstado(false); usuarioPermisoRepository.save(up); });
    }

    private UsuarioResponse toResponse(Usuario u) {
        UsuarioResponse resp = new UsuarioResponse();
        resp.setId(u.getId()); resp.setNombre(u.getNombre());
        resp.setApellido(u.getApellido()); resp.setDni(u.getDni());
        resp.setEmail(u.getEmail()); resp.setTelefono(u.getTelefono());
        resp.setEstado(u.getEstado().name());

        resp.setRoles(usuarioRolRepository.findRolesActivosByUsuarioId(u.getId()).stream()
                .map(ur -> new RolInfo(ur.getId(), ur.getRol().getId(), ur.getRol().getNombre()))
                .collect(Collectors.toList()));

        resp.setPermisosExtra(usuarioPermisoRepository.findByUsuarioIdAndEstadoTrue(u.getId()).stream()
                .map(up -> new PermisoInfo(up.getId(), up.getModulo().getNombre(), up.getPermiso().getNombre()))
                .collect(Collectors.toList()));

        List<DepartamentoInfo> deptos = new ArrayList<>();
        propietarioDeptoRepository.findActivosByUsuarioId(u.getId()).forEach(pd -> {
            DepartamentoInfo di = new DepartamentoInfo();
            di.setDepartamentoId(pd.getDepartamento().getId());
            di.setNumero(pd.getDepartamento().getNumero());
            di.setPiso(pd.getDepartamento().getPiso());
            di.setTipo("PROPIETARIO");
            deptos.add(di);
        });
        inquilinoDeptoRepository.findActivosByUsuarioId(u.getId()).forEach(id -> {
            DepartamentoInfo di = new DepartamentoInfo();
            di.setDepartamentoId(id.getDepartamento().getId());
            di.setNumero(id.getDepartamento().getNumero());
            di.setPiso(id.getDepartamento().getPiso());
            di.setTipo("INQUILINO");
            deptos.add(di);
        });
        resp.setDepartamentos(deptos);

        return resp;
    }
}

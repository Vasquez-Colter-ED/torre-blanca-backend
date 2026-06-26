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
import java.util.List;
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

    public List<UsuarioResponse> listarTodos() {
        return usuarioRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    public UsuarioResponse obtenerPorId(Integer id) {
        return toResponse(usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado")));
    }

    public UsuarioResponse crear(CrearUsuarioRequest request, Integer adminId) {
        verificarDirectivo(adminId, "crear usuarios");

        // Validaciones de formato — sin caracteres especiales
        ValidacionUtil.validarNombre(request.getNombre(), "El nombre");
        ValidacionUtil.validarNombre(request.getApellido(), "El apellido");
        ValidacionUtil.validarDni(request.getDni());
        ValidacionUtil.validarEmail(request.getEmail());
        ValidacionUtil.validarTelefono(request.getTelefono());

        if (usuarioRepository.existsByEmail(request.getEmail()))
            throw new RuntimeException("El email ya está registrado");
        if (request.getDni() != null && usuarioRepository.existsByDni(request.getDni()))
            throw new RuntimeException("El DNI ya está registrado");

        Usuario nuevo = new Usuario();
        nuevo.setNombre(request.getNombre());
        nuevo.setApellido(request.getApellido());
        nuevo.setDni(request.getDni());
        nuevo.setEmail(request.getEmail());
        nuevo.setTelefono(request.getTelefono());
        nuevo.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        nuevo.setEstado(EstadoUsuario.ACTIVO);
        Usuario guardado = usuarioRepository.save(nuevo);

        if (request.getRolId() != null) asignarRol(guardado.getId(), request.getRolId(), adminId);

        if (request.getDepartamentoId() != null) {
            Departamento depto = departamentoRepository.findById(request.getDepartamentoId()).orElse(null);
            if (depto != null) {
                propietarioDeptoRepository.findActivoByDepartamentoId(depto.getId())
                        .ifPresent(pd -> { pd.setEstado(false); propietarioDeptoRepository.save(pd); });
                PropietarioDepartamento pd = new PropietarioDepartamento();
                pd.setUsuario(guardado); pd.setDepartamento(depto);
                pd.setFechaInicio(LocalDate.now()); pd.setEstado(true);
                propietarioDeptoRepository.save(pd);
            }
        }

        return toResponse(guardado);
    }

    public UsuarioResponse editar(Integer id, EditarUsuarioRequest request, Integer solicitanteId) {
        boolean esAdmin = esDirectivo(solicitanteId);
        if (!esAdmin && !id.equals(solicitanteId))
            throw new RuntimeException("Solo puedes editar tu propio perfil");

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

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
        if (request.getRolId() != null && esAdmin) {
            asignarRol(id, request.getRolId(), solicitanteId);
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

        return toResponse(usuarioRepository.save(usuario));
    }

    public MensajeResponse desactivar(Integer id, Integer solicitanteId) {
        verificarDirectivo(solicitanteId, "desactivar usuarios");
        if (id.equals(solicitanteId)) throw new RuntimeException("No puedes desactivar tu propia cuenta");
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (esDirectivo(id)) throw new RuntimeException("No puedes desactivar a un directivo");
        usuario.setEstado(EstadoUsuario.INACTIVO);
        usuarioRepository.save(usuario);
        return new MensajeResponse("Usuario desactivado correctamente", true);
    }

    public MensajeResponse reactivar(Integer id, Integer solicitanteId) {
        verificarDirectivo(solicitanteId, "reactivar usuarios");
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuarioRepository.save(usuario);
        return new MensajeResponse("Usuario reactivado correctamente", true);
    }

    public MensajeResponse asignarRol(Integer usuarioId, Integer rolId, Integer adminId) {
        verificarDirectivo(adminId, "asignar roles");
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Rol rol = rolRepository.findById(rolId)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

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
        return new MensajeResponse("Rol asignado y permisos extra reiniciados", true);
    }

    public MensajeResponse revocarRol(Integer usuarioId, Integer rolId, Integer adminId) {
        verificarDirectivo(adminId, "revocar roles");
        usuarioRolRepository.findRolesActivosByUsuarioId(usuarioId).stream()
                .filter(ur -> ur.getRol().getId().equals(rolId))
                .forEach(ur -> { ur.setEstado(false); usuarioRolRepository.save(ur); });
        eliminarPermisosExtra(usuarioId);
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
        return new MensajeResponse("Permiso asignado correctamente", true);
    }

    public MensajeResponse revocarPermiso(Integer asignacionId, Integer adminId) {
        verificarDirectivo(adminId, "revocar permisos");
        UsuarioPermiso up = usuarioPermisoRepository.findById(asignacionId)
                .orElseThrow(() -> new RuntimeException("Permiso no encontrado"));
        up.setEstado(false);
        usuarioPermisoRepository.save(up);
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

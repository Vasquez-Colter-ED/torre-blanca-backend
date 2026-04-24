package pe.torreblanca.backend.service;

import pe.torreblanca.backend.dto.*;
import pe.torreblanca.backend.entity.*;
import pe.torreblanca.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    // ------------------------------------------------------------------
    // Listar todos los usuarios
    // ------------------------------------------------------------------
    public List<UsuarioResponse> listarTodos() {
        return usuarioRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // Obtener usuario por ID
    // ------------------------------------------------------------------
    public UsuarioResponse obtenerPorId(Integer id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return toResponse(usuario);
    }

    // ------------------------------------------------------------------
    // Crear usuario nuevo
    // ------------------------------------------------------------------
    public UsuarioResponse crear(CrearUsuarioRequest request, Integer adminId) {
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("El email ya está registrado");
        }
        if (request.getDni() != null && usuarioRepository.existsByDni(request.getDni())) {
            throw new RuntimeException("El DNI ya está registrado");
        }

        Usuario nuevo = new Usuario();
        nuevo.setNombre(request.getNombre());
        nuevo.setApellido(request.getApellido());
        nuevo.setDni(request.getDni());
        nuevo.setEmail(request.getEmail());
        nuevo.setTelefono(request.getTelefono());
        nuevo.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        nuevo.setEstado(EstadoUsuario.ACTIVO);

        Usuario guardado = usuarioRepository.save(nuevo);

        if (request.getRolId() != null) {
            asignarRol(guardado.getId(), request.getRolId(), adminId);
        }

        return toResponse(guardado);
    }

    // ------------------------------------------------------------------
    // Editar usuario
    // REGLA: admin puede editar cualquier usuario, incluso otros admins
    // REGLA: un admin siempre puede editarse a sí mismo
    // ------------------------------------------------------------------
    public UsuarioResponse editar(Integer id, EditarUsuarioRequest request, Integer adminId) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (request.getNombre()   != null) usuario.setNombre(request.getNombre());
        if (request.getApellido() != null) usuario.setApellido(request.getApellido());
        if (request.getTelefono() != null) usuario.setTelefono(request.getTelefono());
        if (request.getDni()      != null) usuario.setDni(request.getDni());

        if (request.getEmail() != null && !request.getEmail().equals(usuario.getEmail())) {
            if (usuarioRepository.existsByEmail(request.getEmail())) {
                throw new RuntimeException("El email ya está en uso por otro usuario");
            }
            usuario.setEmail(request.getEmail());
        }

        return toResponse(usuarioRepository.save(usuario));
    }

    // ------------------------------------------------------------------
    // Desactivar usuario (SOFT DELETE - solo cambia estado a INACTIVO)
    // REGLA: admin NO puede desactivar a otro directivo
    // REGLA: admin NO puede desactivarse a sí mismo
    // ------------------------------------------------------------------
    public MensajeResponse desactivar(Integer id, Integer adminId) {
        if (id.equals(adminId)) {
            throw new RuntimeException("No puedes desactivar tu propia cuenta");
        }

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Verificar si el objetivo es directivo
        if (esDirectivo(id)) {
            throw new RuntimeException("No puedes desactivar a un directivo");
        }

        usuario.setEstado(EstadoUsuario.INACTIVO);
        usuarioRepository.save(usuario);
        return new MensajeResponse("Usuario desactivado correctamente", true);
    }

    // ------------------------------------------------------------------
    // Reactivar usuario
    // ------------------------------------------------------------------
    public MensajeResponse reactivar(Integer id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuarioRepository.save(usuario);
        return new MensajeResponse("Usuario reactivado correctamente", true);
    }

    // ------------------------------------------------------------------
    // Asignar rol a usuario
    // Si es directivo, la vigencia es 4 años automáticamente
    // ------------------------------------------------------------------
    public MensajeResponse asignarRol(Integer usuarioId, Integer rolId, Integer adminId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Rol rol = rolRepository.findById(rolId)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        // Desactivar si ya tenía ese mismo rol asignado antes
        usuarioRolRepository.findRolesActivosByUsuarioId(usuarioId).stream()
                .filter(ur -> ur.getRol().getId().equals(rolId))
                .forEach(ur -> {
                    ur.setEstado(false);
                    usuarioRolRepository.save(ur);
                });

        UsuarioRol nuevoRol = new UsuarioRol();
        nuevoRol.setUsuario(usuario);
        nuevoRol.setRol(rol);
        nuevoRol.setFechaInicio(LocalDate.now());
        nuevoRol.setEstado(true);
        nuevoRol.setAsignadoPor(adminId);

        // Directivos tienen vigencia de 4 años
        if (rol.getEsDirectivo()) {
            nuevoRol.setFechaFin(LocalDate.now().plusYears(4));
        }

        usuarioRolRepository.save(nuevoRol);
        return new MensajeResponse("Rol asignado correctamente", true);
    }

    // ------------------------------------------------------------------
    // Revocar rol a usuario
    // ------------------------------------------------------------------
    public MensajeResponse revocarRol(Integer usuarioId, Integer rolId) {
        usuarioRolRepository.findRolesActivosByUsuarioId(usuarioId).stream()
                .filter(ur -> ur.getRol().getId().equals(rolId))
                .forEach(ur -> {
                    ur.setEstado(false);
                    usuarioRolRepository.save(ur);
                });
        return new MensajeResponse("Rol revocado correctamente", true);
    }

    // ------------------------------------------------------------------
    // Asignar permiso personalizado a usuario
    // ------------------------------------------------------------------
    public MensajeResponse asignarPermiso(Integer usuarioId, AsignarPermisoRequest request, Integer adminId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Modulo modulo = moduloRepository.findById(request.getModuloId())
                .orElseThrow(() -> new RuntimeException("Módulo no encontrado"));
        Permiso permiso = permisoRepository.findById(request.getPermisoId())
                .orElseThrow(() -> new RuntimeException("Permiso no encontrado"));

        UsuarioPermiso up = new UsuarioPermiso();
        up.setUsuario(usuario);
        up.setModulo(modulo);
        up.setPermiso(permiso);
        up.setOtorgadoPor(adminId);
        up.setFechaOtorgado(LocalDateTime.now());
        up.setEstado(true);

        usuarioPermisoRepository.save(up);
        return new MensajeResponse("Permiso asignado correctamente", true);
    }

    // ------------------------------------------------------------------
    // Listar catálogos
    // ------------------------------------------------------------------
    public List<Rol>     listarRoles()    { return rolRepository.findAll(); }
    public List<Modulo>  listarModulos()  { return moduloRepository.findAll(); }
    public List<Permiso> listarPermisos() { return permisoRepository.findAll(); }

    // ------------------------------------------------------------------
    // Helpers privados
    // ------------------------------------------------------------------
    private boolean esDirectivo(Integer usuarioId) {
        return usuarioRolRepository.findRolesActivosByUsuarioId(usuarioId)
                .stream()
                .anyMatch(ur -> ur.getRol().getEsDirectivo());
    }

    private UsuarioResponse toResponse(Usuario u) {
        UsuarioResponse resp = new UsuarioResponse();
        resp.setId(u.getId());
        resp.setNombre(u.getNombre());
        resp.setApellido(u.getApellido());
        resp.setDni(u.getDni());
        resp.setEmail(u.getEmail());
        resp.setTelefono(u.getTelefono());
        resp.setEstado(u.getEstado().name());

        List<UsuarioRol> roles = usuarioRolRepository.findRolesActivosByUsuarioId(u.getId());
        resp.setRoles(roles.stream()
                .map(ur -> ur.getRol().getNombre())
                .collect(Collectors.toList()));

        return resp;
    }
}

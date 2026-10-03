package com.tiendas.usuarios;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tiendas.comun.ConflictoException;
import com.tiendas.comun.NoEncontradoException;
import com.tiendas.comun.ReglaNegocioException;

@Service
@Transactional(readOnly = true)
public class UsuarioService implements UserDetailsService {

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;

	public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public UserDetails loadUserByUsername(String username) {
		Usuario usuario = usuarioRepository.findByUsername(normalizar(username))
				.orElseThrow(() -> new UsernameNotFoundException("Usuario inexistente"));
		return User.withUsername(usuario.getUsername())
				.password(usuario.getPasswordHash())
				.roles(usuario.getRol().name())
				.disabled(!usuario.isActivo())
				.build();
	}

	public List<Usuario> listar() {
		return usuarioRepository.findAll(Sort.by("username"));
	}

	public Usuario obtener(Long id) {
		return usuarioRepository.findById(id).orElseThrow(() -> NoEncontradoException.de("Usuario", id));
	}

	public Usuario obtenerPorUsername(String username) {
		return usuarioRepository.findByUsername(normalizar(username))
				.orElseThrow(() -> new NoEncontradoException("Usuario no encontrado"));
	}

	@Transactional
	public Usuario crear(Usuario datos) {
		if (datos.getClave() == null) {
			throw new ReglaNegocioException("La clave es obligatoria");
		}
		Usuario usuario = new Usuario();
		usuario.setUsername(normalizar(datos.getUsername()));
		usuario.setNombre(datos.getNombre());
		usuario.setRol(datos.getRol() == null ? Rol.ADMIN : datos.getRol());
		usuario.setActivo(datos.isActivo());
		usuario.setPasswordHash(passwordEncoder.encode(datos.getClave()));
		return usuarioRepository.save(usuario);
	}

	@Transactional
	public Usuario actualizar(Long id, Usuario datos, String usernameActual) {
		Usuario usuario = obtener(id);
		if (!datos.isActivo() && usuario.isActivo()) {
			validarQuePuedeQuitarse(usuario, usernameActual);
		}
		usuario.setUsername(normalizar(datos.getUsername()));
		usuario.setNombre(datos.getNombre());
		usuario.setActivo(datos.isActivo());
		if (datos.getClave() != null) {
			usuario.setPasswordHash(passwordEncoder.encode(datos.getClave()));
		}
		return usuario;
	}

	@Transactional
	public void eliminar(Long id, String usernameActual) {
		Usuario usuario = obtener(id);
		validarQuePuedeQuitarse(usuario, usernameActual);
		usuarioRepository.delete(usuario);
	}

	/** Crea el primer administrador. No hace nada si ya existe algún usuario. */
	@Transactional
	public boolean crearAdminInicialSiNoHayUsuarios(String username, String clave) {
		if (usuarioRepository.count() > 0) {
			return false;
		}
		Usuario admin = new Usuario();
		admin.setUsername(username);
		admin.setNombre("Administrador");
		admin.setClave(clave);
		crear(admin);
		return true;
	}

	// Evita que la tienda quede sin ningún administrador activo
	private void validarQuePuedeQuitarse(Usuario usuario, String usernameActual) {
		if (usuario.getUsername().equals(normalizar(usernameActual))) {
			throw new ConflictoException("No podés desactivar ni eliminar tu propio usuario");
		}
		if (usuario.getRol() == Rol.ADMIN && usuario.isActivo()
				&& usuarioRepository.countByRolAndActivoTrue(Rol.ADMIN) <= 1) {
			throw new ConflictoException("Debe quedar al menos un administrador activo");
		}
	}

	private static String normalizar(String username) {
		return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
	}
}

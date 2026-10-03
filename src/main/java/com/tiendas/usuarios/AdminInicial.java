package com.tiendas.usuarios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.tiendas.config.TiendaProperties;

/**
 * Al desplegar una tienda nueva la base está vacía y no hay forma de entrar al panel:
 * si se configuraron TIENDA_ADMIN_INICIAL_USUARIO y TIENDA_ADMIN_INICIAL_CLAVE, se crea ese administrador.
 * Una vez creado, conviene borrar esas variables de entorno.
 */
@Component
class AdminInicial implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminInicial.class);

	private final TiendaProperties tienda;
	private final UsuarioService usuarioService;

	AdminInicial(TiendaProperties tienda, UsuarioService usuarioService) {
		this.tienda = tienda;
		this.usuarioService = usuarioService;
	}

	@Override
	public void run(ApplicationArguments args) {
		TiendaProperties.AdminInicial admin = tienda.adminInicial();
		if (!StringUtils.hasText(admin.usuario()) || !StringUtils.hasText(admin.clave())) {
			return;
		}
		if (admin.clave().length() < 10) {
			throw new IllegalStateException("TIENDA_ADMIN_INICIAL_CLAVE debe tener al menos 10 caracteres");
		}
		if (usuarioService.crearAdminInicialSiNoHayUsuarios(admin.usuario(), admin.clave())) {
			log.info("Se creó el administrador inicial '{}'", admin.usuario());
		}
	}
}

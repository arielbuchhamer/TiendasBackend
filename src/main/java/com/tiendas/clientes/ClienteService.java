package com.tiendas.clientes;

import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tiendas.comun.ConflictoException;
import com.tiendas.comun.Consultas;
import com.tiendas.comun.NoEncontradoException;
import com.tiendas.comun.ReglaNegocioException;
import com.tiendas.comun.Textos;
import com.tiendas.pedidos.PedidoRepository;

/** ABM de clientes desde el panel. Lo que hace el propio cliente con su cuenta está en {@link CuentaClienteService}. */
@Service
@ClientesHabilitado
@Transactional(readOnly = true)
class ClienteService {

	private static final Set<String> ORDENES_PERMITIDOS = Set.of("creadoEn", "apellido", "email", "ultimoAcceso");
	private static final Sort ORDEN_POR_DEFECTO = Sort.by(Sort.Direction.DESC, "id");

	private final ClienteRepository clienteRepository;
	private final PedidoRepository pedidoRepository;

	ClienteService(ClienteRepository clienteRepository, PedidoRepository pedidoRepository) {
		this.clienteRepository = clienteRepository;
		this.pedidoRepository = pedidoRepository;
	}

	Page<Cliente> listar(FiltroClientes filtro, Pageable pagina) {
		Pageable paginaSegura = PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(),
				Consultas.ordenPermitido(pagina.getSort(), ORDENES_PERMITIDOS, ORDEN_POR_DEFECTO));
		return clienteRepository.findAll(filtro.comoSpecification(), paginaSegura);
	}

	Cliente obtener(Long id) {
		return clienteRepository.findById(id).orElseThrow(() -> NoEncontradoException.de("Cliente", id));
	}

	/** El cliente queda PENDIENTE y sin contraseña hasta que use la invitación. */
	@Transactional
	Cliente crear(Cliente datos) {
		String email = Textos.minusculas(datos.getEmail());
		if (clienteRepository.existsByEmail(email)) {
			throw new ConflictoException("Ya existe un cliente con el email " + email);
		}
		Cliente cliente = new Cliente();
		copiarDatos(datos, cliente, email);
		return clienteRepository.save(cliente);
	}

	/** Solo los datos de la cuenta: el estado y la contraseña tienen su propio flujo. */
	@Transactional
	Cliente actualizar(Long id, Cliente datos) {
		Cliente cliente = obtener(id);
		String email = Textos.minusculas(datos.getEmail());
		if (clienteRepository.existsByEmailAndIdNot(email, id)) {
			throw new ConflictoException("Ya existe otro cliente con el email " + email);
		}
		copiarDatos(datos, cliente, email);
		return cliente;
	}

	/**
	 * Bloquea o reactiva una cuenta. Reactivar a quien todavía no eligió su contraseña la deja PENDIENTE (puede usar
	 * la invitación); no se puede pasar a ACTIVO una cuenta sin contraseña.
	 */
	@Transactional
	Cliente cambiarEstado(Long id, EstadoCliente estado) {
		Cliente cliente = obtener(id);
		cliente.setEstado(switch (estado) {
			case BLOQUEADO -> EstadoCliente.BLOQUEADO;
			case ACTIVO -> cliente.isTieneClave() ? EstadoCliente.ACTIVO : EstadoCliente.PENDIENTE;
			case PENDIENTE -> throw new ReglaNegocioException("El estado puede ser ACTIVO o BLOQUEADO");
		});
		return cliente;
	}

	/** Un cliente con pedidos no se borra (los pedidos lo referencian): se bloquea. */
	@Transactional
	void eliminar(Long id) {
		Cliente cliente = obtener(id);
		if (pedidoRepository.existsByClienteId(id)) {
			throw new ConflictoException("El cliente tiene pedidos: bloquealo en lugar de eliminarlo");
		}
		clienteRepository.delete(cliente);
	}

	// Copia solo los campos que edita el administrador (nunca estado, contraseña ni fechas)
	private static void copiarDatos(Cliente datos, Cliente cliente, String email) {
		cliente.setEmail(email);
		cliente.setNombre(datos.getNombre().trim());
		cliente.setApellido(datos.getApellido().trim());
		cliente.setDocumento(datos.getDocumento().trim());
		cliente.setRazonSocial(Textos.opcional(datos.getRazonSocial()));
		cliente.setTelefono(Textos.opcional(datos.getTelefono()));
		cliente.setProvincia(Textos.opcional(datos.getProvincia()));
		cliente.setLocalidad(Textos.opcional(datos.getLocalidad()));
		cliente.setNotas(Textos.opcional(datos.getNotas()));
	}
}

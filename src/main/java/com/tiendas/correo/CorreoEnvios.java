package com.tiendas.correo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.tiendas.comun.ConflictoException;
import com.tiendas.comun.NoEncontradoException;
import com.tiendas.comun.ReglaNegocioException;
import com.tiendas.envios.EnvioService;
import com.tiendas.pedidos.DireccionEnvio;
import com.tiendas.pedidos.EstadoPedido;
import com.tiendas.pedidos.Pedido;
import com.tiendas.pedidos.PedidoItem;
import com.tiendas.pedidos.PedidoRepository;
import com.tiendas.pedidos.PedidoService;
import com.tiendas.pedidos.TipoEntrega;

/**
 * Lo que el vendedor hace con Correo Argentino desde una venta: darla de alta en MiCorreo (queda en "Mis envíos"
 * para pagarla e imprimir el rótulo, que la API no genera) y consultar el seguimiento del paquete.
 * <p>
 * Las llamadas al Correo van fuera de transacción; solo se abre una para guardar el resultado.
 */
@Service
@ConditionalOnBooleanProperty("correo-argentino.habilitada")
public class CorreoEnvios {

	private final CorreoArgentino correo;
	private final PedidoService pedidoService;
	private final PedidoRepository pedidoRepository;
	private final EnvioService envioService;
	private final TransactionTemplate transaccion;

	CorreoEnvios(CorreoArgentino correo, PedidoService pedidoService, PedidoRepository pedidoRepository,
			EnvioService envioService, TransactionTemplate transaccion) {
		this.correo = correo;
		this.pedidoService = pedidoService;
		this.pedidoRepository = pedidoRepository;
		this.envioService = envioService;
		this.transaccion = transaccion;
	}

	public Pedido darDeAlta(Long pedidoId) {
		Pedido pedido = pedidoService.obtener(pedidoId);
		if (pedido.getEstado() != EstadoPedido.APROBADO) {
			throw new ReglaNegocioException("Solo se pueden enviar ventas pagadas");
		}
		if (pedido.getEntrega() == TipoEntrega.RETIRO) {
			throw new ReglaNegocioException("La venta es para retirar en el local: no hay nada que enviar");
		}
		if (pedido.getCorreoImportadoEn() != null) {
			throw new ConflictoException("Esta venta ya se cargó en MiCorreo");
		}

		Map<Long, BigDecimal> cantidades = pedido.getItems().stream()
				.collect(Collectors.toMap(PedidoItem::getVarianteId, PedidoItem::getCantidad, BigDecimal::add));
		CorreoArgentino.Paquete paquete = consolidar(envioService.paquetes(cantidades).orElseThrow(() ->
				new ReglaNegocioException("Faltan el peso o las medidas de algún producto de la venta: cargalos en el "
						+ "artículo o en su categoría")));

		boolean sucursal = pedido.getEntrega() == TipoEntrega.SUCURSAL;
		String servicio = pedido.getEnvioOpcion() != null && pedido.getEnvioOpcion().endsWith("-EP") ? "EP" : "CP";
		DireccionEnvio d = pedido.getDireccion();
		String provincia = null;
		if (!sucursal) {
			provincia = Provincias.codigo(d.getProvincia());
			if (provincia == null) {
				throw new ReglaNegocioException("No se reconoce la provincia \"" + d.getProvincia()
						+ "\" de la dirección: corregila antes de cargar el envío");
			}
		}

		correo.darDeAlta(new CorreoArgentino.AltaEnvio(
				pedido.getCodigo().toString(),
				pedido.getCodigo().toString().substring(0, 8).toUpperCase(Locale.ROOT),
				pedido.getCliente().getNombre() + " " + pedido.getCliente().getApellido(),
				pedido.getCliente().getEmail(),
				pedido.getCliente().getTelefono(),
				sucursal ? "S" : "D",
				servicio,
				sucursal ? pedido.getEnvioSucursal() : null,
				sucursal ? null : d.getCalle(),
				sucursal ? null : d.getNumero(),
				sucursal ? null : d.getPiso(),
				sucursal ? null : d.getDepartamento(),
				sucursal ? null : d.getCiudad(),
				provincia,
				sucursal ? null : d.getCodigoPostal(),
				paquete,
				pedido.getSubtotal()));

		transaccion.executeWithoutResult(estado -> buscar(pedidoId).setCorreoImportadoEn(Instant.now()));
		return pedidoService.obtener(pedidoId);
	}

	public Pedido guardarSeguimiento(Long pedidoId, String codigo) {
		transaccion.executeWithoutResult(estado ->
				buscar(pedidoId).setEnvioSeguimiento(StringUtils.hasText(codigo) ? codigo.trim().toUpperCase(Locale.ROOT) : null));
		return pedidoService.obtener(pedidoId);
	}

	public List<CorreoArgentino.EventoSeguimiento> seguimiento(Long pedidoId) {
		Pedido pedido = pedidoService.obtener(pedidoId);
		if (!StringUtils.hasText(pedido.getEnvioSeguimiento())) {
			throw new ReglaNegocioException("Cargá primero el código de seguimiento del envío");
		}
		return correo.seguimiento(pedido.getEnvioSeguimiento());
	}

	private Pedido buscar(Long id) {
		return pedidoRepository.findById(id).orElseThrow(() -> NoEncontradoException.de("Pedido", id));
	}

	/**
	 * Todo en una sola caja: suma los pesos, toma el largo y el ancho mayores y apila las alturas. Es una
	 * estimación razonable para pocos productos; el Correo controla el paquete real al admitirlo.
	 */
	static CorreoArgentino.Paquete consolidar(List<CorreoArgentino.Paquete> paquetes) {
		int peso = paquetes.stream().mapToInt(p -> p.cantidad() * p.pesoGramos()).sum();
		int largo = paquetes.stream().mapToInt(CorreoArgentino.Paquete::largoCm).max().orElse(1);
		int ancho = paquetes.stream().mapToInt(CorreoArgentino.Paquete::anchoCm).max().orElse(1);
		int alto = paquetes.stream().mapToInt(p -> p.cantidad() * p.altoCm()).sum();
		return new CorreoArgentino.Paquete(1, peso, largo, ancho, alto);
	}
}

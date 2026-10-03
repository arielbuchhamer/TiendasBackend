package com.tiendas.pedidos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.tiendas.catalogo.Variante;
import com.tiendas.catalogo.VarianteRepository;
import com.tiendas.comun.ReglaNegocioException;
import com.tiendas.comun.ServicioExternoException;
import com.tiendas.config.TiendaProperties;

/**
 * Inicia una compra: valida el carrito contra la base, calcula precios y totales en el servidor
 * (nunca se confía en importes enviados por el cliente), guarda el pedido y crea el checkout externo.
 * <p>
 * El stock no se reserva acá: se descuenta recién cuando el pago se aprueba (ver {@link PedidoService}).
 * La llamada a Mercado Pago se hace fuera de la transacción para no tener una conexión de base
 * tomada mientras se espera una respuesta HTTP externa.
 */
@Service
public class CheckoutService {

	private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

	private final VarianteRepository varianteRepository;
	private final PedidoRepository pedidoRepository;
	private final PasarelaPago pasarelaPago;
	private final TransactionTemplate transaccion;
	private final TiendaProperties tienda;

	public CheckoutService(VarianteRepository varianteRepository, PedidoRepository pedidoRepository,
			PasarelaPago pasarelaPago, TransactionTemplate transaccion, TiendaProperties tienda) {
		this.varianteRepository = varianteRepository;
		this.pedidoRepository = pedidoRepository;
		this.pasarelaPago = pasarelaPago;
		this.transaccion = transaccion;
		this.tienda = tienda;
	}

	public Pedido iniciar(Pedido solicitud) {
		Pedido pedido = transaccion.execute(estado -> crearPedido(solicitud));
		try {
			PasarelaPago.CheckoutExterno checkout = pasarelaPago.crearCheckout(pedido);
			transaccion.executeWithoutResult(
					estado -> pedidoRepository.registrarCheckout(pedido.getId(), checkout.referencia(), checkout.url()));
			pedido.setCheckoutUrl(checkout.url());
			return pedido;
		} catch (RuntimeException e) {
			log.error("No se pudo crear el checkout del pedido {}", pedido.getCodigo(), e);
			transaccion.executeWithoutResult(estado -> pedidoRepository.cambiarEstado(pedido.getId(), EstadoPedido.CANCELADO));
			throw new ServicioExternoException("No se pudo iniciar el pago. Intentá nuevamente en unos minutos.", e);
		}
	}

	private Pedido crearPedido(Pedido solicitud) {
		Map<Long, BigDecimal> cantidades = agruparCantidades(solicitud.getItems());
		Map<Long, Variante> variantes = varianteRepository.buscarConProducto(cantidades.keySet()).stream()
				.collect(Collectors.toMap(Variante::getId, Function.identity()));

		Pedido pedido = new Pedido();
		pedido.setCodigo(UUID.randomUUID());
		pedido.setEstado(EstadoPedido.PENDIENTE);
		pedido.setCliente(solicitud.getCliente());
		pedido.setMoneda(tienda.moneda());
		aplicarEntrega(solicitud, pedido);

		BigDecimal subtotal = BigDecimal.ZERO;
		for (Map.Entry<Long, BigDecimal> linea : cantidades.entrySet()) {
			PedidoItem item = crearItem(variantes.get(linea.getKey()), linea.getKey(), linea.getValue());
			pedido.agregarItem(item);
			subtotal = subtotal.add(item.getSubtotal());
		}
		pedido.setSubtotal(subtotal);
		pedido.setTotal(subtotal.add(pedido.getCostoEnvio()));
		return pedidoRepository.save(pedido);
	}

	private void aplicarEntrega(Pedido solicitud, Pedido pedido) {
		TiendaProperties.Envio envio = tienda.envio();
		pedido.setEntrega(solicitud.getEntrega());
		if (solicitud.getEntrega() == TipoEntrega.DOMICILIO) {
			if (!envio.domicilioHabilitado()) {
				throw new ReglaNegocioException("La tienda no realiza envíos a domicilio");
			}
			if (solicitud.getDireccion() == null) {
				throw new ReglaNegocioException("La dirección es obligatoria para envíos a domicilio");
			}
			pedido.setDireccion(solicitud.getDireccion());
			pedido.setCostoEnvio(envio.costoFijo());
		} else {
			if (!envio.retiroHabilitado()) {
				throw new ReglaNegocioException("La tienda no permite retirar los pedidos");
			}
			pedido.setCostoEnvio(BigDecimal.ZERO);
		}
	}

	private static PedidoItem crearItem(Variante variante, Long varianteId, BigDecimal cantidad) {
		if (variante == null || !variante.isActivo() || !variante.getProducto().isActivo()) {
			throw new ReglaNegocioException("El producto " + varianteId + " no está disponible");
		}
		String nombre = variante.getProducto().getNombre();
		if (!variante.getProducto().getUnidadVenta().admiteDecimales() && cantidad.stripTrailingZeros().scale() > 0) {
			throw new ReglaNegocioException("La cantidad de " + nombre + " debe ser un número entero");
		}
		if (variante.getStock().compareTo(cantidad) < 0) {
			throw new ReglaNegocioException("No hay stock suficiente de " + nombre);
		}

		BigDecimal precio = variante.precioEfectivo();
		PedidoItem item = new PedidoItem();
		item.setVarianteId(variante.getId());
		item.setCantidad(cantidad);
		item.setProductoNombre(nombre);
		item.setVarianteDescripcion(variante.descripcion().isEmpty() ? null : variante.descripcion());
		item.setSku(variante.getSku());
		item.setUnidadVenta(variante.getProducto().getUnidadVenta());
		item.setImagenId(variante.getImagenId() != null ? variante.getImagenId()
				: variante.getProducto().getImagenes().stream().findFirst().orElse(null));
		item.setPrecioUnitario(precio);
		item.setSubtotal(precio.multiply(cantidad).setScale(2, RoundingMode.HALF_UP));
		return item;
	}

	// Si el carrito trae la misma variante en dos líneas, se suman (así la validación de stock es correcta)
	private static Map<Long, BigDecimal> agruparCantidades(List<PedidoItem> items) {
		Map<Long, BigDecimal> cantidades = new LinkedHashMap<>();
		items.forEach(item -> cantidades.merge(item.getVarianteId(), item.getCantidad(), BigDecimal::add));
		return cantidades;
	}
}

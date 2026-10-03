package com.tiendas.desarrollo;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.tiendas.config.TiendaProperties;
import com.tiendas.mercadopago.MercadoPagoProperties;
import com.tiendas.pedidos.PasarelaPago;
import com.tiendas.pedidos.Pedido;
import com.tiendas.pedidos.PedidoService;

/**
 * Pasarela de pago falsa para desarrollar el frontend sin credenciales de Mercado Pago ni webhook público:
 * aprueba el pedido al instante (descontando stock, como un pago real) y redirige a la página de éxito.
 * <p>
 * Doble candado: requiere el perfil {@code local} Y {@code mercadopago.simulado=true}. Si alguien activa la
 * propiedad en otro perfil, la pasarela real tampoco se crea y la aplicación no arranca: nunca se aprueban
 * pedidos gratis en producción.
 */
@Component
@Profile("local")
@ConditionalOnBooleanProperty("mercadopago.simulado")
class PasarelaSimulada implements PasarelaPago {

	private static final Logger log = LoggerFactory.getLogger(PasarelaSimulada.class);
	static final String PROVEEDOR = "SIMULADO";

	private final PedidoService pedidoService;
	private final TiendaProperties tienda;
	private final MercadoPagoProperties mercadoPago;

	PasarelaSimulada(PedidoService pedidoService, TiendaProperties tienda, MercadoPagoProperties mercadoPago) {
		this.pedidoService = pedidoService;
		this.tienda = tienda;
		this.mercadoPago = mercadoPago;
		log.warn("PAGOS SIMULADOS: los pedidos se aprueban sin cobrar (solo desarrollo local)");
	}

	@Override
	public CheckoutExterno crearCheckout(Pedido pedido) {
		pedidoService.registrarPago(new PagoInformado(PROVEEDOR, "SIM-" + UUID.randomUUID(),
				pedido.getCodigo().toString(), ResultadoPago.APROBADO, "approved", "simulado",
				pedido.getTotal(), pedido.getMoneda(), "simulado", Instant.now()));
		// Mismos parámetros con los que vuelve Mercado Pago, para que el front no distinga
		String url = tienda.frontendUrl() + mercadoPago.rutaExito()
				+ "?external_reference=" + pedido.getCodigo() + "&status=approved&collection_status=approved";
		return new CheckoutExterno("SIMULADO-" + pedido.getCodigo(), url);
	}
}

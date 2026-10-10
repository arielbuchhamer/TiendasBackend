package com.tiendas.pedidos;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tiendas.config.IpCliente;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1")
public class PedidoController {

	/** Carrito y código para calcular el descuento antes de pagar. El email es opcional. */
	public record SolicitudCotizacion(@NotBlank @Size(max = 60) String cuponCodigo, @Email @Size(max = 160) String email,
			@NotNull TipoEntrega entrega, @Valid @NotEmpty @Size(max = 50) List<PedidoItem> items) {
	}

	private final CheckoutService checkoutService;
	private final PedidoService pedidoService;
	private final IpCliente ipCliente;

	public PedidoController(CheckoutService checkoutService, PedidoService pedidoService, IpCliente ipCliente) {
		this.checkoutService = checkoutService;
		this.pedidoService = pedidoService;
		this.ipCliente = ipCliente;
	}

	/**
	 * Crea el pedido y devuelve, entre otros datos, {@code checkoutUrl}: la URL de Mercado Pago a la que
	 * el frontend debe redirigir al comprador.
	 */
	@PostMapping("/checkout")
	@ResponseStatus(HttpStatus.CREATED)
	public Pedido checkout(@Valid @RequestBody Pedido pedido, HttpServletRequest request) {
		return checkoutService.iniciar(pedido, ipCliente.de(request));
	}

	/**
	 * Valida un cupón contra el carrito y devuelve los importes con el descuento aplicado (422 con el motivo
	 * si no se puede usar). No crea nada: el código se vuelve a validar al confirmar el checkout.
	 */
	@PostMapping("/checkout/cupon")
	public CheckoutService.Cotizacion cotizarCupon(@Valid @RequestBody SolicitudCotizacion solicitud,
			HttpServletRequest request) {
		return checkoutService.cotizar(solicitud.cuponCodigo(), solicitud.email(), solicitud.entrega(), solicitud.items(),
				ipCliente.de(request));
	}

	@GetMapping("/admin/pedidos")
	public Page<Pedido> listar(@RequestParam(required = false) EstadoPedido estado,
			@PageableDefault(size = 20) Pageable pagina) {
		return pedidoService.listar(estado, pagina);
	}

	@GetMapping("/admin/pedidos/no-vistos")
	public Map<String, Long> contarNoVistos() {
		return Map.of("cantidad", pedidoService.contarAprobadosNoVistos());
	}

	@GetMapping("/admin/pedidos/{id}")
	public Pedido obtener(@PathVariable Long id) {
		return pedidoService.obtener(id);
	}

	@PatchMapping("/admin/pedidos/{id}/visto")
	public Pedido marcarVisto(@PathVariable Long id) {
		return pedidoService.marcarVisto(id);
	}
}

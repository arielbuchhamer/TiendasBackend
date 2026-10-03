package com.tiendas.pedidos;

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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class PedidoController {

	private final CheckoutService checkoutService;
	private final PedidoService pedidoService;

	public PedidoController(CheckoutService checkoutService, PedidoService pedidoService) {
		this.checkoutService = checkoutService;
		this.pedidoService = pedidoService;
	}

	/**
	 * Crea el pedido y devuelve, entre otros datos, {@code checkoutUrl}: la URL de Mercado Pago a la que
	 * el frontend debe redirigir al comprador.
	 */
	@PostMapping("/checkout")
	@ResponseStatus(HttpStatus.CREATED)
	public Pedido checkout(@Valid @RequestBody Pedido pedido) {
		return checkoutService.iniciar(pedido);
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

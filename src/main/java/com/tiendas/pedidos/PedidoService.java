package com.tiendas.pedidos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tiendas.catalogo.Variante;
import com.tiendas.catalogo.VarianteRepository;
import com.tiendas.comun.NoEncontradoException;
import com.tiendas.config.CacheConfig;
import com.tiendas.pedidos.PasarelaPago.PagoInformado;

@Service
@Transactional(readOnly = true)
public class PedidoService {

	private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

	/** Estados desde los que un pago aprobado todavía puede aprobar el pedido. */
	private static final Set<EstadoPedido> APROBABLES = EnumSet.of(
			EstadoPedido.PENDIENTE, EstadoPedido.RECHAZADO, EstadoPedido.VENCIDO, EstadoPedido.CANCELADO);

	private final PedidoRepository pedidoRepository;
	private final PagoRepository pagoRepository;
	private final VarianteRepository varianteRepository;

	public PedidoService(PedidoRepository pedidoRepository, PagoRepository pagoRepository,
			VarianteRepository varianteRepository) {
		this.pedidoRepository = pedidoRepository;
		this.pagoRepository = pagoRepository;
		this.varianteRepository = varianteRepository;
	}

	// ─── Panel de administración ────────────────────────────────────────

	public Page<Pedido> listar(EstadoPedido estado, Pageable pagina) {
		Pageable recientesPrimero = PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(),
				Sort.by(Sort.Direction.DESC, "creadoEn"));
		Page<Pedido> pedidos = estado == null
				? pedidoRepository.findAll(recientesPrimero)
				: pedidoRepository.findByEstado(estado, recientesPrimero);
		pedidos.forEach(PedidoService::inicializar);
		return pedidos;
	}

	public Pedido obtener(Long id) {
		return inicializar(pedidoRepository.findById(id).orElseThrow(() -> NoEncontradoException.de("Pedido", id)));
	}

	public long contarAprobadosNoVistos() {
		return pedidoRepository.countByEstadoAndVistoFalse(EstadoPedido.APROBADO);
	}

	@Transactional
	public Pedido marcarVisto(Long id) {
		Pedido pedido = obtener(id);
		pedido.setVisto(true);
		return pedido;
	}

	// ─── Pagos ──────────────────────────────────────────────────────────

	/**
	 * Registra un pago informado por la pasarela. Es idempotente: el proveedor puede notificar el mismo
	 * pago varias veces (y en paralelo) y el stock se descuenta una sola vez.
	 */
	@Transactional
	@CacheEvict(cacheNames = CacheConfig.CATALOGO, allEntries = true) // el stock visible cambia
	public void registrarPago(PagoInformado informado) {
		UUID codigo = parsearCodigo(informado.referenciaPedido());
		Pedido pedido = codigo == null ? null : pedidoRepository.bloquearPorCodigo(codigo).orElse(null);
		if (pedido == null) {
			log.warn("Pago {} con referencia desconocida '{}': se ignora", informado.idExterno(), informado.referenciaPedido());
			return;
		}

		actualizarRegistroDePago(pedido, informado);

		switch (informado.resultado()) {
			case APROBADO -> aprobar(pedido, informado);
			case RECHAZADO -> {
				if (pedido.getEstado() == EstadoPedido.PENDIENTE) {
					pedido.setEstado(EstadoPedido.RECHAZADO);
				}
			}
			case REINTEGRADO -> {
				if (pedido.getEstado() == EstadoPedido.APROBADO || pedido.getEstado() == EstadoPedido.REQUIERE_REVISION) {
					// El stock no se repone automáticamente: la mercadería puede no haber vuelto
					pedido.setEstado(EstadoPedido.REINTEGRADO);
				}
			}
			case PENDIENTE -> {
				// En proceso: no cambia el pedido
			}
		}
	}

	private void actualizarRegistroDePago(Pedido pedido, PagoInformado informado) {
		Pago pago = pagoRepository.findByProveedorAndIdExterno(informado.proveedor(), informado.idExterno())
				.orElseGet(() -> {
					Pago nuevo = new Pago();
					nuevo.setProveedor(informado.proveedor());
					nuevo.setIdExterno(informado.idExterno());
					pedido.agregarPago(nuevo);
					return nuevo;
				});
		pago.setEstado(informado.estado());
		pago.setEstadoDetalle(informado.estadoDetalle());
		pago.setMonto(informado.monto());
		pago.setMoneda(informado.moneda());
		pago.setMedioPago(informado.medioPago());
		pago.setAprobadoEn(informado.aprobadoEn());
	}

	private void aprobar(Pedido pedido, PagoInformado pago) {
		if (!APROBABLES.contains(pedido.getEstado())) {
			return; // ya se procesó una aprobación para este pedido
		}
		if (!montoCubreElPedido(pedido, pago)) {
			log.warn("Pedido {}: el pago {} ({} {}) no coincide con el total ({} {})", pedido.getCodigo(),
					pago.idExterno(), pago.monto(), pago.moneda(), pedido.getTotal(), pedido.getMoneda());
			pedido.setEstado(EstadoPedido.REQUIERE_REVISION);
			return;
		}

		Map<Long, BigDecimal> cantidades = pedido.getItems().stream()
				.collect(Collectors.toMap(PedidoItem::getVarianteId, PedidoItem::getCantidad, BigDecimal::add));
		// Bloquea las filas de stock: dos pedidos que compiten por la última unidad se procesan de a uno
		List<Variante> variantes = varianteRepository.bloquearParaActualizar(cantidades.keySet());

		boolean hayStock = variantes.size() == cantidades.size()
				&& variantes.stream().allMatch(v -> v.getStock().compareTo(cantidades.get(v.getId())) >= 0);
		if (!hayStock) {
			log.warn("Pedido {} pagado sin stock suficiente: requiere revisión", pedido.getCodigo());
			pedido.setEstado(EstadoPedido.REQUIERE_REVISION);
			return;
		}

		variantes.forEach(v -> v.setStock(v.getStock().subtract(cantidades.get(v.getId()))));
		pedido.setEstado(EstadoPedido.APROBADO);
		pedido.setAprobadoEn(Instant.now());
	}

	private static boolean montoCubreElPedido(Pedido pedido, PagoInformado pago) {
		return pago.monto() != null
				&& pago.monto().compareTo(pedido.getTotal()) >= 0
				&& pedido.getMoneda().equalsIgnoreCase(pago.moneda());
	}

	private static UUID parsearCodigo(String referencia) {
		try {
			return referencia == null ? null : UUID.fromString(referencia);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static Pedido inicializar(Pedido pedido) {
		Hibernate.initialize(pedido.getItems());
		Hibernate.initialize(pedido.getPagos());
		return pedido;
	}
}

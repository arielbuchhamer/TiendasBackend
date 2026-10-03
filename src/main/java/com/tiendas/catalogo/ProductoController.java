package com.tiendas.catalogo;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Listados paginados: {@code ?page=0&size=24&sort=precio,asc}. Filtros: rubroId, categoriaId, texto, destacados.
 * Los endpoints públicos solo devuelven productos activos; los de /admin incluyen los inactivos.
 */
@RestController
@RequestMapping("/api/v1")
public class ProductoController {

	private final ProductoService productoService;

	public ProductoController(ProductoService productoService) {
		this.productoService = productoService;
	}

	@GetMapping("/productos")
	public Page<Producto> buscar(FiltroProductos filtro, @PageableDefault(size = 24) Pageable pagina) {
		return productoService.buscar(filtro, false, pagina);
	}

	@GetMapping("/productos/aleatorios")
	public List<Producto> aleatorios(@RequestParam(defaultValue = "12") int cantidad) {
		return productoService.aleatorios(cantidad);
	}

	@GetMapping("/productos/{id}")
	public Producto obtener(@PathVariable Long id) {
		return productoService.obtener(id, false);
	}

	@GetMapping("/admin/productos")
	public Page<Producto> buscarAdmin(FiltroProductos filtro, @PageableDefault(size = 24) Pageable pagina) {
		return productoService.buscar(filtro, true, pagina);
	}

	@GetMapping("/admin/productos/{id}")
	public Producto obtenerAdmin(@PathVariable Long id) {
		return productoService.obtener(id, true);
	}

	@PostMapping("/admin/productos")
	@ResponseStatus(HttpStatus.CREATED)
	public Producto crear(@Valid @RequestBody Producto producto) {
		return productoService.crear(producto);
	}

	@PutMapping("/admin/productos/{id}")
	public Producto actualizar(@PathVariable Long id, @Valid @RequestBody Producto producto) {
		return productoService.actualizar(id, producto);
	}

	@DeleteMapping("/admin/productos/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable Long id) {
		productoService.eliminar(id);
	}
}

package com.tiendas.catalogo;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tiendas.comun.ConflictoException;
import com.tiendas.comun.NoEncontradoException;
import com.tiendas.comun.ReglaNegocioException;
import com.tiendas.config.CacheConfig;

@Service
@Transactional(readOnly = true)
public class CategoriaService {

	private final CategoriaRepository categoriaRepository;
	private final RubroRepository rubroRepository;

	public CategoriaService(CategoriaRepository categoriaRepository, RubroRepository rubroRepository) {
		this.categoriaRepository = categoriaRepository;
		this.rubroRepository = rubroRepository;
	}

	@Cacheable(cacheNames = CacheConfig.CATALOGO, key = "'categorias:' + #rubroId")
	public List<Categoria> listar(Long rubroId) {
		return rubroId == null
				? categoriaRepository.findAllByOrderByOrdenAscNombreAsc()
				: categoriaRepository.findByRubroIdOrderByOrdenAscNombreAsc(rubroId);
	}

	public Categoria obtener(Long id) {
		return categoriaRepository.findById(id).orElseThrow(() -> NoEncontradoException.de("Categoría", id));
	}

	@Transactional
	@CacheEvict(cacheNames = CacheConfig.CATALOGO, allEntries = true)
	public Categoria crear(Categoria datos) {
		Categoria categoria = new Categoria();
		copiar(datos, categoria);
		return categoriaRepository.save(categoria);
	}

	@Transactional
	@CacheEvict(cacheNames = CacheConfig.CATALOGO, allEntries = true)
	public Categoria actualizar(Long id, Categoria datos) {
		Categoria categoria = obtener(id);
		copiar(datos, categoria);
		return categoria;
	}

	@Transactional
	@CacheEvict(cacheNames = CacheConfig.CATALOGO, allEntries = true)
	public void eliminar(Long id) {
		categoriaRepository.delete(obtener(id));
		try {
			categoriaRepository.flush();
		} catch (DataIntegrityViolationException e) {
			throw new ConflictoException("La categoría tiene productos asociados: eliminalos o movelos primero");
		}
	}

	private void copiar(Categoria datos, Categoria categoria) {
		if (!rubroRepository.existsById(datos.getRubroId())) {
			throw new ReglaNegocioException("El rubro " + datos.getRubroId() + " no existe");
		}
		categoria.setRubroId(datos.getRubroId());
		categoria.setNombre(datos.getNombre().trim());
		categoria.setOrden(datos.getOrden());
	}
}

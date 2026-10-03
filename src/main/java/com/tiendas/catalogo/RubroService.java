package com.tiendas.catalogo;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tiendas.comun.ConflictoException;
import com.tiendas.comun.NoEncontradoException;
import com.tiendas.config.CacheConfig;

@Service
@Transactional(readOnly = true)
public class RubroService {

	private final RubroRepository rubroRepository;

	public RubroService(RubroRepository rubroRepository) {
		this.rubroRepository = rubroRepository;
	}

	@Cacheable(cacheNames = CacheConfig.CATALOGO, key = "'rubros'")
	public List<Rubro> listar() {
		return rubroRepository.findAllByOrderByOrdenAscNombreAsc();
	}

	public Rubro obtener(Long id) {
		return rubroRepository.findById(id).orElseThrow(() -> NoEncontradoException.de("Rubro", id));
	}

	@Transactional
	@CacheEvict(cacheNames = CacheConfig.CATALOGO, allEntries = true)
	public Rubro crear(Rubro datos) {
		Rubro rubro = new Rubro();
		copiar(datos, rubro);
		return rubroRepository.save(rubro);
	}

	@Transactional
	@CacheEvict(cacheNames = CacheConfig.CATALOGO, allEntries = true)
	public Rubro actualizar(Long id, Rubro datos) {
		Rubro rubro = obtener(id);
		copiar(datos, rubro);
		return rubro;
	}

	@Transactional
	@CacheEvict(cacheNames = CacheConfig.CATALOGO, allEntries = true)
	public void eliminar(Long id) {
		rubroRepository.delete(obtener(id));
		try {
			rubroRepository.flush();
		} catch (DataIntegrityViolationException e) {
			throw new ConflictoException("El rubro tiene categorías asociadas: eliminalas o movelas primero");
		}
	}

	private static void copiar(Rubro datos, Rubro rubro) {
		rubro.setNombre(datos.getNombre().trim());
		rubro.setOrden(datos.getOrden());
	}
}

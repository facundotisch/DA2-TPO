package logired.backend.inventario.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA para la persistencia del inventario multidepósito en PostgreSQL.
 */
@Entity
@Table(name = "items_inventario")
public class ItemInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sku;

    private String nombre;

    private String deposito;

    private Integer stockDisponible;

    private Integer stockBloqueado;

    public ItemInventario() {
    }

    public ItemInventario(String sku, String nombre, String deposito, Integer stockDisponible, Integer stockBloqueado) {
        this.sku = sku;
        this.nombre = nombre;
        this.deposito = deposito;
        this.stockDisponible = stockDisponible;
        this.stockBloqueado = stockBloqueado;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDeposito() {
        return deposito;
    }

    public void setDeposito(String deposito) {
        this.deposito = deposito;
    }

    public Integer getStockDisponible() {
        return stockDisponible;
    }

    public void setStockDisponible(Integer stockDisponible) {
        this.stockDisponible = stockDisponible;
    }

    public Integer getStockBloqueado() {
        return stockBloqueado;
    }

    public void setStockBloqueado(Integer stockBloqueado) {
        this.stockBloqueado = stockBloqueado;
    }
}

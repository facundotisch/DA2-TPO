package logired.backend.pedido.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import logired.backend.deposito.entity.Deposito;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String descripcion;

    private String estado;

    private Double total;

    private String clienteNombre;

    private String direccionEntrega;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "deposito_id")
    private Deposito deposito;

    @OneToMany(mappedBy = "pedido", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private final List<PedidoItem> items = new ArrayList<>();

    public Pedido() {
    }

    public Pedido(String descripcion, String estado, Double total) {
        this.descripcion = descripcion;
        this.estado = estado;
        this.total = total;
    }

    public Long getId() {
        return id;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Double getTotal() {
        return total;
    }

    public void setTotal(Double total) {
        this.total = total;
    }

    public Deposito getDeposito() {
        return deposito;
    }

    public void setDeposito(Deposito deposito) {
        this.deposito = deposito;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public void setClienteNombre(String clienteNombre) {
        this.clienteNombre = clienteNombre;
    }

    public String getDireccionEntrega() {
        return direccionEntrega;
    }

    public void setDireccionEntrega(String direccionEntrega) {
        this.direccionEntrega = direccionEntrega;
    }

    public List<PedidoItem> getItems() {
        return items;
    }

    public void agregarItem(PedidoItem item) {
        item.setPedido(this);
        items.add(item);
    }
}
package logired.backend.pedido.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import logired.backend.deposito.entity.Deposito;
import logired.backend.pedido.state.EstadoAsignado;
import logired.backend.pedido.state.EstadoCreado;
import logired.backend.pedido.state.EstadoEnRuta;
import logired.backend.pedido.state.EstadoEntregado;
import logired.backend.pedido.state.EstadoFallido;
import logired.backend.pedido.state.EstadoPedido;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String descripcion;

    private String estado;

    private Double total;

    // Control de concurrencia optimista: dos actualizaciones simultáneas del mismo pedido
    // no se pisan; la segunda recibe HTTP 409. El default 0 cubre filas ya existentes.
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0 not null")
    private Long version;

    private String clienteNombre;

    private String direccionEntrega;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "deposito_id")
    private Deposito deposito;

    @OneToMany(mappedBy = "pedido", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private final List<PedidoItem> items = new ArrayList<>();

    @Transient
    private EstadoPedido estadoInstancia;

    public Pedido() {
        this.setEstadoInstancia(new EstadoCreado());
    }

    /**
     * Reconstruye un pedido con un estado conocido (datos históricos y tests).
     * Rechaza nombres de estado inexistentes para que nunca se persista un estado inválido.
     */
    public Pedido(String descripcion, String estado, Double total) {
        this.descripcion = descripcion;
        this.total = total;
        restaurarEstado(estado);
    }

    @PostLoad
    private void reconstruirEstadoDesdeBD() {
        sincronizarInstanciaDesdeTexto(this.estado);
    }

    private static EstadoPedido estadoDesdeTexto(String estadoTexto) {
        if (estadoTexto == null) {
            return new EstadoCreado();
        }
        switch (estadoTexto.toUpperCase()) {
            case "CREADO":
                return new EstadoCreado();
            case "ASIGNADO":
                return new EstadoAsignado();
            case "EN_RUTA":
            case "ENRUTA":
                return new EstadoEnRuta();
            case "ENTREGADO":
                return new EstadoEntregado();
            case "FALLIDO":
                return new EstadoFallido();
            default:
                throw new IllegalArgumentException("Estado de pedido desconocido: " + estadoTexto);
        }
    }

    private void sincronizarInstanciaDesdeTexto(String estadoTexto) {
        if (estadoTexto == null) {
            this.estadoInstancia = new EstadoCreado();
            return;
        }
        switch (estadoTexto.toUpperCase()) {
            case "ASIGNADO":
                this.estadoInstancia = new EstadoAsignado();
                break;
            case "EN_RUTA":
            case "ENRUTA":
                this.estadoInstancia = new EstadoEnRuta();
                break;
            case "ENTREGADO":
                this.estadoInstancia = new EstadoEntregado();
                break;
            case "FALLIDO":
                this.estadoInstancia = new EstadoFallido();
                break;
            default:
                this.estadoInstancia = new EstadoCreado();
                break;
        }
    }

    // --- MÉTODOS DEL PATRÓN STATE ---
    public void avanzarAAsignado() {
        if (estadoInstancia == null) reconstruirEstadoDesdeBD();
        estadoInstancia.asignar(this);
    }

    public void avanzarAEnRuta() {
        if (estadoInstancia == null) reconstruirEstadoDesdeBD();
        estadoInstancia.ponerEnRuta(this);
    }

    public void avanzarAEntregado() {
        if (estadoInstancia == null) reconstruirEstadoDesdeBD();
        estadoInstancia.entregar(this);
    }

    public void reportarFalla() {
        if (estadoInstancia == null) reconstruirEstadoDesdeBD();
        estadoInstancia.fallar(this);
    }

    public void setEstadoInstancia(EstadoPedido nuevoEstado) {
        this.estadoInstancia = nuevoEstado;
        this.estado = nuevoEstado.getNombreEstado();
    }

    // --- GETTERS Y SETTERS ORIGINALES INTACTOS ---
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

    /**
     * El estado no tiene setter público: solo cambia por las transiciones del patrón State
     * (avanzarAAsignado, avanzarAEnRuta, avanzarAEntregado, reportarFalla).
     */
    private void restaurarEstado(String estadoTexto) {
        setEstadoInstancia(estadoDesdeTexto(estadoTexto));
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

    public String getMapaEntregaUrl() {
        if (direccionEntrega != null && !direccionEntrega.isBlank()) {
            return "https://www.openstreetmap.org/search?query="
                    + URLEncoder.encode(direccionEntrega, StandardCharsets.UTF_8);
        }
        return deposito == null ? null : deposito.getOpenStreetMapUrl();
    }

    public void agregarItem(PedidoItem item) {
        item.setPedido(this);
        items.add(item);
    }
}
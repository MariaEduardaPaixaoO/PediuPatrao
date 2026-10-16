package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Pedido da pizzaria.
 *
 * Todos os valores monetários, o status, os responsáveis e os horários são definidos
 * exclusivamente pelo servidor (PedidoService). O status é armazenado como texto para
 * que pedidos antigos (formato anterior à evolução) continuem sendo lidos sem erro.
 */
@Document(collection = "pedidos")
public class Pedido {

    @Id
    private String id;

    /** Controle de concorrência otimista. */
    @Version
    private Long version;

    private String clienteId;
    /** Nome do cliente no momento do registro (preserva o histórico). */
    private String clienteNome;
    private TipoEntrega tipoEntrega;

    private List<ItemPedido> itens = new ArrayList<>();

    private BigDecimal subtotal;
    private BigDecimal descontoPercentual;
    private BigDecimal valorDesconto;
    private BigDecimal valorTotal;

    /** Valor de {@link StatusPedido} (texto). */
    private String status;

    // Entrada
    private String registradoPor;
    private LocalDateTime dataHoraEntrada;

    // Saída (entrega ou retirada)
    private String saidaPor;
    private LocalDateTime dataHoraSaida;

    // Cancelamento
    private String canceladoPor;
    private LocalDateTime dataHoraCancelamento;
    private String justificativaCancelamento;

    private List<TransicaoStatus> historico = new ArrayList<>();

    /** Cópia independente (listas novas) usada para comparações e compensações. */
    public Pedido copiar() {
        Pedido c = new Pedido();
        c.id = id;
        c.version = version;
        c.clienteId = clienteId;
        c.clienteNome = clienteNome;
        c.tipoEntrega = tipoEntrega;
        c.itens = itens == null ? new ArrayList<>() : new ArrayList<>(itens);
        c.subtotal = subtotal;
        c.descontoPercentual = descontoPercentual;
        c.valorDesconto = valorDesconto;
        c.valorTotal = valorTotal;
        c.status = status;
        c.registradoPor = registradoPor;
        c.dataHoraEntrada = dataHoraEntrada;
        c.saidaPor = saidaPor;
        c.dataHoraSaida = dataHoraSaida;
        c.canceladoPor = canceladoPor;
        c.dataHoraCancelamento = dataHoraCancelamento;
        c.justificativaCancelamento = justificativaCancelamento;
        c.historico = historico == null ? new ArrayList<>() : new ArrayList<>(historico);
        return c;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }

    public String getClienteNome() { return clienteNome; }
    public void setClienteNome(String clienteNome) { this.clienteNome = clienteNome; }

    public TipoEntrega getTipoEntrega() { return tipoEntrega; }
    public void setTipoEntrega(TipoEntrega tipoEntrega) { this.tipoEntrega = tipoEntrega; }

    public List<ItemPedido> getItens() { return itens; }
    public void setItens(List<ItemPedido> itens) { this.itens = itens; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getDescontoPercentual() { return descontoPercentual; }
    public void setDescontoPercentual(BigDecimal descontoPercentual) { this.descontoPercentual = descontoPercentual; }

    public BigDecimal getValorDesconto() { return valorDesconto; }
    public void setValorDesconto(BigDecimal valorDesconto) { this.valorDesconto = valorDesconto; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(String registradoPor) { this.registradoPor = registradoPor; }

    public LocalDateTime getDataHoraEntrada() { return dataHoraEntrada; }
    public void setDataHoraEntrada(LocalDateTime dataHoraEntrada) { this.dataHoraEntrada = dataHoraEntrada; }

    public String getSaidaPor() { return saidaPor; }
    public void setSaidaPor(String saidaPor) { this.saidaPor = saidaPor; }

    public LocalDateTime getDataHoraSaida() { return dataHoraSaida; }
    public void setDataHoraSaida(LocalDateTime dataHoraSaida) { this.dataHoraSaida = dataHoraSaida; }

    public String getCanceladoPor() { return canceladoPor; }
    public void setCanceladoPor(String canceladoPor) { this.canceladoPor = canceladoPor; }

    public LocalDateTime getDataHoraCancelamento() { return dataHoraCancelamento; }
    public void setDataHoraCancelamento(LocalDateTime dataHoraCancelamento) { this.dataHoraCancelamento = dataHoraCancelamento; }

    public String getJustificativaCancelamento() { return justificativaCancelamento; }
    public void setJustificativaCancelamento(String justificativaCancelamento) { this.justificativaCancelamento = justificativaCancelamento; }

    public List<TransicaoStatus> getHistorico() { return historico; }
    public void setHistorico(List<TransicaoStatus> historico) { this.historico = historico; }
}

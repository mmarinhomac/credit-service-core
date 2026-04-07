package br.com.sicredi.creditservice.application.usecase;

import br.com.sicredi.creditservice.domain.exception.AgroSemAreaException;
import br.com.sicredi.creditservice.domain.exception.ElegibilidadeNegadaException;
import br.com.sicredi.creditservice.domain.model.OperacaoCredito;
import br.com.sicredi.creditservice.domain.model.OperacaoSocio;
import br.com.sicredi.creditservice.domain.model.Segmento;
import br.com.sicredi.creditservice.domain.port.in.ContratoOperacaoCommand;
import br.com.sicredi.creditservice.domain.port.in.ContratarOperacaoCreditoUseCase;
import br.com.sicredi.creditservice.domain.port.out.OperacaoCreditoRepository;
import br.com.sicredi.creditservice.domain.port.out.OperacaoSocioRepository;
import br.com.sicredi.creditservice.domain.port.out.ProdutoElegibilidadePort;

import java.time.LocalDateTime;
import java.util.UUID;

public class ContratarOperacaoCreditoService implements ContratarOperacaoCreditoUseCase {

    private final OperacaoCreditoRepository operacaoCreditoRepository;
    private final OperacaoSocioRepository operacaoSocioRepository;
    private final ProdutoElegibilidadePort produtoElegibilidadePort;

    public ContratarOperacaoCreditoService(
            OperacaoCreditoRepository operacaoCreditoRepository,
            OperacaoSocioRepository operacaoSocioRepository,
            ProdutoElegibilidadePort produtoElegibilidadePort) {
        this.operacaoCreditoRepository = operacaoCreditoRepository;
        this.operacaoSocioRepository = operacaoSocioRepository;
        this.produtoElegibilidadePort = produtoElegibilidadePort;
    }

    @Override
    public UUID executar(ContratoOperacaoCommand command) {
        validarAgro(command);
        verificarElegibilidade(command);

        OperacaoCredito operacao = new OperacaoCredito(
                UUID.randomUUID(),
                command.idAssociado(),
                command.valorOperacao(),
                command.segmento(),
                command.codigoProdutoCredito(),
                command.codigoConta(),
                command.areaBeneficiadaHa(),
                LocalDateTime.now()
        );

        OperacaoCredito salva = operacaoCreditoRepository.save(operacao);

        if (command.segmento() == Segmento.PJ) {
            operacaoSocioRepository.save(new OperacaoSocio(salva.idOperacaoCredito(), command.idAssociado()));
        }

        return salva.idOperacaoCredito();
    }

    private void validarAgro(ContratoOperacaoCommand command) {
        if (command.segmento() == Segmento.AGRO
                && (command.areaBeneficiadaHa() == null || command.areaBeneficiadaHa().signum() <= 0)) {
            throw new AgroSemAreaException();
        }
    }

    private void verificarElegibilidade(ContratoOperacaoCommand command) {
        boolean permitido = produtoElegibilidadePort.verificar(
                command.codigoProdutoCredito(),
                command.segmento(),
                command.valorOperacao()
        );
        if (!permitido) {
            throw new ElegibilidadeNegadaException(
                    command.codigoProdutoCredito(),
                    command.segmento().name()
            );
        }
    }
}

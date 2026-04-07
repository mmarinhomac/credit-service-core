package br.com.sicredi.creditservice.web.controller;

import br.com.sicredi.creditservice.domain.model.OperacaoCredito;
import br.com.sicredi.creditservice.domain.model.Segmento;
import br.com.sicredi.creditservice.domain.port.in.ContratoOperacaoCommand;
import br.com.sicredi.creditservice.domain.port.in.ContratarOperacaoCreditoUseCase;
import br.com.sicredi.creditservice.domain.port.in.ConsultarOperacaoCreditoUseCase;
import br.com.sicredi.creditservice.web.dto.ContratoOperacaoRequest;
import br.com.sicredi.creditservice.web.dto.ContratoOperacaoResponse;
import br.com.sicredi.creditservice.web.dto.OperacaoCreditoResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/operacoes-credito")
public class OperacaoCreditoController {

    private final ContratarOperacaoCreditoUseCase contratarUseCase;
    private final ConsultarOperacaoCreditoUseCase consultarUseCase;

    public OperacaoCreditoController(ContratarOperacaoCreditoUseCase contratarUseCase,
                                     ConsultarOperacaoCreditoUseCase consultarUseCase) {
        this.contratarUseCase = contratarUseCase;
        this.consultarUseCase = consultarUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContratoOperacaoResponse contratar(@RequestBody ContratoOperacaoRequest request) {
        ContratoOperacaoCommand command = new ContratoOperacaoCommand(
                request.idAssociado(),
                request.valorOperacao(),
                Segmento.valueOf(request.segmento()),
                request.codigoProdutoCredito(),
                request.codigoConta(),
                request.areaBeneficiadaHa()
        );
        UUID idOperacaoCredito = contratarUseCase.executar(command);
        return new ContratoOperacaoResponse(idOperacaoCredito);
    }

    @GetMapping("/{idOperacaoCredito}")
    public OperacaoCreditoResponse consultar(@PathVariable UUID idOperacaoCredito) {
        OperacaoCredito operacao = consultarUseCase.executar(idOperacaoCredito);
        return toResponse(operacao);
    }

    private OperacaoCreditoResponse toResponse(OperacaoCredito operacao) {
        return new OperacaoCreditoResponse(
                operacao.idOperacaoCredito(),
                operacao.idAssociado(),
                operacao.valorOperacao(),
                operacao.segmento().name(),
                operacao.codigoProdutoCredito(),
                operacao.codigoConta(),
                operacao.areaBeneficiadaHa(),
                operacao.dataContratacao()
        );
    }
}

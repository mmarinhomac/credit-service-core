package br.com.sicredi.creditservice.config;

import br.com.sicredi.creditservice.application.usecase.ConsultarOperacaoCreditoService;
import br.com.sicredi.creditservice.application.usecase.ContratarOperacaoCreditoService;
import br.com.sicredi.creditservice.domain.port.in.ConsultarOperacaoCreditoUseCase;
import br.com.sicredi.creditservice.domain.port.in.ContratarOperacaoCreditoUseCase;
import br.com.sicredi.creditservice.domain.port.out.OperacaoCreditoRepository;
import br.com.sicredi.creditservice.domain.port.out.OperacaoSocioRepository;
import br.com.sicredi.creditservice.domain.port.out.ProdutoElegibilidadePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public ContratarOperacaoCreditoUseCase contratarOperacaoCreditoUseCase(
            OperacaoCreditoRepository operacaoCreditoRepository,
            OperacaoSocioRepository operacaoSocioRepository,
            ProdutoElegibilidadePort produtoElegibilidadePort) {
        return new ContratarOperacaoCreditoService(
                operacaoCreditoRepository,
                operacaoSocioRepository,
                produtoElegibilidadePort);
    }

    @Bean
    public ConsultarOperacaoCreditoUseCase consultarOperacaoCreditoUseCase(
            OperacaoCreditoRepository operacaoCreditoRepository) {
        return new ConsultarOperacaoCreditoService(operacaoCreditoRepository);
    }
}

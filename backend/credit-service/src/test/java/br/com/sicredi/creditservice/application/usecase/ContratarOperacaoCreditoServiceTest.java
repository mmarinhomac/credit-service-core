package br.com.sicredi.creditservice.application.usecase;

import br.com.sicredi.creditservice.domain.exception.AgroSemAreaException;
import br.com.sicredi.creditservice.domain.exception.ElegibilidadeNegadaException;
import br.com.sicredi.creditservice.domain.model.Segmento;
import br.com.sicredi.creditservice.domain.port.in.ContratoOperacaoCommand;
import br.com.sicredi.creditservice.domain.port.out.OperacaoCreditoRepository;
import br.com.sicredi.creditservice.domain.port.out.OperacaoSocioRepository;
import br.com.sicredi.creditservice.domain.port.out.ProdutoElegibilidadePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContratarOperacaoCreditoServiceTest {

    @Mock OperacaoCreditoRepository operacaoCreditoRepository;
    @Mock OperacaoSocioRepository operacaoSocioRepository;
    @Mock ProdutoElegibilidadePort produtoElegibilidadePort;

    ContratarOperacaoCreditoService service;

    @BeforeEach
    void setUp() {
        service = new ContratarOperacaoCreditoService(
                operacaoCreditoRepository, operacaoSocioRepository, produtoElegibilidadePort);
    }

    @Test
    void shouldThrowWhenAgroHasNullArea() {
        ContratoOperacaoCommand command = agroCommand(null);

        assertThatThrownBy(() -> service.executar(command))
                .isInstanceOf(AgroSemAreaException.class);

        verify(produtoElegibilidadePort, never()).verificar(any(), any(), any());
        verify(operacaoCreditoRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenAgroHasZeroArea() {
        ContratoOperacaoCommand command = agroCommand(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.executar(command))
                .isInstanceOf(AgroSemAreaException.class);

        verify(produtoElegibilidadePort, never()).verificar(any(), any(), any());
    }

    @Test
    void shouldThrowWhenEligibilityDenied() {
        ContratoOperacaoCommand command = pfCommand();
        when(produtoElegibilidadePort.verificar(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.executar(command))
                .isInstanceOf(ElegibilidadeNegadaException.class);

        verify(operacaoCreditoRepository, never()).save(any());
    }

    @Test
    void shouldContractPfOperacaoWithoutSocioRecord() {
        ContratoOperacaoCommand command = pfCommand();
        when(produtoElegibilidadePort.verificar(any(), any(), any())).thenReturn(true);
        when(operacaoCreditoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID id = service.executar(command);

        assertThat(id).isNotNull();
        verify(operacaoSocioRepository, never()).save(any());
    }

    @Test
    void shouldContractPjOperacaoAndPersistSocioRecord() {
        ContratoOperacaoCommand command = pjCommand();
        when(produtoElegibilidadePort.verificar(any(), any(), any())).thenReturn(true);
        when(operacaoCreditoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID id = service.executar(command);

        assertThat(id).isNotNull();
        ArgumentCaptor<br.com.sicredi.creditservice.domain.model.OperacaoSocio> captor =
                ArgumentCaptor.forClass(br.com.sicredi.creditservice.domain.model.OperacaoSocio.class);
        verify(operacaoSocioRepository).save(captor.capture());
        assertThat(captor.getValue().idOperacaoCredito()).isEqualTo(id);
        assertThat(captor.getValue().idAssociado()).isEqualTo(command.idAssociado());
    }

    @Test
    void shouldContractAgroOperacaoWhenAreaIsValid() {
        ContratoOperacaoCommand command = agroCommand(new BigDecimal("10.5"));
        when(produtoElegibilidadePort.verificar(any(), any(), any())).thenReturn(true);
        when(operacaoCreditoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID id = service.executar(command);

        assertThat(id).isNotNull();
        verify(operacaoSocioRepository, never()).save(any());
    }

    // --- helpers ---

    private ContratoOperacaoCommand pfCommand() {
        return new ContratoOperacaoCommand(1L, new BigDecimal("1000"), Segmento.PF,
                "101A", "0123456789", null);
    }

    private ContratoOperacaoCommand pjCommand() {
        return new ContratoOperacaoCommand(2L, new BigDecimal("10000"), Segmento.PJ,
                "202B", "9876543210", null);
    }

    private ContratoOperacaoCommand agroCommand(BigDecimal areaBeneficiadaHa) {
        return new ContratoOperacaoCommand(3L, new BigDecimal("5000"), Segmento.AGRO,
                "303C", "1234567890", areaBeneficiadaHa);
    }
}

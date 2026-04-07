package br.com.sicredi.creditservice.application.usecase;

import br.com.sicredi.creditservice.domain.exception.OperacaoNaoEncontradaException;
import br.com.sicredi.creditservice.domain.model.OperacaoCredito;
import br.com.sicredi.creditservice.domain.model.Segmento;
import br.com.sicredi.creditservice.domain.port.out.OperacaoCreditoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultarOperacaoCreditoServiceTest {

    @Mock OperacaoCreditoRepository operacaoCreditoRepository;

    ConsultarOperacaoCreditoService service;

    @BeforeEach
    void setUp() {
        service = new ConsultarOperacaoCreditoService(operacaoCreditoRepository);
    }

    @Test
    void shouldReturnOperacaoWhenFound() {
        UUID id = UUID.randomUUID();
        OperacaoCredito expected = operacao(id);
        when(operacaoCreditoRepository.findById(id)).thenReturn(Optional.of(expected));

        OperacaoCredito result = service.executar(id);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldThrowWhenOperacaoNotFound() {
        UUID id = UUID.randomUUID();
        when(operacaoCreditoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.executar(id))
                .isInstanceOf(OperacaoNaoEncontradaException.class)
                .hasMessageContaining(id.toString());
    }

    private OperacaoCredito operacao(UUID id) {
        return new OperacaoCredito(id, 1L, new BigDecimal("1000"), Segmento.PF,
                "101A", "0123456789", null, LocalDateTime.now());
    }
}

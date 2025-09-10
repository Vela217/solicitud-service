package co.com.solicitudes.usecase.listforreview;

import co.com.solicitudes.model.loanapplication.LoanApplication;
import co.com.solicitudes.model.loanapplication.gateways.LoanApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListForReviewUseCaseTest {

    @Mock
    LoanApplicationRepository repo;

    private ListForReviewUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListForReviewUseCase(repo);
    }

    @Test
    @DisplayName("list ⇒ devuelve PageResult con content, total y totalPages calculado (ceil)")
    void list_returnsPageResult_withContentAndTotals() {
        // Arrange
        int page = 0;
        int size = 2;

        var app1 = LoanApplication.builder()
                .numberDocument("11111")
                .termMonths(12)
                .amount(new BigDecimal("1200000"))
                .build();

        var app2 = LoanApplication.builder()
                .numberDocument("22222")
                .termMonths(24)
                .amount(new BigDecimal("2500000"))
                .build();

        // total = 5  -> totalPages = ceil(5/2) = 3
        when(repo.countForReview(anyList())).thenReturn(Mono.just(5L));
        when(repo.findForReview(anyList(), eq(page), eq(size)))
                .thenReturn(Flux.just(app1, app2));

        // Act
        var result = useCase.list(page, size);

        // Assert
        StepVerifier.create(result)
                .assertNext(pr -> {
                    // Campos del record
                    org.junit.jupiter.api.Assertions.assertEquals(2, pr.content().size());
                    org.junit.jupiter.api.Assertions.assertEquals(5L, pr.total());
                    org.junit.jupiter.api.Assertions.assertEquals(0, pr.page());
                    org.junit.jupiter.api.Assertions.assertEquals(2, pr.size());
                    org.junit.jupiter.api.Assertions.assertEquals(3, pr.totalPages());
                    org.junit.jupiter.api.Assertions.assertEquals("11111", pr.content().get(0).getNumberDocument());
                    org.junit.jupiter.api.Assertions.assertEquals("22222", pr.content().get(1).getNumberDocument());
                })
                .verifyComplete();

        // Verify order & params
        InOrder inOrder = inOrder(repo);
        inOrder.verify(repo).countForReview(anyList());
        inOrder.verify(repo).findForReview(anyList(), eq(page), eq(size));
        verifyNoMoreInteractions(repo);
    }

    @Test
    @DisplayName("list ⇒ devuelve PageResult vacío cuando total=0 y no hay elementos")
    void list_returnsEmptyPageResult_whenNoData() {
        // Arrange
        int page = 1;
        int size = 10;

        when(repo.countForReview(anyList())).thenReturn(Mono.just(0L));
        when(repo.findForReview(anyList(), eq(page), eq(size)))
                .thenReturn(Flux.empty());

        var result = useCase.list(page, size);

        StepVerifier.create(result)
                .assertNext(pr -> {
                    org.junit.jupiter.api.Assertions.assertTrue(pr.content().isEmpty());
                    org.junit.jupiter.api.Assertions.assertEquals(0L, pr.total());
                    org.junit.jupiter.api.Assertions.assertEquals(1, pr.page());
                    org.junit.jupiter.api.Assertions.assertEquals(10, pr.size());
                    // ceil(0/10) = 0
                    org.junit.jupiter.api.Assertions.assertEquals(0, pr.totalPages());
                })
                .verifyComplete();

        InOrder inOrder = inOrder(repo);
        inOrder.verify(repo).countForReview(anyList());
        inOrder.verify(repo).findForReview(anyList(), eq(page), eq(size));
        verifyNoMoreInteractions(repo);
    }
}

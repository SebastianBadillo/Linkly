package io.github.sebastianbadillo.linkly.clicks;

import io.github.sebastianbadillo.linkly.repository.LinkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClickFlusherTest {

    @Mock
    private ClickCounter counter;
    @Mock
    private LinkRepository repository;
    @InjectMocks
    private ClickFlusher flusher;

    @Test
    void writesEachPendingCountToPostgres() {
        when(counter.takePendingCodes(ClickFlusher.BATCH_SIZE)).thenReturn(List.of("bf26"));
        when(counter.takeCount("bf26")).thenReturn(4200L);

        flusher.flush();

        verify(repository).addClicks("bf26", 4200);
    }

    @Test
    void emptyCounterMeansNoDatabaseCall() {
        when(counter.takePendingCodes(ClickFlusher.BATCH_SIZE)).thenReturn(List.of("bf26"));
        when(counter.takeCount("bf26")).thenReturn(0L); // GETDEL returned nil

        flusher.flush();

        verifyNoInteractions(repository);
    }

    @Test
    void failedUpdateRestoresTheClicksAndContinuesWithTheOtherLinks() {
        when(counter.takePendingCodes(ClickFlusher.BATCH_SIZE)).thenReturn(List.of("bad", "good"));
        when(counter.takeCount("bad")).thenReturn(7L);
        when(counter.takeCount("good")).thenReturn(3L);
        when(repository.addClicks("bad", 7)).thenThrow(new DataAccessResourceFailureException("db down"));
        when(repository.addClicks("good", 3)).thenReturn(1);

        flusher.flush();

        verify(counter).restore("bad", 7);
        verify(repository).addClicks("good", 3);
        verify(counter, never()).restore("good", 3);
    }

    @Test
    void failedReadRestoresNothing() {
        when(counter.takePendingCodes(ClickFlusher.BATCH_SIZE)).thenReturn(List.of("bf26"));
        when(counter.takeCount("bf26")).thenThrow(new IllegalStateException("Valkey down"));

        flusher.flush();

        verify(counter, never()).restore(anyString(), anyLong());
        verifyNoInteractions(repository);
    }

    @Test
    void valkeyDownSkipsTheRunWithoutFailing() {
        when(counter.takePendingCodes(ClickFlusher.BATCH_SIZE)).thenThrow(new IllegalStateException("Valkey down"));

        assertThatCode(() -> flusher.flush()).doesNotThrowAnyException();

        verifyNoInteractions(repository);
    }
}

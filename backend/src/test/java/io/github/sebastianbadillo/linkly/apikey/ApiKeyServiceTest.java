package io.github.sebastianbadillo.linkly.apikey;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiKeyServiceTest {

    private final ApiKeyRepository repository = mock(ApiKeyRepository.class);
    private final ApiKeyService service = new ApiKeyService(repository);

    @Test
    void createStoresOnlyTheHashAndReturnsTheRawKeyOnce() {
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        String rawKey = service.create("k6 load test").key();

        ArgumentCaptor<ApiKey> saved = ArgumentCaptor.forClass(ApiKey.class);
        verify(repository).save(saved.capture());
        assertThat(rawKey).startsWith("lk_").hasSize(3 + 43); // 32 bytes -> 43 base64url chars
        assertThat(saved.getValue().getKeyHash()).isEqualTo(ApiKeyService.sha256(rawKey)).isNotEqualTo(rawKey);
    }

    @Test
    void twoKeysAreNeverTheSame() {
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(service.create("a").key()).isNotEqualTo(service.create("b").key());
    }

    @Test
    void validKeyIsFound() {
        ApiKey key = new ApiKey(ApiKeyService.sha256("lk_good"), "x");
        when(repository.findByKeyHash(ApiKeyService.sha256("lk_good"))).thenReturn(Optional.of(key));

        assertThat(service.authenticate("lk_good")).contains(key);
    }

    @Test
    void unknownKeyIsEmpty() {
        when(repository.findByKeyHash(any())).thenReturn(Optional.empty());
        assertThat(service.authenticate("lk_unknown")).isEmpty();
    }

    @Test
    void revokedKeyIsEmpty() {
        ApiKey key = new ApiKey(ApiKeyService.sha256("lk_old"), "x");
        key.revoke();
        when(repository.findByKeyHash(any())).thenReturn(Optional.of(key));

        assertThat(service.authenticate("lk_old")).isEmpty();
    }

    @Test
    void missingKeyIsEmptyWithoutTouchingTheDatabase() {
        assertThat(service.authenticate(null)).isEmpty();
        assertThat(service.authenticate("  ")).isEmpty();
        verifyNoInteractions(repository);
    }
}

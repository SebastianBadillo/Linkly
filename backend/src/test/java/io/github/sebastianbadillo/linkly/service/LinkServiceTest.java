package io.github.sebastianbadillo.linkly.service;

import io.github.sebastianbadillo.linkly.entity.Link;
import io.github.sebastianbadillo.linkly.exception.AliasTakenException;
import io.github.sebastianbadillo.linkly.exception.CodeGenerationException;
import io.github.sebastianbadillo.linkly.exception.InvalidAliasException;
import io.github.sebastianbadillo.linkly.exception.InvalidUrlException;
import io.github.sebastianbadillo.linkly.exception.LinkNotFoundException;
import io.github.sebastianbadillo.linkly.repository.LinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests: no Spring, no database. The repository and generator are mocks we program,
 * so we can force rare situations (like a code collision) on demand.
 * The validators are real: simple classes with no dependencies, so there is nothing to fake.
 */
@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

    private static final String URL = "https://example.com/black-friday";

    @Mock
    private LinkRepository repository;
    @Mock
    private ShortCodeGenerator generator;

    private LinkService service;

    @BeforeEach
    void setUp() {
        service = new LinkService(repository, generator, new AliasValidator(), new TargetUrlValidator());
    }

    // --- create with a generated code ---

    @Test
    void createSavesTheGeneratedCode() {
        when(generator.generate()).thenReturn("aaaaaa");
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0)); // return what was saved

        Link result = service.create(URL, null);

        assertThat(result.getCode()).isEqualTo("aaaaaa");
        assertThat(result.getTargetUrl()).isEqualTo(URL);
    }

    @Test
    void createRetriesWhenGeneratedCodeCollides() {
        when(generator.generate()).thenReturn("aaaaaa", "bbbbbb");
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate")) // 1st save: collision
                .thenAnswer(inv -> inv.getArgument(0));                      // 2nd save: works

        Link result = service.create(URL, null);

        assertThat(result.getCode()).isEqualTo("bbbbbb");
        verify(repository, times(2)).saveAndFlush(any());
    }

    @Test
    void createGivesUpAfterFiveCollisions() {
        when(generator.generate()).thenReturn("aaaaaa");
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.create(URL, null))
                .isInstanceOf(CodeGenerationException.class);
        verify(repository, times(LinkService.MAX_CODE_ATTEMPTS)).saveAndFlush(any());
    }

    // --- create with an alias ---

    @Test
    void createUsesTheNormalizedAlias() {
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        Link result = service.create(URL, "  BF26 ");

        assertThat(result.getCode()).isEqualTo("bf26");
        verifyNoInteractions(generator); // an alias never asks the generator for a code
    }

    @Test
    void createWithTakenAliasFailsWithoutRetry() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.create(URL, "bf26"))
                .isInstanceOf(AliasTakenException.class)
                .hasMessageContaining("bf26");
        verify(repository, times(1)).saveAndFlush(any());
    }

    @Test
    void createWithInvalidAliasNeverTouchesTheDatabase() {
        assertThatThrownBy(() -> service.create(URL, "bf/26"))
                .isInstanceOf(InvalidAliasException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void createWithInvalidUrlNeverTouchesTheDatabase() {
        assertThatThrownBy(() -> service.create("javascript:alert(1)", null))
                .isInstanceOf(InvalidUrlException.class);
        verifyNoInteractions(repository, generator);
    }

    // --- read ---

    @Test
    void getByCodeLowercasesBeforeLookup() {
        Link link = new Link("bf26", URL);
        when(repository.findByCode("bf26")).thenReturn(Optional.of(link));

        assertThat(service.getByCode("BF26")).isSameAs(link);
    }

    @Test
    void getByCodeThrowsWhenMissing() {
        when(repository.findByCode("nope42")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByCode("nope42"))
                .isInstanceOf(LinkNotFoundException.class);
    }

    @Test
    void findPageClampsValuesAndSortsNewestFirst() {
        when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        service.findPage(-3, 500);

        verify(repository).findAll(captor.capture()); // catch the Pageable the service built
        Pageable used = captor.getValue();
        assertThat(used.getPageNumber()).isZero();
        assertThat(used.getPageSize()).isEqualTo(LinkService.MAX_PAGE_SIZE);
        assertThat(used.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void findPageAcceptsNormalValues() {
        when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        service.findPage(2, 20);

        verify(repository).findAll(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    // --- update ---

    @Test
    void updateChangesOnlyTheTargetUrl() {
        Link link = new Link("bf26", URL);
        when(repository.findByCode("bf26")).thenReturn(Optional.of(link));

        Link result = service.updateTargetUrl("bf26", "https://example.com/cyber-monday");

        assertThat(result.getTargetUrl()).isEqualTo("https://example.com/cyber-monday");
        assertThat(result.getCode()).isEqualTo("bf26");
        // No save() call: inside the transaction, Hibernate's dirty checking writes the change.
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateWithInvalidUrlNeverLooksUpTheLink() {
        assertThatThrownBy(() -> service.updateTargetUrl("bf26", "ftp://example.com"))
                .isInstanceOf(InvalidUrlException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void updateThrowsWhenMissing() {
        when(repository.findByCode("nope42")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateTargetUrl("nope42", URL))
                .isInstanceOf(LinkNotFoundException.class);
    }

    // --- delete ---

    @Test
    void deleteRemovesTheLink() {
        Link link = new Link("bf26", URL);
        when(repository.findByCode("bf26")).thenReturn(Optional.of(link));

        service.delete("bf26");

        verify(repository).delete(link);
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(repository.findByCode("nope42")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("nope42"))
                .isInstanceOf(LinkNotFoundException.class);
        verify(repository, never()).delete(any());
    }
}

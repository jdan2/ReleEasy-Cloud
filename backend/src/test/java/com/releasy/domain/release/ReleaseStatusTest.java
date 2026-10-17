package com.releasy.domain.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaseStatusTest {

    private static final Instant T0 = Instant.parse("2026-10-17T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(30);

    @ParameterizedTest(name = "{0} -> {1} permitido: {2}")
    @CsvSource({
            "PENDING,   RUNNING,   true",
            "PENDING,   FAILED,    true",
            "PENDING,   SUCCEEDED, false",
            "PENDING,   PENDING,   false",
            "RUNNING,   SUCCEEDED, true",
            "RUNNING,   FAILED,    true",
            "RUNNING,   PENDING,   false",
            "RUNNING,   RUNNING,   false",
            "SUCCEEDED, FAILED,    false",
            "SUCCEEDED, RUNNING,   false",
            "FAILED,    RUNNING,   false",
            "FAILED,    SUCCEEDED, false"
    })
    void transiciones(ReleaseStatus from, ReleaseStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void soloSucceededYFailedSonFinales() {
        assertThat(ReleaseStatus.PENDING.isTerminal()).isFalse();
        assertThat(ReleaseStatus.RUNNING.isTerminal()).isFalse();
        assertThat(ReleaseStatus.SUCCEEDED.isTerminal()).isTrue();
        assertThat(ReleaseStatus.FAILED.isTerminal()).isTrue();
    }

    @Test
    void cicloCompletoCorrecto() {
        Release release = pending().start(T0).succeed("https://d123.cloudfront.net", T1);

        assertThat(release.status()).isEqualTo(ReleaseStatus.SUCCEEDED);
        assertThat(release.url()).isEqualTo("https://d123.cloudfront.net");
        assertThat(release.errorMessage()).isNull();
        assertThat(release.createdAt()).isEqualTo(T0);
        assertThat(release.updatedAt()).isEqualTo(T1);
    }

    @Test
    void puedeFallarAntesDeArrancar() {
        Release release = pending().fail("No se pudo arrancar Step Functions", T1);

        assertThat(release.status()).isEqualTo(ReleaseStatus.FAILED);
        assertThat(release.errorMessage()).isEqualTo("No se pudo arrancar Step Functions");
    }

    @Test
    void noPuedeTerminarBienSinHaberArrancado() {
        assertThatThrownBy(() -> pending().succeed("https://x", T1))
                .isInstanceOf(IllegalReleaseTransitionException.class)
                .hasMessageContaining("PENDING -> SUCCEEDED");
    }

    @Test
    void unReleaseFinalizadoNoCambiaDeEstado() {
        Release failed = pending().start(T0).fail("error de build", T1);

        assertThatThrownBy(() -> failed.start(T1)).isInstanceOf(IllegalReleaseTransitionException.class);
        assertThatThrownBy(() -> failed.succeed("https://x", T1)).isInstanceOf(IllegalReleaseTransitionException.class);
    }

    private static Release pending() {
        return Release.pending("r-1", "a-1", "t-1", "abc123", "feat: algo", T0);
    }
}

package com.releasy.application.webhook;

import com.releasy.domain.app.App;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PushEventFilterTest {

    private static final String SHA = "9f2c1e0b7a6d5c4b3a291807f6e5d4c3b2a19087";
    private final PushEventFilter filter = new PushEventFilter();

    @Test
    void soloLosEventosPushSeProcesan() {
        assertThat(filter.isPush("push")).isTrue();
        assertThat(filter.isPush("ping")).isFalse();
        assertThat(filter.isPush("pull_request")).isFalse();
        assertThat(filter.isPush(null)).isFalse();
    }

    @Test
    void unPushAUnaRamaDevuelveElNombreDeLaRama() {
        assertThat(filter.pushedBranch(push("refs/heads/master", SHA, false))).contains("master");
        assertThat(filter.pushedBranch(push("refs/heads/feature/login", SHA, false))).contains("feature/login");
    }

    @Test
    void ignoraLosPushDeTags() {
        assertThat(filter.pushedBranch(push("refs/tags/v1.0.0", SHA, false))).isEmpty();
    }

    @Test
    void ignoraElBorradoDeRamas() {
        assertThat(filter.pushedBranch(push("refs/heads/master", "0000000000000000000000000000000000000000", true))).isEmpty();
        assertThat(filter.pushedBranch(push("refs/heads/master", "0000000000000000000000000000000000000000", false))).isEmpty();
    }

    @Test
    void ignoraEventosSinRefOSinSha() {
        assertThat(filter.pushedBranch(push(null, SHA, false))).isEmpty();
        assertThat(filter.pushedBranch(push("refs/heads/master", null, false))).isEmpty();
    }

    @Test
    void soloApuntaALaAppSiLaRamaCoincideExactamente() {
        App app = new App("a-1", "t-1", "tienda", "springboot-api", "acme/tienda", "master", Map.of(), Map.of(), "https://x",
                Instant.EPOCH, Instant.EPOCH);

        assertThat(filter.targets(app, "master")).isTrue();
        assertThat(filter.targets(app, "main")).isFalse();
        assertThat(filter.targets(app, "master-old")).isFalse();
    }

    private static PushEvent push(String ref, String after, boolean deleted) {
        return new PushEvent(ref, after, deleted, "acme/tienda", "Merge pull request #1");
    }
}

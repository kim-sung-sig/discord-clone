package com.example.discord.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DomainQualifiedTypeNameResolverTest {
    @Test
    void recordsNestedInControllersArePrefixedWithTheirDomain() {
        var resolver = new DomainQualifiedTypeNameResolver();

        assertThat(resolver.getNameOfClass(AuthController.SessionResponse.class)).isEqualTo("AuthSessionResponse");
        assertThat(resolver.getNameOfClass(GatewayController.SessionResponse.class)).isEqualTo("GatewaySessionResponse");
        assertThat(resolver.getNameOfClass(StageController.StageSessionResponse.class)).isEqualTo("StageSessionResponse");
    }

    @Test
    void anyRemainingNameClashFailsInsteadOfOverwritingASchema() {
        var resolver = new DomainQualifiedTypeNameResolver();
        resolver.getNameOfClass(FirstOwner.Same.class);

        assertThatThrownBy(() -> resolver.getNameOfClass(SecondOwner.Same.class))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Same");
    }

    static final class AuthController {
        record SessionResponse(String deviceName) {
        }
    }

    static final class GatewayController {
        record SessionResponse(long lastDeliveredSequence) {
        }
    }

    static final class StageController {
        record StageSessionResponse(String topic) {
        }
    }

    static final class FirstOwner {
        record Same() {
        }
    }

    static final class SecondOwner {
        record Same() {
        }
    }
}

package edu.austral.ingsis.snippethandler.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class WebSocketBearerTokenResolverTest {

    private final WebSocketBearerTokenResolver resolver = new WebSocketBearerTokenResolver();

    @Test
    void theAuthorizationHeaderStillWorks() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer abc.def.ghi");

        assertThat(resolver.resolve(request)).isEqualTo("abc.def.ghi");
    }

    @Test
    void aBrowserWebSocketSendsItAsSubprotocol() {
        assertThat(resolver.resolve(upgrade("bearer, abc.def.ghi"))).isEqualTo("abc.def.ghi");
    }

    @Test
    void withoutTheBearerProtocolThereIsNoToken() {
        assertThat(resolver.resolve(upgrade("chat, abc.def.ghi"))).isNull();
        assertThat(resolver.resolve(upgrade("bearer"))).isNull();
        assertThat(resolver.resolve(upgrade("bearer, "))).isNull();
    }

    @Test
    void theSubprotocolCountsOnlyInAWebSocketUpgrade() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Sec-WebSocket-Protocol", "bearer, abc.def.ghi");

        assertThat(resolver.resolve(request)).isNull();
    }

    @Test
    void aPlainRequestWithoutTokenHasNone() {
        assertThat(resolver.resolve(new MockHttpServletRequest())).isNull();
    }

    private static MockHttpServletRequest upgrade(String protocols) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Upgrade", "websocket");
        request.addHeader("Sec-WebSocket-Protocol", protocols);
        return request;
    }
}

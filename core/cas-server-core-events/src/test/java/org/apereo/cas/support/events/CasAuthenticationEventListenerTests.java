package org.apereo.cas.support.events;

import org.apereo.cas.authentication.CoreAuthenticationTestUtils;
import org.apereo.cas.authentication.DefaultAuthenticationTransaction;
import org.apereo.cas.authentication.adaptive.geo.GeoLocationResponse;
import org.apereo.cas.authentication.adaptive.geo.GeoLocationService;
import org.apereo.cas.config.CasCoreUtilConfiguration;
import org.apereo.cas.mock.MockTicketGrantingTicket;
import org.apereo.cas.support.events.authentication.CasAuthenticationPolicyFailureEvent;
import org.apereo.cas.support.events.authentication.CasAuthenticationTransactionFailureEvent;
import org.apereo.cas.support.events.authentication.adaptive.CasRiskyAuthenticationDetectedEvent;
import org.apereo.cas.support.events.config.CasCoreEventsConfiguration;
import org.apereo.cas.support.events.dao.AbstractCasEventRepository;
import org.apereo.cas.support.events.dao.CasEvent;
import org.apereo.cas.support.events.ticket.CasTicketGrantingTicketCreatedEvent;
import org.apereo.cas.support.events.ticket.CasTicketGrantingTicketDestroyedEvent;
import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.util.HttpRequestUtils;

import lombok.val;
import org.apereo.inspektr.common.web.ClientInfo;
import org.apereo.inspektr.common.web.ClientInfoHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.security.auth.login.FailedLoginException;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * This is {@link CasAuthenticationEventListenerTests}.
 *
 * @author Misagh Moayyed
 * @since 5.3.0
 */
@SpringBootTest(classes = {
    CasAuthenticationEventListenerTests.EventTestConfiguration.class,
    CasCoreEventsConfiguration.class,
    CasCoreUtilConfiguration.class,
    RefreshAutoConfiguration.class
})
@Tag("Events")
public class CasAuthenticationEventListenerTests {
    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Autowired
    @Qualifier(CasEventRepository.BEAN_NAME)
    private CasEventRepository casEventRepository;

    private MockHttpServletRequest request;

    @BeforeEach
    public void initialize() {
        request = new MockHttpServletRequest();
        request.setRemoteAddr("123.456.789.000");
        request.setLocalAddr("123.456.789.000");
        request.addHeader(HttpRequestUtils.USER_AGENT_HEADER, "test");
        ClientInfoHolder.setClientInfo(new ClientInfo(request));
    }

    @Test
    public void verifyCasAuthenticationWithNoClientInfo() {
        ClientInfoHolder.setClientInfo(null);
        val event = new CasAuthenticationTransactionFailureEvent(this,
            CollectionUtils.wrap("error", new FailedLoginException()),
            CollectionUtils.wrap(CoreAuthenticationTestUtils.getCredentialsWithSameUsernameAndPassword()));
        applicationContext.publishEvent(event);
        assertFalse(casEventRepository.load().findAny().isEmpty());
    }

    @Test
    public void verifyCasAuthenticationWithGeo() {
        request.addHeader("geolocation", "34,45,1,12345");
        ClientInfoHolder.setClientInfo(new ClientInfo(request));
        
        val event = new CasAuthenticationTransactionFailureEvent(this,
            CollectionUtils.wrap("error", new FailedLoginException()),
            CollectionUtils.wrap(CoreAuthenticationTestUtils.getCredentialsWithSameUsernameAndPassword()));
        applicationContext.publishEvent(event);
        val savedEventOptional = casEventRepository.load().findFirst();
        assertFalse(savedEventOptional.isEmpty());
        val savedEvent = savedEventOptional.get();
        assertEquals(CasAuthenticationTransactionFailureEvent.class.getSimpleName(), savedEvent.getEventId());
    }

    @Test
    public void verifyCasAuthenticationTransactionFailureEvent() {
        val event = new CasAuthenticationTransactionFailureEvent(this,
            CollectionUtils.wrap("error", new FailedLoginException()),
            CollectionUtils.wrap(CoreAuthenticationTestUtils.getCredentialsWithSameUsernameAndPassword()));
        applicationContext.publishEvent(event);
        val savedEventOptional = casEventRepository.load().findFirst();
        assertFalse(savedEventOptional.isEmpty());
        val savedEvent = savedEventOptional.get();
        assertEquals(CasAuthenticationTransactionFailureEvent.class.getSimpleName(), savedEvent.getEventId());
    }

    @Test
    public void verifyTicketGrantingTicketCreated() {
        val tgt = new MockTicketGrantingTicket("casuser");
        val event = new CasTicketGrantingTicketCreatedEvent(this, tgt);
        applicationContext.publishEvent(event);
        assertFalse(casEventRepository.load().findAny().isEmpty());
    }

    @Test
    public void verifyCasAuthenticationPolicyFailureEvent() {
        val event = new CasAuthenticationPolicyFailureEvent(this,
            CollectionUtils.wrap("error", new FailedLoginException()),
            new DefaultAuthenticationTransaction(CoreAuthenticationTestUtils.getService(),
                CollectionUtils.wrap(CoreAuthenticationTestUtils.getCredentialsWithSameUsernameAndPassword())),
            CoreAuthenticationTestUtils.getAuthentication());
        applicationContext.publishEvent(event);
        assertFalse(casEventRepository.load().findAny().isEmpty());
    }

    @Test
    public void verifyCasRiskyAuthenticationDetectedEvent() {
        val event = new CasRiskyAuthenticationDetectedEvent(this,
            CoreAuthenticationTestUtils.getAuthentication(),
            CoreAuthenticationTestUtils.getRegisteredService(),
            new Object());
        applicationContext.publishEvent(event);
        assertFalse(casEventRepository.load().findAny().isEmpty());
    }

    @Test
    public void verifyCasTicketGrantingTicketDestroyed() {
        val event = new CasTicketGrantingTicketDestroyedEvent(this,
            new MockTicketGrantingTicket("casuser"));
        applicationContext.publishEvent(event);
        assertFalse(casEventRepository.load().findAny().isEmpty());
    }

    @TestConfiguration(value = "EventTestConfiguration", proxyBeanMethods = false)
    public static class EventTestConfiguration {
        @Bean
        public CasEventRepository casEventRepository() {
            return new AbstractCasEventRepository(CasEventRepositoryFilter.noOp()) {
                private final Collection<CasEvent> events = new LinkedHashSet<>();

                @Override
                public CasEvent saveInternal(final CasEvent event) {
                    events.add(event);
                    return event;
                }

                @Override
                public void removeAll() {
                    events.clear();
                }

                @Override
                public Stream<CasEvent> load() {
                    return events.stream();
                }
            };
        }

        @Bean
        public GeoLocationService geoLocationService() {
            val mock = mock(GeoLocationService.class);
            when(mock.locate(anyString())).thenReturn(new GeoLocationResponse().setLatitude(156).setLongitude(34));
            return mock;
        }
    }
}

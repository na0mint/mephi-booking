package ru.mephi.eurekaserver;

import static org.assertj.core.api.Assertions.*;

import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest(properties = {"eureka.server.enable-self-preservation=false",
                        "eureka.server.wait-time-in-ms-when-sync-empty=0"})
class EurekaApplicationTest {
    @Autowired PeerAwareInstanceRegistry registry;
    @Test
    void startsStandaloneServiceRegistry() {
        assertThat(registry).isNotNull();
        assertThat(registry.getApplications().getRegisteredApplications()).isEmpty();
    }
}

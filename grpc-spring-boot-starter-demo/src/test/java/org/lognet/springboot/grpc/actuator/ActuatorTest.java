package org.lognet.springboot.grpc.actuator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.lognet.springboot.grpc.GrpcServerTestBase;
import org.lognet.springboot.grpc.TestConfig;
import org.lognet.springboot.grpc.demo.DemoApp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Created by alexf on 28-Jan-16.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {DemoApp.class, TestConfig.class}, webEnvironment = RANDOM_PORT
        , properties = {

        "management.endpoint.health.show-details=always"
        , "management.endpoint.health.show-components=always"
        , "spring.main.web-application-type=servlet"
})
@ActiveProfiles({"disable-security", "measure"})
public class ActuatorTest extends GrpcServerTestBase {

    @Autowired
    private PrometheusConfig prometheusConfig;

    @LocalServerPort
    private int localServerPort;

    private RestTemplate restTemplate = new RestTemplate();


    private String url(String path) {
        return "http://localhost:" + localServerPort + path;
    }

    @Test
    public void actuatorEnvTest() throws ExecutionException, InterruptedException {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/actuator/env"), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    public void actuatorGrpcTest() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/actuator/grpc"), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        final JsonNode json = new ObjectMapper().readTree(response.getBody());
        final JsonNode services = json.get("services");
        assertThat(services.size(), Matchers.greaterThan(0));
        for (JsonNode service : services) {
            assertThat(service.get("name").asText(), Matchers.not(Matchers.blankOrNullString()));
        }
        final int port = json.get("port").asInt();
        assertThat(port, Matchers.greaterThan(0));
    }

    @Test
    public void actuatorHealthTest() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/actuator/health/grpc"), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        final JsonNode json = new ObjectMapper().readTree(response.getBody());
        final JsonNode components = json.get("components");
        final Set<String> services = new java.util.HashSet<>();
        final Set<String> statuses = new java.util.HashSet<>();
        services.addAll(components.propertyNames());
        components.forEach(component -> statuses.add(component.get("status").asText()));

        assertThat(services, Matchers.containsInAnyOrder(super.appServicesNames().toArray(new String[]{})));
        assertThat(statuses, Matchers.contains(Status.UP.getCode()));
    }

    @Override
    protected void afterGreeting() throws Exception {


        ResponseEntity<ObjectNode> metricsResponse = restTemplate.getForEntity(url("/actuator/metrics"), ObjectNode.class);
        assertEquals(HttpStatus.OK, metricsResponse.getStatusCode());
        final String metricName = "grpc.server.calls";
        final Optional<String> containsGrpcServerCallsMetric = metricsResponse.getBody().withArray("names")
                .valueStream()
                .map(JsonNode::asText)
                .filter(metricName::equals)
                .findFirst();
        assertThat("Should contain " + metricName, containsGrpcServerCallsMetric.isPresent());


        Callable<Long> getPrometheusMetrics = () -> {
            ResponseEntity<String> response = restTemplate.getForEntity(url("/actuator/prometheus"), String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            return Stream.of(response.getBody().split(System.lineSeparator()))
                    .filter(s -> s.contains(metricName.replace('.', '_')))
                    .count();
        };

        Awaitility
                .waitAtMost(Duration.ofMillis(prometheusConfig.step().toMillis() * 2))
                .until(getPrometheusMetrics, Matchers.greaterThan(0L));

    }


}

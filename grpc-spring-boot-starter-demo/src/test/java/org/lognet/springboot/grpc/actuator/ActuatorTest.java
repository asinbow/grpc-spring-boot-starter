package org.lognet.springboot.grpc.actuator;

import com.jayway.jsonpath.JsonPath;
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
import java.util.List;
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

        List<String> serviceNames = JsonPath.read(response.getBody(), "$.services[*].name");
        assertThat(serviceNames.size(), Matchers.greaterThan(0));
        serviceNames.forEach(name -> assertThat(name, Matchers.not(Matchers.blankOrNullString())));

        int port = JsonPath.read(response.getBody(), "$.port");
        assertThat(port, Matchers.greaterThan(0));
    }

    @Test
    public void actuatorHealthTest() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/actuator/health/grpc"), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        java.util.Map<String, Object> components = JsonPath.read(response.getBody(), "$.components");
        assertThat(components.keySet(), Matchers.containsInAnyOrder(super.appServicesNames().toArray(new String[]{})));

        List<String> statuses = JsonPath.read(response.getBody(), "$.components.*.status");
        assertThat(statuses, Matchers.everyItem(Matchers.is(Status.UP.getCode())));
    }

    @Override
    protected void afterGreeting() throws Exception {

        ResponseEntity<String> metricsResponse = restTemplate.getForEntity(url("/actuator/metrics"), String.class);
        assertEquals(HttpStatus.OK, metricsResponse.getStatusCode());
        final String metricName = "grpc.server.calls";
        List<String> metricNames = JsonPath.read(metricsResponse.getBody(), "$.names");
        assertThat("Should contain " + metricName, metricNames.contains(metricName));

        Callable<Long> getPrometheusMetrics = () -> {
            ResponseEntity<String> prometheusResponse = restTemplate.getForEntity(url("/actuator/prometheus"), String.class);
            assertEquals(HttpStatus.OK, prometheusResponse.getStatusCode());
            return Stream.of(prometheusResponse.getBody().split(System.lineSeparator()))
                    .filter(s -> s.contains(metricName.replace('.', '_')))
                    .count();
        };

        Awaitility
                .waitAtMost(Duration.ofMillis(prometheusConfig.step().toMillis() * 2))
                .until(getPrometheusMetrics, Matchers.greaterThan(0L));

    }


}

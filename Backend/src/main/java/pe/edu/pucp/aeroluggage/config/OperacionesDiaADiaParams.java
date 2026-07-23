package pe.edu.pucp.aeroluggage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "operaciones-dia-a-dia")
@PropertySource(value = "classpath:system_params.yml", factory = YamlPropertySourceFactory.class)
public class OperacionesDiaADiaParams {

    private long tickIntervalMs = 5000L;
    private long heartbeatMs = 60000L;
    private long timeoutMs = 90000L;

    public long getTickIntervalMs() {
        return tickIntervalMs;
    }

    public void setTickIntervalMs(final long tickIntervalMs) {
        this.tickIntervalMs = tickIntervalMs;
    }

    public long getHeartbeatMs() {
        return heartbeatMs;
    }

    public void setHeartbeatMs(final long heartbeatMs) {
        this.heartbeatMs = heartbeatMs;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(final long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}

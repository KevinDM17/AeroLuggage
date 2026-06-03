package pe.edu.pucp.aeroluggage.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

    private static final String DB_URL_KEY = "DB_URL";

    private final Dotenv dotenv;

    public DatabaseConfig(final Dotenv dotenv) {
        this.dotenv = dotenv;
    }

    @Primary
    @Bean
    public DataSource dataSource() {
        final String jdbcUrl = dotenv.get(DB_URL_KEY);

        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(2);
        config.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON;");

        return new HikariDataSource(config);
    }
}
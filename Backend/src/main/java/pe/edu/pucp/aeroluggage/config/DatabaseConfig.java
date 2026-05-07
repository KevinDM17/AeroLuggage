package pe.edu.pucp.aeroluggage.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DatabaseConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:aeroluggage.db");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(2);
        
        // Activar WAL mode para mejorar concurrencia en escrituras
        config.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON;");
        
        return new HikariDataSource(config);
    }
}
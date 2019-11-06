package xln.common.config;

import com.github.jasync.r2dbc.mysql.JasyncConnectionFactory;
import com.github.jasync.sql.db.mysql.pool.MySQLConnectionFactory;
import com.github.jasync.sql.db.mysql.util.URLParser;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@Configuration
@EnableR2dbcRepositories
public class R2DBCConfig extends AbstractR2dbcConfiguration {

    @Value("${xln.r2dbc-config.url:mysql://localhost:3306}")
    private String url;

    @Override
    //@Bean
    public ConnectionFactory connectionFactory() {

        return new JasyncConnectionFactory(new MySQLConnectionFactory(
                URLParser.INSTANCE.parseOrDie(url,  URLParser.INSTANCE.getDEFAULT().getCharset())
        ));
    }
}

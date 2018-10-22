package xln.common.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
public class CommonConfig
{

    public static String timeZone;


    @PostConstruct
    public void init()
    {
        int test = 10;
    }

    @Value("${xln-timeZone:Asia/Taipei}")
    public void setTimeZone(String timeZone)
    {
        this.timeZone = timeZone;
    }
}

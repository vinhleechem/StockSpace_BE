package fu.stockspace.stockspace_be;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.TimeZone;

@SpringBootApplication
public class StockSpaceBeApplication {

    @PostConstruct
    public void init() {
        // Set JVM timezone to Ho Chi Minh (GMT+7)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    public static void main(String[] args) {
        SpringApplication.run(StockSpaceBeApplication.class, args);
    }

}

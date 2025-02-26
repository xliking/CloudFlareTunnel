package xlike.top.cloudflare;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 *
 * docker pull xlike0616/cloudflare-tunnel-manager:latest
 *
 *
 * @author Administrator
 */
@SpringBootApplication
public class CloudFlareApplication {

    private static final Logger log = LoggerFactory.getLogger(CloudFlareApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(CloudFlareApplication.class, args);
        log.info("CloudFlareApplication started successfully.");
    }

}

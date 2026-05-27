package it.ivscience.parer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.ImportResource;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, TransactionAutoConfiguration.class})
@ImportResource("classpath:config/spring/api/*.xml")
public class MainApplication {

	public static void main(String[] args) {
		System.exit(SpringApplication.exit(SpringApplication.run(MainApplication.class, args)));
	}

}

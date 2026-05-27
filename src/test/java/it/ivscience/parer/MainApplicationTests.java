package it.ivscience.parer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

@SpringBootTest
@ActiveProfiles("test")
class MainApplicationTests {

	@MockBean S3Client s3Client;
	@MockBean SecretsManagerClient secretsManagerClient;

	@BeforeAll
	static void setup() {
		System.setProperty("aws.region", "us-east-1");
		System.setProperty("aws.accessKeyId", "test");
		System.setProperty("aws.secretAccessKey", "test");
	}

	@Test
	void contextLoads() {
	}

}
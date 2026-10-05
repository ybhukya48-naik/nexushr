package com.zidio.nexushr;

import com.zidio.nexushr.service.email.ResendEmailService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class NexusHrApplicationTests {

    @MockitoBean
    private ResendEmailService resendEmailService;

    @Test
    void contextLoads() {
    }
}

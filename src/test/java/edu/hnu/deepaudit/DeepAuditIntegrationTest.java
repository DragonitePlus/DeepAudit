package edu.hnu.deepaudit;

import edu.hnu.deepaudit.interception.UserContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@SpringBootTest
@AutoConfigureMockMvc
class DeepAuditIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testSqlAuditFlow() throws Exception {
        // This request triggers:
        // 1. Controller sets UserContext (admin_001)
        // 2. Service calls Mapper -> MyBatis
        // 3. SqlAuditInterceptor intercepts
        //    -> Parses SQL (table: sys_user)
        //    -> DLP checks result
        //    -> Prints Log
        
        System.out.println(">>> Starting Integration Test <<<");
        
        mockMvc.perform(get("/api/users")
                .param("operatorId", "test_auditor_007"))
                .andDo(print())
                .andExpect(status().isOk());
                
        // Allow async threads to finish logging
        Thread.sleep(2000);
        
        System.out.println(">>> Integration Test Finished <<<");
    }
}

package edu.hnu.deepaudit.controller;

import edu.hnu.deepaudit.model.SysUser;
import edu.hnu.deepaudit.interception.UserContext;
import edu.hnu.deepaudit.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TestController {

    @Autowired
    private SysUserService sysUserService;

    @GetMapping("/ping")
    public String ping() {
        return "DeepAudit System is Running!";
    }

    /**
     * Simulate a user query with Identity Binding
     */
    @GetMapping("/api/users")
    public List<SysUser> getUsers(@RequestParam(defaultValue = "admin_001") String operatorId) {
        // 1. Simulate Identity Binding (In real world, this happens in a Filter/Interceptor)
        UserContext.setUserId(operatorId);
        
        try {
            // 2. Perform DB Operation (will be intercepted)
            return sysUserService.list();
        } finally {
            // 3. Clean up
            UserContext.clear();
        }
    }
    
    @GetMapping("/api/users/{id}")
    public SysUser getUser(@PathVariable Long id, @RequestParam(defaultValue = "user_002") String operatorId) {
        UserContext.setUserId(operatorId);
        try {
            return sysUserService.getById(id);
        } finally {
            UserContext.clear();
        }
    }
}

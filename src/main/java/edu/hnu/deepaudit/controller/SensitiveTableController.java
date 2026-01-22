package edu.hnu.deepaudit.controller;

import edu.hnu.deepaudit.mapper.sys.SysSensitiveTableMapper;
import edu.hnu.deepaudit.model.SysSensitiveTable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sensitive-tables")
public class SensitiveTableController {

    @Autowired
    private SysSensitiveTableMapper sysSensitiveTableMapper;

    @GetMapping
    public List<SysSensitiveTable> getAll() {
        return sysSensitiveTableMapper.selectList(null);
    }

    @PostMapping
    public String create(@RequestBody SysSensitiveTable table) {
        sysSensitiveTableMapper.insert(table);
        return "Created successfully";
    }

    @PutMapping("/{id}")
    public String update(@PathVariable Long id, @RequestBody SysSensitiveTable table) {
        table.setId(id);
        sysSensitiveTableMapper.updateById(table);
        return "Updated successfully";
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        sysSensitiveTableMapper.deleteById(id);
        return "Deleted successfully";
    }
}

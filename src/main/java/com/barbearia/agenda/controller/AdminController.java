package com.barbearia.agenda.controller;

import com.barbearia.agenda.dto.AdminCreateRequest;
import com.barbearia.agenda.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/registrar")
    public ResponseEntity<?> registrar(@RequestBody AdminCreateRequest req) {
        adminService.criar(req);
        return ResponseEntity.status(201).build();
    }
}



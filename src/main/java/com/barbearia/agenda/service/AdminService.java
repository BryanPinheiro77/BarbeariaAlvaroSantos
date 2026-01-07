package com.barbearia.agenda.service;

import com.barbearia.agenda.dto.AdminCreateRequest;
import com.barbearia.agenda.model.Admin;
import com.barbearia.agenda.repository.AdminRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(
            AdminRepository adminRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Admin criar(AdminCreateRequest req) {

        Admin admin = new Admin();
        admin.setNome(req.nome());
        admin.setEmail(req.email());
        admin.setSenhaHash(passwordEncoder.encode(req.senha()));
        admin.setCriadoEm(LocalDateTime.now());

        return adminRepository.save(admin);
    }
}

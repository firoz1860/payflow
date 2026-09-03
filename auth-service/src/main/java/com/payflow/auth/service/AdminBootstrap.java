package com.payflow.auth.service;
import com.payflow.auth.domain.RoleName;
import com.payflow.auth.domain.User;
import com.payflow.auth.repository.RoleRepository;
import com.payflow.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminName;
    public AdminBootstrap(UserRepository userRepository, RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${payflow.bootstrap.admin-email:}") String adminEmail,
                          @Value("${payflow.bootstrap.admin-password:}") String adminPassword,
                          @Value("${payflow.bootstrap.admin-name:PayFlow Admin}") String adminName) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminName = adminName;
    }
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.info("No bootstrap admin configured; set PAYFLOW_ADMIN_EMAIL and "
                    + "PAYFLOW_ADMIN_PASSWORD to create one");
            return;
        }
        if (adminPassword.length() < 12) {
            throw new IllegalStateException(
                    "PAYFLOW_ADMIN_PASSWORD must be at least 12 characters");
        }
        if (userRepository.existsByEmailIgnoreCase(adminEmail)) {
            log.debug("Bootstrap admin already exists, leaving it untouched");
            return;
        }
        User admin = new User(adminEmail, passwordEncoder.encode(adminPassword), adminName, null);
        admin.addRole(roleRepository.findByName(RoleName.PAYFLOW_ADMIN)
                .orElseThrow(() -> new IllegalStateException("PAYFLOW_ADMIN role not seeded")));
        admin.verifyEmail();
        userRepository.save(admin);
        log.warn("Created bootstrap platform admin {}. Change this password immediately.", adminEmail);
    }
}

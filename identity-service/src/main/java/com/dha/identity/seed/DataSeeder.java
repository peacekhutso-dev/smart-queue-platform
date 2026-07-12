package com.dha.identity.seed;

import com.dha.identity.entity.User;
import com.dha.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Demo accounts for the admin dashboard — this system has no admin sign-up flow, so
 * STAFF/ADMIN users only ever come from this seeder. Change these passwords before
 * any real deployment.
 *
 * assignedBranchId is hardcoded to 1 (the first branch branch-service's own seeder
 * creates) rather than looked up — Identity and Branch are separate databases now,
 * so this is a deliberate, documented coupling to "seed data is seeded in a known
 * order," not a real cross-service lookup. Revisit once branch assignment becomes a
 * user-editable admin action instead of seed data.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private static final Long DEFAULT_BRANCH_ID = 1L;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.findByEmail("staff@dha.gov.za").isEmpty()) {
            userRepository.save(User.builder()
                    .firstName("Naledi").lastName("Pandor")
                    .email("staff@dha.gov.za")
                    .password(passwordEncoder.encode("Staff@12345"))
                    .role(User.UserRole.STAFF)
                    .assignedBranchId(DEFAULT_BRANCH_ID)
                    .isVerified(true).isIdVerified(true).isActive(true)
                    .build());
            log.info("Seeded demo STAFF account staff@dha.gov.za");
        }

        if (userRepository.findByEmail("admin@dha.gov.za").isEmpty()) {
            userRepository.save(User.builder()
                    .firstName("Thabo").lastName("Mbeki")
                    .email("admin@dha.gov.za")
                    .password(passwordEncoder.encode("Admin@12345"))
                    .role(User.UserRole.ADMIN)
                    .assignedBranchId(DEFAULT_BRANCH_ID)
                    .isVerified(true).isIdVerified(true).isActive(true)
                    .build());
            log.info("Seeded demo ADMIN account admin@dha.gov.za");
        }
    }
}

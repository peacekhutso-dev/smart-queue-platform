package com.dha.queue.config;

import com.dha.queue.entity.Branch;
import com.dha.queue.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final BranchRepository branchRepository;

    @Override
    public void run(String... args) {
        if (branchRepository.count() == 0) {
            log.info("Seeding DHA branch data...");
            branchRepository.saveAll(List.of(
                    Branch.builder()
                            .name("Pretoria Central DHA")
                            .address("230 Johannes Ramokhoase St, Pretoria")
                            .province("Gauteng").city("Pretoria").suburb("Central")
                            .latitude(-25.7449).longitude(28.1878)
                            .phoneNumber("012 324 1860")
                            .openingTime(LocalTime.of(8, 0)).closingTime(LocalTime.of(16, 30))
                            .maxCapacity(150).activeCounters(8).avgServiceTimeMinutes(7)
                            .hasWifi(true).hasParking(true).isAccessible(true)
                            .build(),
                    Branch.builder()
                            .name("Johannesburg Central DHA")
                            .address("77 Harrison St, Johannesburg")
                            .province("Gauteng").city("Johannesburg").suburb("CBD")
                            .latitude(-26.2041).longitude(28.0473)
                            .phoneNumber("011 836 3228")
                            .openingTime(LocalTime.of(8, 0)).closingTime(LocalTime.of(16, 30))
                            .maxCapacity(200).activeCounters(12).avgServiceTimeMinutes(8)
                            .hasWifi(false).hasParking(false).isAccessible(true)
                            .build(),
                    Branch.builder()
                            .name("Cape Town Barrack Street DHA")
                            .address("Barrack St, Cape Town")
                            .province("Western Cape").city("Cape Town").suburb("CBD")
                            .latitude(-33.9249).longitude(18.4241)
                            .phoneNumber("021 462 4970")
                            .openingTime(LocalTime.of(8, 0)).closingTime(LocalTime.of(16, 0))
                            .maxCapacity(120).activeCounters(7).avgServiceTimeMinutes(9)
                            .hasWifi(true).hasParking(false).isAccessible(true)
                            .build(),
                    Branch.builder()
                            .name("Durban Smith Street DHA")
                            .address("Smith St, Durban CBD")
                            .province("KwaZulu-Natal").city("Durban").suburb("CBD")
                            .latitude(-29.8587).longitude(31.0218)
                            .phoneNumber("031 336 1100")
                            .openingTime(LocalTime.of(8, 0)).closingTime(LocalTime.of(16, 0))
                            .maxCapacity(180).activeCounters(10).avgServiceTimeMinutes(7)
                            .hasWifi(false).hasParking(true).isAccessible(true)
                            .build(),
                    Branch.builder()
                            .name("Soweto DHA")
                            .address("Dlamini, Soweto, Johannesburg")
                            .province("Gauteng").city("Johannesburg").suburb("Soweto")
                            .latitude(-26.2677).longitude(27.8587)
                            .phoneNumber("011 933 2442")
                            .openingTime(LocalTime.of(8, 0)).closingTime(LocalTime.of(16, 30))
                            .maxCapacity(100).activeCounters(5).avgServiceTimeMinutes(10)
                            .hasWifi(false).hasParking(true).isAccessible(false)
                            .build(),
                    Branch.builder()
                            .name("Bellville DHA")
                            .address("Voortrekker Rd, Bellville")
                            .province("Western Cape").city("Cape Town").suburb("Bellville")
                            .latitude(-33.9001).longitude(18.6301)
                            .phoneNumber("021 959 6800")
                            .openingTime(LocalTime.of(8, 0)).closingTime(LocalTime.of(16, 0))
                            .maxCapacity(80).activeCounters(4).avgServiceTimeMinutes(8)
                            .hasWifi(true).hasParking(true).isAccessible(true)
                            .build()
            ));
            log.info("Seeded {} DHA branches.", branchRepository.count());
        }
    }
}
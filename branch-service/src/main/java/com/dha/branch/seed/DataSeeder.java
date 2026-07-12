package com.dha.branch.seed;

import com.dha.branch.entity.Branch;
import com.dha.branch.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
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
            List<Branch> branches = new ArrayList<>();

            branches.add(Branch.builder()
                    .name("Pretoria Central DHA")
                    .address("230 Johannes Ramokhoase St, Pretoria")
                    .province("Gauteng").city("Pretoria").suburb("Central")
                    .latitude(-25.7449).longitude(28.1878)
                    .phoneNumber("012 324 1860")
                    .openingTime(LocalTime.of(8, 0)).closingTime(LocalTime.of(16, 30))
                    .maxCapacity(150).activeCounters(8).avgServiceTimeMinutes(7)
                    .hasWifi(true).hasParking(true).isAccessible(true)
                    .build());
            branches.add(Branch.builder()
                    .name("Johannesburg Central DHA")
                    .address("77 Harrison St, Johannesburg")
                    .province("Gauteng").city("Johannesburg").suburb("CBD")
                    .latitude(-26.2041).longitude(28.0473)
                    .phoneNumber("011 836 3228")
                    .openingTime(LocalTime.of(8, 0)).closingTime(LocalTime.of(16, 30))
                    .maxCapacity(200).activeCounters(12).avgServiceTimeMinutes(8)
                    .hasWifi(false).hasParking(false).isAccessible(true)
                    .build());
            branches.add(Branch.builder()
                    .name("Durban Smith Street DHA")
                    .address("Smith St, Durban CBD")
                    .province("KwaZulu-Natal").city("Durban").suburb("CBD")
                    .latitude(-29.8587).longitude(31.0218)
                    .phoneNumber("031 336 1100")
                    .openingTime(LocalTime.of(8, 0)).closingTime(LocalTime.of(16, 0))
                    .maxCapacity(180).activeCounters(10).avgServiceTimeMinutes(7)
                    .hasWifi(false).hasParking(true).isAccessible(true)
                    .build());
            branches.add(Branch.builder()
                    .name("Soweto DHA")
                    .address("Dlamini, Soweto, Johannesburg")
                    .province("Gauteng").city("Johannesburg").suburb("Soweto")
                    .latitude(-26.2677).longitude(27.8587)
                    .phoneNumber("011 933 2442")
                    .openingTime(LocalTime.of(8, 0)).closingTime(LocalTime.of(16, 30))
                    .maxCapacity(100).activeCounters(5).avgServiceTimeMinutes(10)
                    .hasWifi(false).hasParking(true).isAccessible(false)
                    .build());

            // ── Western Cape: Cape Town Metro ──────────────────────────────────
            branches.add(wcBranch("Cape Town CBD DHA", "Faircape Building, 56 Barrack Street, Cape Town",
                    "Cape Town", "CBD", -33.9275937, 18.4221106, 150, 9));
            branches.add(wcBranch("Bellville DHA", "ABSA Building, Voortrekker Road, Bellville",
                    "Cape Town", "Bellville", -33.9031673, 18.6412629, 110, 7));
            branches.add(wcBranch("Khayelitsha DHA", "Corner of Tsolo and Mzala Street, Khayelitsha",
                    "Cape Town", "Khayelitsha", -34.0484657, 18.6733047, 120, 6));
            branches.add(wcBranch("Mitchells Plain DHA", "Alpha Street, Beacon Valley, Mitchells Plain",
                    "Cape Town", "Mitchells Plain", -34.0454179, 18.6199238, 110, 6));
            branches.add(wcBranch("Nyanga DHA", "New Eisleben Road & Miller Street, Gugulethu",
                    "Cape Town", "Gugulethu", -33.9884067, 18.591453, 100, 5));
            branches.add(wcBranch("Wynberg DHA", "18-24 Maynard Road, Wynberg",
                    "Cape Town", "Wynberg", -34.0046604, 18.4686392, 100, 6));

            // ── Western Cape: Winelands & West Coast ───────────────────────────
            branches.add(wcBranch("Paarl DHA", "33 Market Street, Paarl",
                    "Paarl", "Central", -33.7382618, 18.9677634, 80, 5));
            branches.add(wcBranch("Stellenbosch DHA", "143 Bird Street, Stellenbosch",
                    "Stellenbosch", "Central", -33.9218885, 18.8539278, 70, 4));
            branches.add(wcBranch("Malmesbury DHA", "5 Piet Retief Street, Malmesbury",
                    "Malmesbury", "Central", -33.4611635, 18.7305691, 60, 3));
            branches.add(wcBranch("Vredendal DHA", "16 Matzikama Street, Vredendal",
                    "Vredendal", "Central", -31.6621514, 18.5071974, 50, 3));

            // ── Western Cape: Overberg & Karoo ─────────────────────────────────
            branches.add(wcBranch("Caledon DHA", "Jo-El Building, 22 Prince Alfred Road, Caledon",
                    "Caledon", "Central", -34.2317139, 19.4246918, 50, 3));
            branches.add(wcBranch("Grabouw DHA", "Old Clinic Building, Old Cape Road, Grabouw",
                    "Grabouw", "Central", -34.1533127, 19.010118, 50, 3));
            branches.add(wcBranch("Worcester DHA", "64 High Street, Worcester",
                    "Worcester", "Central", -33.6429837, 19.4467885, 70, 4));
            branches.add(wcBranch("Beaufort West DHA", "49 Donkin Street, Beaufort West",
                    "Beaufort West", "Central", -32.3476326, 22.5826894, 50, 3));

            // ── Western Cape: Garden Route ──────────────────────────────────────
            branches.add(wcBranch("George DHA", "109 York Street, George",
                    "George", "Central", -33.9579138, 22.4566501, 80, 5));
            branches.add(wcBranch("Oudtshoorn DHA", "57 Voortrekker Road, Oudtshoorn",
                    "Oudtshoorn", "Central", -33.5911945, 22.1915911, 60, 4));

            branchRepository.saveAll(branches);
            log.info("Seeded {} DHA branches.", branchRepository.count());
        }
    }

    /**
     * Western Cape branches share opening hours and amenity defaults (not published by DHA,
     * so these are operational placeholders — tune once real per-branch data is available).
     */
    private Branch wcBranch(String name, String address, String city, String suburb,
                             double lat, double lng, int maxCapacity, int activeCounters) {
        return Branch.builder()
                .name(name)
                .address(address)
                .province("Western Cape").city(city).suburb(suburb)
                .latitude(lat).longitude(lng)
                .openingTime(LocalTime.of(8, 0)).closingTime(LocalTime.of(16, 0))
                .maxCapacity(maxCapacity).activeCounters(activeCounters).avgServiceTimeMinutes(8)
                .hasWifi(true).hasParking(true).isAccessible(true)
                .build();
    }
}

package com.dha.payment.repository;

import com.dha.payment.entity.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {
    boolean existsByProviderEventId(String providerEventId);
}

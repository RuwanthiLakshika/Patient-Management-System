package com.pm.billing_service.controller;

import com.pm.billing_service.grpc.BillingGrpcService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/billing")
public class BillingController {
    private final BillingGrpcService billingGrpcService;

    public BillingController(BillingGrpcService billingGrpcService) {
        this.billingGrpcService = billingGrpcService;
    }

    @GetMapping("/accounts")
    public List<BillingGrpcService.BillingAccount> getAccounts() {
        return billingGrpcService.getAccounts();
    }
}

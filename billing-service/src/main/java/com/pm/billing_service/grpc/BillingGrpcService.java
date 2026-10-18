package com.pm.billing_service.grpc;

import billing.BillingRequest;
import billing.BillingResponse;
import billing.BillingServiceGrpc.BillingServiceImplBase;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@GrpcService
public class BillingGrpcService extends BillingServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(
            BillingGrpcService.class);
    private final List<BillingAccount> accounts = new CopyOnWriteArrayList<>();

    @Override
    public void createBillingAccount(BillingRequest billingRequest,
                                     StreamObserver<BillingResponse> responseObserver) {

        log.info("createBillingAccount request received {}", billingRequest.toString());

        // Business logic - e.g save to database, perform calculates etc

        String accountId = "BA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        accounts.add(new BillingAccount(accountId, billingRequest.getPatientId(),
                billingRequest.getName(), billingRequest.getEmail(), "ACTIVE"));

        BillingResponse response = BillingResponse.newBuilder()
                .setAccountId(accountId)
                .setStatus("ACTIVE")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public List<BillingAccount> getAccounts() {
        return List.copyOf(accounts);
    }

    public record BillingAccount(String accountId, String patientId, String patientName,
                                 String email, String status) {}
}
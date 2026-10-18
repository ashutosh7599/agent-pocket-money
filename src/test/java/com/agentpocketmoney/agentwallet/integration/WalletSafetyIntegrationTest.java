package com.agentpocketmoney.agentwallet.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentpocketmoney.agentwallet.dto.SettlementReportRow;
import com.agentpocketmoney.agentwallet.dto.WalletPolicyRequest;
import com.agentpocketmoney.agentwallet.mock.webhook.HmacSignatureVerifier;
import com.agentpocketmoney.agentwallet.model.wallet.*;
import com.agentpocketmoney.agentwallet.repository.PaymentRepository;
import com.agentpocketmoney.agentwallet.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker=true)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties="logging.level.com.agentpocketmoney.agentwallet=WARN")
class WalletSafetyIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:15-alpine");
    private static final String WEBHOOK_SECRET="integration-test-only-signing-secret";
    @DynamicPropertySource static void configureDatabase(DynamicPropertyRegistry properties){
        properties.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username",POSTGRES::getUsername);
        properties.add("spring.datasource.password",POSTGRES::getPassword);
        properties.add("WEBHOOK_SIGNING_SECRET",()->WEBHOOK_SECRET);
    }
    @Autowired WalletService wallets;
    @Autowired PaymentService payments;
    @Autowired WalletPolicyService policies;
    @Autowired PaymentRepository paymentRepository;
    @Autowired MockWebhookService webhooks;
    @Autowired HmacSignatureVerifier signatures;
    @Autowired ObjectMapper json;
    @Autowired ReconciliationService reconciliation;

    @Test void concurrentAuthorizationsCannotOverspendWallet(){
        UUID wallet=wallets.createWallet().id();
        wallets.topUp(wallet,"load-test-fund",500);
        allowMerchant(wallet);
        ExecutorService pool=Executors.newFixedThreadPool(20);
        try{
            List<Future<PaymentStatus>> results=new ArrayList<>();
            for(int i=0;i<100;i++){
                int number=i;
                results.add(pool.submit(()->{
                    Payment payment=payments.create(wallet,"load-payment-"+number,"merchant-load",10,"OTHER","load test");
                    return payments.authorize(wallet,payment.getId()).getStatus();
                }));
            }
            long authorized=0;
            for(Future<PaymentStatus> result:results)if(result.get(30,TimeUnit.SECONDS)==PaymentStatus.AUTHORIZED)authorized++;
            var balance=wallets.getWallet(wallet);
            assertThat(authorized).isEqualTo(50);
            assertThat(balance.availableBalanceMinorUnits()).isZero();
            assertThat(balance.balanceMinorUnits()).isEqualTo(500);
            assertThat(balance.availableBalanceMinorUnits()).isGreaterThanOrEqualTo(0);
        }catch(Exception e){throw new AssertionError("Concurrent wallet authorization failed.",e);}
        finally{pool.shutdownNow();}
    }

    @Test void concurrentRetriesCreateOnlyOnePaymentAndOneTopUp(){
        UUID wallet=wallets.createWallet().id();
        ExecutorService pool=Executors.newFixedThreadPool(12);
        try{
            List<Future<UUID>> topups=new ArrayList<>();
            for(int i=0;i<12;i++)topups.add(pool.submit(()->wallets.topUp(wallet,"same-topup-key",500).id()));
            Set<UUID> topupIds=new HashSet<>();for(Future<UUID> result:topups)topupIds.add(result.get(30,TimeUnit.SECONDS));
            assertThat(topupIds).hasSize(1);
            allowMerchant(wallet);
            List<Future<UUID>> creates=new ArrayList<>();
            for(int i=0;i<12;i++)creates.add(pool.submit(()->payments.create(wallet,"same-payment-key","merchant-load",50,"OTHER","idempotency test").getId()));
            Set<UUID> paymentIds=new HashSet<>();for(Future<UUID> result:creates)paymentIds.add(result.get(30,TimeUnit.SECONDS));
            assertThat(paymentIds).hasSize(1);
            assertThat(paymentRepository.findByWalletIdAndIdempotencyKey(wallet,"same-payment-key")).isPresent();
            UUID paymentId=paymentIds.iterator().next();
            List<Future<PaymentStatus>> authorizations=new ArrayList<>();
            for(int i=0;i<12;i++)authorizations.add(pool.submit(()->payments.authorize(wallet,paymentId).getStatus()));
            for(Future<PaymentStatus> result:authorizations)assertThat(result.get(30,TimeUnit.SECONDS)).isEqualTo(PaymentStatus.AUTHORIZED);
            assertThat(wallets.getWallet(wallet).heldBalanceMinorUnits()).isEqualTo(50);
            assertThat(wallets.getWallet(wallet).balanceMinorUnits()).isEqualTo(500);
        }catch(Exception e){throw new AssertionError("Concurrent idempotent retry failed.",e);}
        finally{pool.shutdownNow();}
    }

    @Test void signedWebhookReplayAndReconciliationBreaksPersist(){
        UUID wallet=wallets.createWallet().id();wallets.topUp(wallet,"reconcile-fund",1000);allowMerchant(wallet);
        Payment payment=payments.create(wallet,"reconcile-payment","merchant-load",200,"OTHER","reconcile test");
        payments.authorize(wallet,payment.getId());payments.capture(wallet,payment.getId(),"reconcile-capture");
        String body;
        try{body=json.writeValueAsString(Map.of("eventId",UUID.randomUUID(),"eventType","payment.updated",
                "paymentId",payment.getId(),"status","CAPTURED","occurredAt",java.time.Instant.now()));}
        catch(Exception e){throw new AssertionError(e);}
        String signature=signatures.sign(body);
        assertThat(webhooks.receive(body,signature).outcome()).isEqualTo(MockWebhookService.Outcome.ACCEPTED);
        assertThat(webhooks.receive(body,signature).outcome()).isEqualTo(MockWebhookService.Outcome.DUPLICATE);
        var batch=reconciliation.reconcile(List.of(new SettlementReportRow(payment.getId(),201,"CAPTURED")));
        assertThat(batch.getBreakCount()).isEqualTo(1);
        assertThat(reconciliation.listBreaks(batch.getId())).hasSize(1);
    }

    private void allowMerchant(UUID wallet){
        policies.replace(wallet,new WalletPolicyRequest(List.of("merchant-load"),10000L,100000L,100000L,
                null,null,null,List.of()));
    }
}

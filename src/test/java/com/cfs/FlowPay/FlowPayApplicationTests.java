package com.cfs.FlowPay;

import com.cfs.FlowPay.merchant.MerchantDtos;
import com.cfs.FlowPay.merchant.Merchant;
import com.cfs.FlowPay.merchant.MerchantRepository;
import com.cfs.FlowPay.merchant.MerchantService;
import com.cfs.FlowPay.order.OrderDtos;
import com.cfs.FlowPay.order.OrderService;
import com.cfs.FlowPay.order.PaymentOrder;
import com.cfs.FlowPay.payment.Payment;
import com.cfs.FlowPay.payment.PaymentDtos;
import com.cfs.FlowPay.payment.PaymentRepository;
import com.cfs.FlowPay.payment.PaymentService;
import com.cfs.FlowPay.bank.BankJobs;
import com.cfs.FlowPay.refund.RefundDtos;
import com.cfs.FlowPay.refund.RefundService;
import com.cfs.FlowPay.settelment.SettlementService;
import com.cfs.FlowPay.transaction.LedgerEntry;
import com.cfs.FlowPay.transaction.LedgerEntryRepository;
import com.cfs.FlowPay.auth.AuthDtos;
import com.cfs.FlowPay.auth.AuthService;
import com.cfs.FlowPay.auth.DashboardUserDtos;
import com.cfs.FlowPay.auth.DashboardUserService;
import com.cfs.FlowPay.common.ApiException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.time.Instant;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:flowpay;MODE=MySQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.jobs-enabled=false",
		"app.kafka-enabled=false"
})
class FlowPayApplicationTests {

	@Autowired
	private MerchantService merchantService;

	@Autowired
	private MerchantRepository merchantRepository;

	@Autowired
	private OrderService orderService;

	@Autowired
	private PaymentService paymentService;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private LedgerEntryRepository ledgerRepository;

	@Autowired
	private RefundService refundService;

	@Autowired
	private SettlementService settlementService;

	@Autowired
	private AuthService authService;

	@Autowired
	private DashboardUserService dashboardUserService;

	@Test
	void contextLoads() {
	}

	@Test
	void registrationReturnsAnApiKeyThatCanAuthenticate() {
		MerchantDtos.Registered registered = merchantService.register(
				new MerchantDtos.Register("Test merchant", "merchant@example.com"));

		Assertions.assertTrue(registered.apiKey().startsWith("fp_test_"));
		Assertions.assertEquals(
				registered.merchant().id,
				merchantService.authenticate(registered.apiKey()).id);
	}

	@Test
	void dashboardRegistrationAndLoginIssueMerchantScopedSessions() {
		AuthDtos.Registered registered = authService.register(
				new AuthDtos.Register("Auth test merchant", "auth@example.com", "correct-horse-battery"));

		AuthDtos.Authenticated login = authService.login(
				new AuthDtos.Login("AUTH@example.com", "correct-horse-battery"));

		Assertions.assertEquals("OWNER", registered.role());
		Assertions.assertEquals(registered.merchant().id, login.merchant().id);
		Assertions.assertEquals(registered.merchant().id,
				merchantService.authenticate(login.sessionToken()).id);
	}

	@Test
	void viewerSessionCannotMutateMerchantData() {
		AuthDtos.Registered registered = authService.register(
				new AuthDtos.Register("Role test merchant", "role-owner@example.com", "correct-horse-battery"));
		dashboardUserService.create(registered.merchant().id,
				new DashboardUserDtos.Create("role-viewer@example.com", "viewer-password-123", "VIEWER"));
		AuthDtos.Authenticated viewer = authService.login(
				new AuthDtos.Login("role-viewer@example.com", "viewer-password-123"));

		Assertions.assertEquals("VIEWER", viewer.role());
		Assertions.assertThrows(ApiException.class,
				() -> merchantService.requireMutation(viewer.sessionToken()));
		Assertions.assertThrows(ApiException.class,
				() -> authService.rotateApiKey(viewer.sessionToken(), "viewer-password-123"));
	}

	@Test
	void ownerCanRotateCredentialsAfterPasswordConfirmation() {
		AuthDtos.Registered registered = authService.register(
				new AuthDtos.Register("Credential test merchant", "credentials@example.com", "correct-horse-battery"));
		AuthDtos.Authenticated login = authService.login(
				new AuthDtos.Login("credentials@example.com", "correct-horse-battery"));

		AuthDtos.CredentialRotation newApiKey = authService.rotateApiKey(
				login.sessionToken(), "correct-horse-battery");
		Assertions.assertNotEquals(registered.apiKey(), newApiKey.value());
		Assertions.assertEquals(registered.merchant().id,
				merchantService.authenticate(newApiKey.value()).id);
		Assertions.assertThrows(ApiException.class,
				() -> merchantService.authenticate(registered.apiKey()));

		AuthDtos.CredentialRotation newWebhookSecret = authService.rotateWebhookSecret(
				login.sessionToken(), "correct-horse-battery");
		Assertions.assertNotEquals(registered.webhookSecret(), newWebhookSecret.value());
		Assertions.assertEquals(newWebhookSecret.value(),
				merchantRepository.findById(registered.merchant().id).orElseThrow().webhookSecret);
	}

	@Test
	void incorrectPasswordCannotRotateCredentials() {
		authService.register(new AuthDtos.Register(
				"Password test merchant", "password-rotate@example.com", "correct-horse-battery"));
		AuthDtos.Authenticated login = authService.login(
				new AuthDtos.Login("password-rotate@example.com", "correct-horse-battery"));

		Assertions.assertThrows(ApiException.class,
				() -> authService.rotateApiKey(login.sessionToken(), "not-the-password"));
	}

	@Test
	void delayedPaymentJobCompletesSuccessfullyWhenDue() {
		Merchant merchant = merchantService.register(
				new MerchantDtos.Register("Delayed payment merchant", "delayed-payment@example.com")).merchant();
		PaymentOrder order = orderService.create(merchant.id,
				new OrderDtos.Create(10_000, "delayed-payment"));
		Payment payment = paymentService.create(merchant.id, "delayed-payment-idempotency",
				new PaymentDtos.Create(order.id, PaymentDtos.Method.UPI, PaymentDtos.Scenario.DELAYED_SUCCESS));
		payment = paymentService.process(merchant.id, payment.id);
		Assertions.assertEquals("PENDING", payment.status);
		payment.resolveAt = Instant.now().minusSeconds(1);
		paymentRepository.save(payment);

		new BankJobs(paymentRepository, paymentService).completeDelayed();

		Assertions.assertEquals("SUCCESS", paymentRepository.findById(payment.id).orElseThrow().status);
	}

	@Test
	void timeoutPaymentJobFailsWhenDue() {
		Merchant merchant = merchantService.register(
				new MerchantDtos.Register("Timeout payment merchant", "timeout-payment@example.com")).merchant();
		PaymentOrder order = orderService.create(merchant.id,
				new OrderDtos.Create(10_000, "timeout-payment"));
		Payment payment = paymentService.create(merchant.id, "timeout-payment-idempotency",
				new PaymentDtos.Create(order.id, PaymentDtos.Method.CARD, PaymentDtos.Scenario.TIMEOUT));
		payment = paymentService.process(merchant.id, payment.id);
		Assertions.assertEquals("PENDING", payment.status);
		payment.resolveAt = Instant.now().minusSeconds(1);
		paymentRepository.save(payment);

		new BankJobs(paymentRepository, paymentService).completeDelayed();

		Assertions.assertEquals("FAILED", paymentRepository.findById(payment.id).orElseThrow().status);
		Assertions.assertEquals(0, merchantRepository.findById(merchant.id).orElseThrow().balance);
	}

	@Test
	void settlementCannotBeCreatedWithoutAvailableBalance() {
		Merchant merchant = merchantService.register(
				new MerchantDtos.Register("Empty settlement merchant", "empty-settlement@example.com"))
				.merchant();

		Assertions.assertThrows(ApiException.class,
				() -> settlementService.create(merchant.id, "empty-settlement"));
	}

	@Test
	void successfulPaymentCreditsNetAmountToMerchantOnlyOnce() {
		Merchant merchant = merchantService.register(
				new MerchantDtos.Register("Balance test merchant", "balance@example.com"))
				.merchant();
		PaymentOrder order = orderService.create(
				merchant.id, new OrderDtos.Create(10_000, "balance-test"));
		Payment payment = paymentService.create(
				merchant.id,
				"payment-balance-test",
				new PaymentDtos.Create(
						order.id,
						PaymentDtos.Method.UPI,
						PaymentDtos.Scenario.SUCCESS));

		Payment processed = paymentService.process(merchant.id, payment.id);
		paymentService.process(merchant.id, payment.id);
		refundService.create(
				merchant.id,
				"refund-balance-test",
				new RefundDtos.Create(payment.id, 10_000));

		Merchant updatedMerchant = merchantRepository.findById(merchant.id).orElseThrow();
		Assertions.assertEquals("SUCCESS", processed.status);
		Assertions.assertEquals(0, updatedMerchant.balance);

		List<LedgerEntry> entries =
				ledgerRepository.findByMerchantIdOrderByCreatedAtDesc(merchant.id);
		Assertions.assertEquals(2, entries.size());
		Assertions.assertTrue(entries.stream().anyMatch(entry ->
				entry.type.equals("PAYMENT")
						&& entry.amount == 9_800
						&& entry.balanceAfter == 9_800));
		Assertions.assertTrue(entries.stream().anyMatch(entry ->
				entry.type.equals("REFUND")
						&& entry.amount == -9_800
						&& entry.balanceAfter == 0));
	}

}

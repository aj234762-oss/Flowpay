package com.cfs.FlowPay.Payment;

import com.cfs.FlowPay.bank.MockBankService;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class MockBankServiceTest {
    private final MockBankService bank = new MockBankService();

    @Test void successAndFailureResolveImmediately() {
        assertEquals("SUCCESS", bank.initiate("SUCCESS").status());
        assertEquals("FAILED", bank.initiate("FAILURE").status());
    }

    @Test void delayedAndTimeoutHaveDistinctTerminalResults() {
        var delayed = bank.initiate("DELAYED_SUCCESS");
        var timeout = bank.initiate("TIMEOUT");
        assertEquals("PENDING", delayed.status());
        assertEquals("PENDING", timeout.status());
        assertEquals("SUCCESS", bank.check("DELAYED_SUCCESS", Instant.now().minusSeconds(1)));
        assertEquals("FAILED", bank.check("TIMEOUT", Instant.now().minusSeconds(1)));
        assertEquals("PENDING", bank.check("TIMEOUT", Instant.now().plusSeconds(30)));
    }

    @Test void feeUsesWholeMinorUnits() {
        assertEquals(20, PaymentService.fee(1000));
        assertEquals(1, PaymentService.fee(1));
    }
}

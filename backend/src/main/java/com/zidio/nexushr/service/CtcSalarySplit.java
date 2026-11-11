package com.zidio.nexushr.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Every employee uses the same split of one month of CTC.
 * Basic 61, HRA 30, conveyance 3.6, medical 2.8, other allowance 2.6.
 * Incentives are not part of this 100 percent.
 * The month is then paid in proportion to actual working hours.
 */
public final class CtcSalarySplit {

    private static final BigDecimal BASIC = new BigDecimal("0.61");
    private static final BigDecimal HRA = new BigDecimal("0.30");
    private static final BigDecimal CONVEYANCE = new BigDecimal("0.036");
    private static final BigDecimal MEDICAL = new BigDecimal("0.028");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private CtcSalarySplit() {
    }

    public record Parts(
            BigDecimal basic,
            BigDecimal hra,
            BigDecimal conveyance,
            BigDecimal medical,
            BigDecimal otherAllowance,
            BigDecimal earnings
    ) {
    }

    public static Parts split(BigDecimal monthlyCtc, int workedMinutes, int expectedMinutes) {
        BigDecimal monthly = monthlyCtc == null ? ZERO : monthlyCtc.max(ZERO);
        BigDecimal ratio = BigDecimal.ONE;
        if (expectedMinutes <= 0) {
            ratio = ZERO;
        } else {
            int paid = Math.max(0, Math.min(workedMinutes, expectedMinutes));
            ratio = BigDecimal.valueOf(paid)
                    .divide(BigDecimal.valueOf(expectedMinutes), 8, RoundingMode.HALF_UP);
        }
        BigDecimal payable = monthly.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
        BigDecimal basic = share(monthly, ratio, BASIC);
        BigDecimal hra = share(monthly, ratio, HRA);
        BigDecimal conveyance = share(monthly, ratio, CONVEYANCE);
        BigDecimal medical = share(monthly, ratio, MEDICAL);
        BigDecimal other = payable
                .subtract(basic)
                .subtract(hra)
                .subtract(conveyance)
                .subtract(medical)
                .setScale(2, RoundingMode.HALF_UP);
        if (other.signum() < 0) {
            other = ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return new Parts(basic, hra, conveyance, medical, other, payable);
    }

    private static BigDecimal share(BigDecimal monthly, BigDecimal ratio, BigDecimal rate) {
        return monthly.multiply(rate).multiply(ratio).setScale(2, RoundingMode.HALF_UP);
    }
}

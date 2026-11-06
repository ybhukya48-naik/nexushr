package com.zidio.nexushr.service;

public final class CompanyPasswords {

    private CompanyPasswords() {
    }

    public static String initialPassword(String companyCode) {
        if (companyCode != null && companyCode.equalsIgnoreCase("GORLE")) {
            return "Gorle@1234";
        }
        return "Cyond@1234";
    }

    public static String emailDomain(String companyCode) {
        if (companyCode != null && companyCode.equalsIgnoreCase("GORLE")) {
            return "@gorle.com";
        }
        return "@cyond.com";
    }
}

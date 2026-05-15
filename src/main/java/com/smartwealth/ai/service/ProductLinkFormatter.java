package com.smartwealth.ai.service;

import org.springframework.stereotype.Service;

@Service
public class ProductLinkFormatter {

    public String toDisplayName(String productName) {
        return "[BUY] " + productName;
    }

    public boolean purchaseLinkEnabled() {
        return true;
    }
}

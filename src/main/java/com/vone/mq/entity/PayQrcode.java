package com.vone.mq.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;

@Entity
public class PayQrcode {
    @Id
    @SequenceGenerator(
            name = "pay_qrcode_sequence",
            sequenceName = "PAY_QRCODE_SEQ",
            allocationSize = 50)
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "pay_qrcode_sequence")
    private Long id;

    private String payUrl;
    private double price;
    private int type;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPayUrl() {
        return payUrl;
    }

    public void setPayUrl(String payUrl) {
        this.payUrl = payUrl;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }
}

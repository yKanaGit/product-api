package com.example.product;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

import java.math.BigDecimal;

@Entity
public class Product extends PanacheEntity {

    @Column(nullable = false)
    public String name;

    public String description;

    @Column(precision = 10, scale = 2)
    public BigDecimal price;

    public Product() {
    }

    public Product(String name, String description, BigDecimal price) {
        this.name = name;
        this.description = description;
        this.price = price;
    }
}

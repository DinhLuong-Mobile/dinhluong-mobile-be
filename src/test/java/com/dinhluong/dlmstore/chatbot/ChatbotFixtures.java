package com.dinhluong.dlmstore.chatbot;

import com.dinhluong.dlmstore.entity.*;
import com.dinhluong.dlmstore.entity.Enums.*;
import com.dinhluong.dlmstore.repository.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.util.List;

/** Synthetic data only; this configuration is never component-scanned into the application. */
@TestConfiguration
public class ChatbotFixtures {
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    @Bean ApplicationRunner syntheticChatbotData(RoleRepository roles, UserRepository users,
            BrandRepository brands, CategoryRepository categories, ProductRepository products,
            PlatformTransactionManager manager, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        return args -> {
            try (var connection = jdbc.getDataSource().getConnection()) {
                if (!connection.getMetaData().getURL().startsWith("jdbc:h2:mem:")) {
                    throw new IllegalStateException("Synthetic fixtures require an isolated in-memory database");
                }
            }
            // These legacy String converters use MySQL's JSON parameter coercion, unlike H2.
            for (String column : List.of("available_rams", "available_roms", "specifications_json")) {
                jdbc.execute("alter table products alter column " + column + " varchar(10000)");
            }
            new TransactionTemplate(manager).executeWithoutResult(status -> {
            var role = new Roles(); role.setName("USER"); roles.save(role);
            users.save(Users.builder().email("fixture@example.test").password("unused-test-password")
                    .fullName("Synthetic fixture").role(role).isEnabled(true).build());
            var brand = new Brand(); brand.setName("Samsung"); brand.setSlug("fixture-samsung"); brands.save(brand);
            var category = new Category(); category.setName("Fixture phones"); category.setSlug("fixture-phones"); categories.save(category);
            var group = new SpecGroup(); group.setName("Fixture specifications"); entityManager.persist(group);
            var chip = new SpecAttribute(); chip.setName("Chipset"); chip.setGroup(group); entityManager.persist(chip);
            var camera = new SpecAttribute(); camera.setName("Camera chính"); camera.setGroup(group); entityManager.persist(camera);
            for (int i = 1; i <= 5; i++) {
                var p = new Product(); p.setName("Fixture Phone " + i); p.setSlug("fixture-phone-" + i);
                p.setBrand(brand); p.setCategory(category); p.setStatus(ProductStatus.ACTIVE); p.setProductType(ProductType.MAIN);
                p.setDisplayPrice(BigDecimal.valueOf(5000000L + i * 500000L)); p.setTotalStock(10);
                p.setBatteryCapacity(5000 + i * 100); p.setRefreshRate(60 + i * 15); p.setScreenSize(6.1);
                p.setSupport5g(true); p.setAvailableRams(List.of("8GB")); p.setAvailableRoms(List.of("256GB"));
                p.setSearchKeywords("fixture phone choi game"); p.setOsType(OsType.ANDROID);
                var variant = new ProductVariant(); variant.setProduct(p); variant.setSku("fixture-sku-" + i);
                variant.setRam("8GB"); variant.setRom("256GB"); variant.setColorName("Black");
                variant.setStockQuantity(10); variant.setPrice(p.getDisplayPrice());
                var variants = new java.util.ArrayList<ProductVariant>(); variants.add(variant);
                if (i == 1) {
                    var extra = new ProductVariant(); extra.setProduct(p); extra.setSku("fixture-sku-extra");
                    extra.setRam("8GB"); extra.setRom("512GB"); extra.setColorName("Black");
                    extra.setStockQuantity(4); extra.setPrice(p.getDisplayPrice().add(BigDecimal.valueOf(500000)));
                    variants.add(extra); p.setAvailableRoms(List.of("256GB", "512GB"));
                }
                p.setVariants(variants);
                var chipValue = new ProductSpecValue(); chipValue.setProduct(p); chipValue.setAttribute(chip); chipValue.setValue("Fixture Chip " + i);
                var cameraValue = new ProductSpecValue(); cameraValue.setProduct(p); cameraValue.setAttribute(camera); cameraValue.setValue((40+i*10) + "MP");
                p.setSpecValues(List.of(chipValue,cameraValue));
                products.save(p);
            }
            });
        };
    }
}

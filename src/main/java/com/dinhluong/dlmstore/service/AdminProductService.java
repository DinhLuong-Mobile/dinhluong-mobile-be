package com.dinhluong.dlmstore.service;

import com.dinhluong.dlmstore.dto.requests.BulkStockUpdateRequest;

import com.dinhluong.dlmstore.dto.requests.ProductRequest;
import com.dinhluong.dlmstore.dto.responses.ProductOverviewStatsResponse;
import com.dinhluong.dlmstore.dto.responses.ProductResponse;
import com.dinhluong.dlmstore.dto.responses.ProductVariantResponse;
import com.dinhluong.dlmstore.entity.*;
import com.dinhluong.dlmstore.entity.Enums.OrderStatus;
import com.dinhluong.dlmstore.entity.Enums.ProductStatus;
import com.dinhluong.dlmstore.entity.Enums.ProductType;
import com.dinhluong.dlmstore.exception.ValidationException;
import com.dinhluong.dlmstore.repository.*;
import com.dinhluong.dlmstore.service.tools.ProductDataEnricher;

import lombok.RequiredArgsConstructor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.hibernate.validator.internal.util.stereotypes.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AdminProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductService productService;
    @Autowired
    @Lazy
    private OrderService orderService;
    private final CloudinaryService cloudinaryService;
    private final ProductVariantRepository productVariantRepository;
    private final ExcelExportService<List<Product>> excelExportService;
    private final SpecAttributeRepository specAttributeRepository;
    private final OrderRepository orderRepository;
    // 🔥 CẬP NHẬT 1: Inject Class làm giàu dữ liệu tập trung
    private final ProductDataEnricher productDataEnricher;
    private final NotificationService notificationService;
    private final PaymentRepository paymentRepository;
    public ProductOverviewStatsResponse getOverviewStats() {
        return ProductOverviewStatsResponse.builder()
                .totalProducts(productRepository.count())
                .activeProducts(productRepository.countActiveProducts())
                .inactiveProducts(productRepository.countInactiveProducts())
                .outOfStockVariants(productVariantRepository.countOutOfStockVariants())
                .lowStockVariants(productVariantRepository.countLowStockVariants())
                .build();
    }

    @Transactional(readOnly = true)
    public ByteArrayInputStream exportProductsToExcel(ProductType productType, String keyword, ProductStatus status,
            Long brandId, Long categoryId) {
        List<Product> products = productRepository.findAllWithFilters(productType, keyword, status, brandId, categoryId);

        for (Product p : products) {
            if (p.getVariants() != null) p.getVariants().size();
            if (p.getImages() != null) p.getImages().size();
            if (p.getSpecValues() != null) p.getSpecValues().size();
            if (p.getHighlightSpecs() != null) p.getHighlightSpecs().size();
        }
        return excelExportService.exportToExcel(products, "Danh_sach_san_pham");
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getAdminProducts(ProductType productType, String keyword, ProductStatus status,
            Long brandId, Long categoryId, Pageable pageable) {
        System.out.println("---- Status nhận được: " + status);
        Page<Product> products = productRepository.findWithFilters(productType, keyword, status, brandId, categoryId, pageable);

        return products.map(p -> {
            int outOfStockCount = p.getVariants() != null ? (int) p.getVariants().stream()
                    .filter(v -> v.getStockQuantity() == null || v.getStockQuantity() == 0).count() : 0;
            int lowStockCount = p.getVariants() != null ? (int) p.getVariants().stream()
                    .filter(v -> v.getStockQuantity() != null && v.getStockQuantity() > 0 && v.getStockQuantity() < 5).count() : 0;

            return ProductResponse.builder()
                    .id(p.getId())
                    .name(p.getName())
                    .slug(p.getSlug())
                    .displayPrice(p.getDisplayPrice())
                    .originalPrice(p.getOriginalPrice())
                    .thumbnailUrl(p.getThumbnailUrl())
                    .brandName(p.getBrand() != null ? p.getBrand().getName() : "N/A")
                    .categoryName(p.getCategory() != null ? p.getCategory().getName() : "N/A")
                    .status(p.getStatus() != null ? p.getStatus().name() : "INACTIVE")
                    .totalVariants(p.getVariants() != null ? p.getVariants().size() : 0)
                    .totalStock(p.getTotalStock())
                    .isFeatured(p.isFeatured())
                    .soldQuantity(p.getSoldQuantity())
                    .createdAt(p.getCreatedAt())
                    .outOfStockVariantCount(outOfStockCount) 
                    .lowStockVariantCount(lowStockCount) 
                    .build();
        });
    }

    @Transactional
    public void toggleProductStatus(Long id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm!"));
        if (product.getStatus() == ProductStatus.ACTIVE) {
            product.setStatus(ProductStatus.INACTIVE);
        } else {
            product.setStatus(ProductStatus.ACTIVE);
        }
        productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public ProductRequest getProductByIdForEdit(Long id) {
        Product p = productRepository.findById(id).orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm"));
        ProductRequest req = new ProductRequest();
        req.setId(p.getId());
        req.setName(p.getName());
        req.setSlug(p.getSlug());
        req.setProductType(p.getProductType());
        req.setCategoryId(p.getCategory() != null ? p.getCategory().getId() : null);
        req.setBrandId(p.getBrand() != null ? p.getBrand().getId() : null);
        req.setDisplayPrice(p.getDisplayPrice());
        req.setOriginalPrice(p.getOriginalPrice());
        req.setStatus(p.getStatus());
        req.setDescription(p.getDescription());
        req.setThumbnailUrl(p.getThumbnailUrl());
        req.setSpecificationsJson(p.getSpecificationsJson()); 
        req.setInstallmentText(p.getInstallmentText());
        req.setHighlightFeatures(p.getHighlightFeatures());
        req.setSpecialFeatures(p.getSpecialFeatures());
        req.setOsType(p.getOsType());
        req.setScreenSize(p.getScreenSize());
        req.setScreenResolutionType(p.getScreenResolutionType());
        req.setRefreshRate(p.getRefreshRate());
        req.setBatteryCapacity(p.getBatteryCapacity());
        req.setSupport5g(p.getSupport5g());

        if (p.getImages() != null && !p.getImages().isEmpty()) {
            req.setImages(p.getImages().stream().map(img -> {
                ProductRequest.ImageDTO dto = new ProductRequest.ImageDTO();
                dto.setId(img.getId());
                dto.setImageUrl(img.getImageUrl());
                dto.setSortOrder(img.getSortOrder());
                return dto;
            }).collect(Collectors.toList()));
        }

        if (p.getVariants() != null && !p.getVariants().isEmpty()) {
            req.setVariants(p.getVariants().stream().map(v -> {
                ProductRequest.VariantDTO dto = new ProductRequest.VariantDTO();
                dto.setId(v.getId());
                dto.setSku(v.getSku());
                dto.setColorName(v.getColorName());
                dto.setColorHex(v.getColorHex());
                dto.setPrice(v.getPrice());
                dto.setRam(v.getRam());
                dto.setRom(v.getRom());
                dto.setStockQuantity(v.getStockQuantity());
                dto.setIsActive(v.isActive());
                dto.setImageUrl(v.getImageUrl()); 
                return dto;
            }).collect(Collectors.toList()));
        }

        if (p.getHighlightSpecs() != null && !p.getHighlightSpecs().isEmpty()) {
            req.setHighlightSpecs(p.getHighlightSpecs().stream().map(h -> {
                ProductRequest.HighlightSpecDTO dto = new ProductRequest.HighlightSpecDTO();
                dto.setId(h.getId());
                dto.setLabel(h.getLabel());
                dto.setValue(h.getValue());
                dto.setIconUrl(h.getIconUrl());
                return dto;
            }).collect(Collectors.toList()));
        }

        if (p.getSpecValues() != null && !p.getSpecValues().isEmpty()) {
            req.setSpecValues(p.getSpecValues().stream().map(sv -> {
                ProductRequest.SpecValueDTO dto = new ProductRequest.SpecValueDTO();
                dto.setAttributeId(sv.getAttribute().getId());
                dto.setValue(sv.getValue());
                return dto;
            }).collect(Collectors.toList()));
        }

        return req;
    }

    // =========================================================
    // 5. TẠO MỚI SẢN PHẨM (CREATE)
    // =========================================================
    @Transactional(rollbackFor = Exception.class)
    public Product createProduct(ProductRequest request,
            MultipartFile thumbnailFile,
            List<MultipartFile> galleryFiles) throws IOException {
        validateProductRequest(request);
        Product product = new Product();
        product.setImages(new ArrayList<>());
        product.setVariants(new ArrayList<>());
        product.setHighlightSpecs(new ArrayList<>());
        product.setSpecValues(new ArrayList<>());

        mapBasicProductInfo(product, request, thumbnailFile);

        int currentSortOrder = 1;
        if (request.getImages() != null) {
            for (ProductRequest.ImageDTO imgDto : request.getImages()) {
                if (imgDto.getImageUrl() != null && !imgDto.getImageUrl().isEmpty()) {
                    ProductImage img = new ProductImage();
                    img.setImageUrl(imgDto.getImageUrl());
                    img.setSortOrder(imgDto.getSortOrder() != null ? imgDto.getSortOrder() : currentSortOrder++);
                    img.setProduct(product);
                    product.getImages().add(img);
                }
            }
        }

        if (galleryFiles != null && !galleryFiles.isEmpty()) {
            List<String> uploadedUrls = galleryFiles.parallelStream()
                    .filter(file -> !file.isEmpty())
                    .map(file -> {
                        try {
                            return cloudinaryService.uploadFile(file);
                        } catch (IOException e) {
                            throw new RuntimeException("Lỗi upload: " + e.getMessage());
                        }
                    })
                    .collect(Collectors.toList());

            for (String url : uploadedUrls) {
                ProductImage img = new ProductImage();
                img.setImageUrl(url);
                img.setSortOrder(currentSortOrder++);
                img.setProduct(product);
                product.getImages().add(img);
            }
        }

        if (request.getVariants() != null) {
            for (ProductRequest.VariantDTO varDto : request.getVariants()) {
                ProductVariant variant = new ProductVariant();
                mapVariantInfo(variant, varDto, request.getDisplayPrice());
                variant.setProduct(product);
                product.getVariants().add(variant);
            }
        }

        if (request.getHighlightSpecs() != null) {
            for (ProductRequest.HighlightSpecDTO specDto : request.getHighlightSpecs()) {
                ProductHighlightSpec spec = new ProductHighlightSpec();
                mapHighlightSpecInfo(spec, specDto);
                spec.setProduct(product);
                product.getHighlightSpecs().add(spec);
            }
        }

        if (request.getSpecValues() != null) {
            for (ProductRequest.SpecValueDTO specValDto : request.getSpecValues()) {
                ProductSpecValue specVal = new ProductSpecValue();
                specVal.setValue(specValDto.getValue());
                specVal.setAttribute(specAttributeRepository.findById(specValDto.getAttributeId())
                        .orElseThrow(() -> new RuntimeException("Lỗi thuộc tính")));
                specVal.setProduct(product);
                product.getSpecValues().add(specVal);
            }
        }

        // 🔥 CẬP NHẬT 2: Gọi class chuyên xử lý trước khi lưu để nặn JSON, Keyword, Tồn kho
        productDataEnricher.enrichProductBeforeSave(product, request.getSpecificationsJson());

        return productRepository.save(product);
    }

    // =========================================================
    // 6. CẬP NHẬT SẢN PHẨM (UPDATE)
    // =========================================================
    @Transactional(rollbackFor = Exception.class)
    public Product updateProduct(Long id, ProductRequest request,
            MultipartFile thumbnailFile,
            List<MultipartFile> galleryFiles) throws IOException {

        validateProductRequest(request);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm để cập nhật"));
        if (product.getVariants() == null) product.setVariants(new ArrayList<>());
        if (product.getImages() == null) product.setImages(new ArrayList<>());
        if (product.getHighlightSpecs() == null) product.setHighlightSpecs(new ArrayList<>());
        if (product.getSpecValues() == null) product.setSpecValues(new ArrayList<>());
        mapBasicProductInfo(product, request, thumbnailFile);

        // --- 1. UPDATE VARIANTS ---
        if (request.getVariants() != null) {
            List<Long> requestedVariantIds = request.getVariants().stream()
                    .map(ProductRequest.VariantDTO::getId).filter(vId -> vId != null).collect(Collectors.toList());
            product.getVariants().removeIf(existingVar -> !requestedVariantIds.contains(existingVar.getId()));

            for (ProductRequest.VariantDTO varDto : request.getVariants()) {
                if (varDto.getId() != null) {
                    ProductVariant existingVar = product.getVariants().stream()
                            .filter(v -> v.getId().equals(varDto.getId())).findFirst().orElse(null);
                    if (existingVar != null) mapVariantInfo(existingVar, varDto, request.getDisplayPrice());
                } else {
                    ProductVariant newVariant = new ProductVariant();
                    mapVariantInfo(newVariant, varDto, request.getDisplayPrice());
                    newVariant.setProduct(product);
                    product.getVariants().add(newVariant);
                }
            }
        } else {
            product.getVariants().clear();
        }

        // --- 2. UPDATE IMAGES ---
        int currentSortOrder = 1;
        if (request.getImages() != null) {
            List<Long> requestedImageIds = request.getImages().stream()
                    .map(ProductRequest.ImageDTO::getId).filter(imgId -> imgId != null).collect(Collectors.toList());
            product.getImages().removeIf(existingImg -> !requestedImageIds.contains(existingImg.getId()));

            for (ProductRequest.ImageDTO imgDto : request.getImages()) {
                if (imgDto.getId() != null) {
                    ProductImage existingImg = product.getImages().stream()
                            .filter(img -> img.getId().equals(imgDto.getId())).findFirst().orElse(null);
                    if (existingImg != null) {
                        existingImg.setImageUrl(imgDto.getImageUrl());
                        existingImg.setSortOrder(imgDto.getSortOrder() != null ? imgDto.getSortOrder() : currentSortOrder++);
                    }
                } else {
                    if (imgDto.getImageUrl() != null && !imgDto.getImageUrl().isEmpty()) {
                        ProductImage newImg = new ProductImage();
                        newImg.setImageUrl(imgDto.getImageUrl());
                        newImg.setSortOrder(imgDto.getSortOrder() != null ? imgDto.getSortOrder() : currentSortOrder++);
                        newImg.setProduct(product);
                        product.getImages().add(newImg);
                    }
                }
            }
        } else {
            product.getImages().clear();
        }

        if (galleryFiles != null && !galleryFiles.isEmpty()) {
            List<String> uploadedUrls = galleryFiles.parallelStream()
                    .filter(file -> !file.isEmpty())
                    .map(file -> {
                        try { return cloudinaryService.uploadFile(file); } 
                        catch (IOException e) { throw new RuntimeException("Lỗi upload: " + e.getMessage()); }
                    }).collect(Collectors.toList());

            for (String url : uploadedUrls) {
                ProductImage img = new ProductImage();
                img.setImageUrl(url);
                img.setSortOrder(currentSortOrder++);
                img.setProduct(product);
                product.getImages().add(img);
            }
        }

        // --- 3. UPDATE HIGHLIGHT SPECS ---
        if (request.getHighlightSpecs() != null) {
            List<Long> requestedSpecIds = request.getHighlightSpecs().stream()
                    .map(ProductRequest.HighlightSpecDTO::getId).filter(specId -> specId != null).collect(Collectors.toList());
            product.getHighlightSpecs().removeIf(existingSpec -> !requestedSpecIds.contains(existingSpec.getId()));

            for (ProductRequest.HighlightSpecDTO specDto : request.getHighlightSpecs()) {
                if (specDto.getId() != null) {
                    ProductHighlightSpec existingSpec = product.getHighlightSpecs().stream()
                            .filter(s -> s.getId().equals(specDto.getId())).findFirst().orElse(null);
                    if (existingSpec != null) mapHighlightSpecInfo(existingSpec, specDto);
                } else {
                    ProductHighlightSpec newSpec = new ProductHighlightSpec();
                    mapHighlightSpecInfo(newSpec, specDto);
                    newSpec.setProduct(product);
                    product.getHighlightSpecs().add(newSpec);
                }
            }
        } else {
            product.getHighlightSpecs().clear();
        }

        // --- 4. UPDATE SPEC VALUES (EAV) ---
        if (request.getSpecValues() != null) {
            List<Long> requestedAttrIds = request.getSpecValues().stream()
                    .map(ProductRequest.SpecValueDTO::getAttributeId).collect(Collectors.toList());

            // Đã fix: Tránh NPE nếu trong DB có SpecValue bị mất liên kết Attribute
            product.getSpecValues().removeIf(existingVal ->
                    existingVal.getAttribute() == null || !requestedAttrIds.contains(existingVal.getAttribute().getId()));

            for (ProductRequest.SpecValueDTO specValDto : request.getSpecValues()) {
                // Đã fix: Thêm check v.getAttribute() != null
                ProductSpecValue existingVal = product.getSpecValues().stream()
                        .filter(v -> v.getAttribute() != null && v.getAttribute().getId().equals(specValDto.getAttributeId()))
                        .findFirst().orElse(null);

                if (existingVal != null) {
                    existingVal.setValue(specValDto.getValue());
                } else {
                    ProductSpecValue newVal = new ProductSpecValue();
                    newVal.setValue(specValDto.getValue());
                    newVal.setAttribute(specAttributeRepository.findById(specValDto.getAttributeId())
                            .orElseThrow(() -> new RuntimeException("Không tìm thấy thuộc tính")));
                    newVal.setProduct(product);
                    product.getSpecValues().add(newVal);
                }
            }
        } else {
            product.getSpecValues().clear();
        }

        // 🔥 CẬP NHẬT 3: Gọi class chuyên xử lý trước khi lưu để nặn JSON, Keyword, Tồn kho
        productDataEnricher.enrichProductBeforeSave(product, request.getSpecificationsJson());

        return productRepository.save(product);
    }

    private void mapBasicProductInfo(Product product, ProductRequest request, MultipartFile thumbnailFile)
            throws IOException {
        product.setName(request.getName());
        if (request.getSlug() == null || request.getSlug().trim().isEmpty()) {
            // Tận dụng luôn tiện ích unAccent có sẵn của bạn
            String generatedSlug = com.dinhluong.dlmstore.utils.StringUtils.unAccent(request.getName())
                                    .toLowerCase()
                                    .replaceAll("[^a-z0-9]+", "-");
            
            // Xóa dấu gạch ngang thừa ở đầu/cuối nếu có
            generatedSlug = generatedSlug.replaceAll("^-+|-+$", ""); 
            product.setSlug(generatedSlug);
        } else {
            // Nếu có nhập thì cũng nên chuẩn hóa lại cho an toàn
            String safeSlug = request.getSlug().toLowerCase().replaceAll("[^a-z0-9]+", "-");
            product.setSlug(safeSlug);
        }
        product.setProductType(request.getProductType());
        product.setDisplayPrice(request.getDisplayPrice());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setStatus(request.getStatus() != null ? request.getStatus() : ProductStatus.ACTIVE);
        product.setDescription(request.getDescription());
        
        // 🔥 CẬP NHẬT 4: Đã xóa dòng product.setSpecificationsJson(...) ở đây vì Enricher sẽ tự làm

        product.setInstallmentText(request.getInstallmentText());
        product.setHighlightFeatures(request.getHighlightFeatures());
        product.setSpecialFeatures(request.getSpecialFeatures());
        product.setOsType(request.getOsType());
        product.setScreenSize(request.getScreenSize());
        product.setScreenResolutionType(request.getScreenResolutionType());
        product.setRefreshRate(request.getRefreshRate());
        product.setBatteryCapacity(request.getBatteryCapacity());
        product.setSupport5g(request.getSupport5g() != null ? request.getSupport5g() : false); 

        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            product.setThumbnailUrl(cloudinaryService.uploadFile(thumbnailFile));
        } else if (request.getThumbnailUrl() != null) {
            product.setThumbnailUrl(request.getThumbnailUrl());
        }

        if (request.getCategoryId() != null) {
            product.setCategory(categoryRepository.findById(request.getCategoryId()).orElse(null));
        }
        if (request.getBrandId() != null) {
            product.setBrand(brandRepository.findById(request.getBrandId()).orElse(null));
        }
    }

    private void mapVariantInfo(ProductVariant variant, ProductRequest.VariantDTO dto,
            java.math.BigDecimal defaultPrice) {
        variant.setSku(dto.getSku());
        variant.setColorName(dto.getColorName());
        variant.setColorHex(dto.getColorHex());
        variant.setPrice(dto.getPrice() != null ? dto.getPrice() : defaultPrice);
        variant.setRam(dto.getRam());
        variant.setRom(dto.getRom());
        variant.setStockQuantity(dto.getStockQuantity() != null ? dto.getStockQuantity() : 0);
        variant.setActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        variant.setImageUrl(dto.getImageUrl());
    }

    private void mapHighlightSpecInfo(ProductHighlightSpec spec, ProductRequest.HighlightSpecDTO dto) {
        spec.setLabel(dto.getLabel());
        spec.setValue(dto.getValue());
        spec.setIconUrl(dto.getIconUrl());
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm!"));

        // 1. Tìm các đơn hàng đang chờ hoặc đang xử lý của sản phẩm này
        List<OrderStatus> targetStatuses = Arrays.asList(OrderStatus.PENDING, OrderStatus.PROCESSING);
        List<Order> affectedOrders = orderRepository.findUnfinishedOrdersByProductId(id, targetStatuses);

        // 2. GỌI ORDER SERVICE ĐỂ HỦY ĐƠN TỰ ĐỘNG
        String cancelReason = "Sản phẩm [" + product.getName() + "] hiện đã ngừng kinh doanh. Rất xin lỗi quý khách!";

        for (Order order : affectedOrders) {
            try {
                // Mượn tay OrderService để Hủy đơn + Hoàn kho + Xử lý hoàn tiền + Gửi Noti
                orderService.updateOrderStatus(
                        order.getId(),
                        Order.OrderStatus.CANCELLED.name(),
                        cancelReason,
                        "ADMIN"
                );
            } catch (Exception e) {
                // Dùng try-catch để lỡ 1 đơn bị lỗi thì không làm chết nguyên tiến trình xóa sản phẩm
                System.err.println("Lỗi khi auto-cancel đơn hàng #" + order.getId() + ": " + e.getMessage());
            }
        }

        // 3. Tiến hành Xóa mềm (Soft Delete) sản phẩm
        String timestamp = String.valueOf(System.currentTimeMillis());
        product.setSlug(product.getSlug() + "-deleted-" + timestamp);
        product.setDeleted(true);

        if (product.getVariants() != null) {
            for (ProductVariant variant : product.getVariants()) {
                if (variant.getSku() != null) {
                    variant.setSku(variant.getSku() + "-del-" + timestamp);
                }
            }
        }

        productRepository.save(product);
    }

    public String uploadSingleImage(MultipartFile file) throws IOException {
        return cloudinaryService.uploadFile(file);
    }

    @Transactional
    public void toggleProductFeatured(Long id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm!"));
        if (!product.isFeatured()) {
            long featuredCount = productRepository.countByIsFeaturedTrue();
            if (featuredCount >= 10) {
                throw new RuntimeException("Chỉ được ghim tối đa 10 sản phẩm lên trang chủ!");
            }
        }
        product.setFeatured(!product.isFeatured());
        productRepository.save(product);
    }

    public List<ProductVariantResponse> getProductVariants(Long id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại"));
        if (product.getVariants() == null || product.getVariants().isEmpty()) {
            return new ArrayList<>(); // Trả về list rỗng nếu không có variant
        }
        return product.getVariants().stream()
                .map(v -> ProductVariantResponse.builder()
                        .id(v.getId())
                        .sku(v.getSku())
                        .colorName(v.getColorName())
                        .colorHex(v.getColorHex())
                        .ram(v.getRam())
                        .rom(v.getRom())
                        .price(v.getPrice())
                        .stockQuantity(v.getStockQuantity())
                        .imageUrl(v.getImageUrl())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateBulkVariantStock(BulkStockUpdateRequest request) {
        // Chốt chặn 1: Tránh lỗi nếu request.getStocks() null
        if (request == null || request.getStocks() == null || request.getStocks().isEmpty()) {
            return;
        }

        for (BulkStockUpdateRequest.StockItem item : request.getStocks()) {
            ProductVariant variant = productVariantRepository.findById(item.getVariantId())
                    .orElseThrow(() -> new RuntimeException("Biến thể không tồn tại"));
            variant.setStockQuantity(item.getStockQuantity());
            productVariantRepository.save(variant);
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại"));

        // Chốt chặn 2: Tránh lỗi nếu getVariants() null
        int newTotalStock = 0;
        if (product.getVariants() != null) {
            newTotalStock = product.getVariants().stream()
                    .mapToInt(v -> v.getStockQuantity() != null ? v.getStockQuantity() : 0)
                    .sum();
        }

        product.setTotalStock(newTotalStock);
        productRepository.save(product);
    }

    private void validateProductRequest(ProductRequest request) {
        // 1. Validate Tên sản phẩm
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ValidationException("Tên sản phẩm không được để trống.");
        }

        // 2. Validate Giá bán
        if (request.getDisplayPrice() == null) {
            throw new ValidationException("Giá bán sản phẩm không được để trống.");
        }
        if (request.getDisplayPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Giá bán sản phẩm phải lớn hơn hoặc bằng 0.");
        }

        // 3. Validate Giá gốc (Nếu có nhập thì phải >= giá bán)
        if (request.getOriginalPrice() == null) {
            // Nếu bạn muốn BẮT BUỘC nhập
            throw new ValidationException("Giá niêm yết không được để trống.");
        } else {
            // Nếu đã có giá trị (không null) thì mới được phép .compareTo()
            if (request.getOriginalPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidationException("Giá niêm yết không được nhỏ hơn 0.");
            }
            if (request.getOriginalPrice().compareTo(request.getDisplayPrice()) < 0) {
                throw new ValidationException("Giá niêm yết không được nhỏ hơn giá bán khuyến mãi.");
            }
        }

        // 4. Validate Danh mục và Hãng (Bắt buộc)
        if (request.getCategoryId() == null) {
            throw new ValidationException("Vui lòng chọn danh mục cho sản phẩm.");
        }
        if (request.getBrandId() == null) {
            throw new ValidationException("Vui lòng chọn thương hiệu cho sản phẩm.");
        }

        // 5. Validate Biến thể (Variants) nếu có
        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            List<String> skuList = new ArrayList<>(); // Dùng để check trùng SKU trong cùng 1 form gửi lên

            for (ProductRequest.VariantDTO variant : request.getVariants()) {
                // A. Validate giá và tồn kho
                if (variant.getPrice() != null && variant.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                    throw new ValidationException("Giá của phân loại/biến thể không được nhỏ hơn 0.");
                }
                if (variant.getStockQuantity() != null && variant.getStockQuantity() < 0) {
                    throw new ValidationException("Số lượng tồn kho của phân loại không được là số âm.");
                }

                // B. Validate SKU trống
                if (variant.getSku() == null || variant.getSku().trim().isEmpty()) {
                    throw new ValidationException("Mã SKU của các phiên bản không được để trống.");
                }

                // C. Validate trùng SKU trong cùng 1 lần gửi
                if (skuList.contains(variant.getSku())) {
                    throw new ValidationException("Mã SKU '" + variant.getSku() + "' bị trùng lặp giữa các phiên bản.");
                }
                skuList.add(variant.getSku());

                // D. Validate trùng SKU với Database (Cần gọi Repository)
                // Nếu bạn có hàm existsBySku trong ProductVariantRepository, hãy mở comment đoạn này:

            boolean isDuplicateInDb;
            if (variant.getId() != null) {
                // Đang Edit: Bỏ qua check trùng với chính nó
                isDuplicateInDb = productVariantRepository.existsBySkuAndIdNot(variant.getSku(), variant.getId());
            } else {
                // Đang Create mới hoàn toàn
                isDuplicateInDb = productVariantRepository.existsBySku(variant.getSku());
            }
            if (isDuplicateInDb) {
                throw new ValidationException("Mã SKU '" + variant.getSku() + "' đã tồn tại trên hệ thống. Vui lòng chọn mã khác!");
            }

            }
        }
    }
}
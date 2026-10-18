package com.dinhluong.dlmstore.service;

import com.dinhluong.dlmstore.dto.responses.DashboardResponse;
import com.dinhluong.dlmstore.entity.Order;
import com.dinhluong.dlmstore.entity.ProductVariant;
import com.dinhluong.dlmstore.entity.Voucher;
import com.dinhluong.dlmstore.chatbot.repository.ChatbotInteractionRepository;
import com.dinhluong.dlmstore.repository.*;
import com.dinhluong.dlmstore.repository.projections.DashboardProjections;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final VoucherRepository voucherRepository;
    private final PaymentRepository paymentRepository;
    private final ChatbotInteractionRepository chatbotInteractionRepository;
   
   public DashboardResponse getDashboardData(String timeFilter, String startDateStr, String endDateStr) {
    // Gọi hàm helper mới để lấy Range thời gian
    LocalDateTime[] dateRange = getDateRange(timeFilter, startDateStr, endDateStr);
    LocalDateTime startDate = dateRange[0];
    LocalDateTime endDate = dateRange[1];
    
    return DashboardResponse.builder()
            .overview(buildOverview(startDate, endDate))
            .cancellationStats(buildCancellationStats(startDate, endDate))
            .performance(buildBusinessPerformance(startDate, endDate))
            .revenueTrends(buildRevenueTrends(startDate, endDate))
            .paymentMethods(buildPaymentMethods(startDate, endDate))
            .topProducts(buildTopProducts(startDate, endDate))
            .lowStockVariants(buildLowStockVariants())
            .topBrands(buildTopBrands(startDate, endDate))
            .activeVouchers(buildActiveVouchers())
            .supportStats(buildSupportStats(startDate, endDate))
            .build();
}

    private DashboardResponse.Overview buildOverview(LocalDateTime start, LocalDateTime end) {
        BigDecimal totalRevenue = orderRepository.sumRevenue(start, end);
        return DashboardResponse.Overview.builder()
                .totalRevenue(totalRevenue != null ? totalRevenue : BigDecimal.ZERO)
                .completedOrders(orderRepository.countCompletedOrders(start, end))
                .newUsers(userRepository.countNewUsers(start, end))
                .pendingTasks(chatMessageRepository.countUnreadMessages()) // Giả định lấy tin nhắn chưa đọc
                .build();
    }

    private List<DashboardResponse.RevenueTrend> buildRevenueTrends(LocalDateTime start, LocalDateTime end) {
        List<Order> orders = orderRepository.findDeliveredOrdersForTrends(start, end);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");
        
        // Group by Date String
        Map<String, DashboardResponse.RevenueTrend> trendsMap = new LinkedHashMap<>();
        
        for (Order o : orders) {
            String dateKey = o.getCreatedAt().format(formatter);
            DashboardResponse.RevenueTrend trend = trendsMap.getOrDefault(dateKey, 
                    DashboardResponse.RevenueTrend.builder().date(dateKey).revenue(BigDecimal.ZERO).orders(0L).build());
            
            trend.setRevenue(trend.getRevenue().add(o.getTotalAmount()));
            trend.setOrders(trend.getOrders() + 1);
            trendsMap.put(dateKey, trend);
        }
        return new ArrayList<>(trendsMap.values());
    }

    private List<DashboardResponse.PaymentMethodStat> buildPaymentMethods(LocalDateTime start, LocalDateTime end) {
        List<DashboardProjections.PaymentMethodProjection> stats = paymentRepository.getPaymentMethodsStats(start, end);
        String[] colors = {"#faad14", "#1890ff", "#eb2f96", "#52c41a"}; // Bảng màu tương ứng
        List<DashboardResponse.PaymentMethodStat> result = new ArrayList<>();
        
        for (int i = 0; i < stats.size(); i++) {
            DashboardProjections.PaymentMethodProjection stat = stats.get(i);
            result.add(DashboardResponse.PaymentMethodStat.builder()
                    .name(stat.getMethod() != null ? stat.getMethod() : "Khác")
                    .value(stat.getMethodCount())
                    .color(colors[i % colors.length])
                    .build());
        }
        return result;
    }

    private List<DashboardResponse.TopProduct> buildTopProducts(LocalDateTime start, LocalDateTime end) {
        return orderItemRepository.getTopProducts(start, end).stream().map(p -> 
            DashboardResponse.TopProduct.builder()
                    .id(p.getId())
                    .name(p.getProductName())
                    .variant(p.getVariantName())
                    .sold(p.getSold())
                    .revenue(p.getRevenue())
                    .image(p.getImage())
                    .build()
        ).collect(Collectors.toList());
    }

    private List<DashboardResponse.LowStockVariant> buildLowStockVariants() {
        List<ProductVariant> variants = productVariantRepository.findTop10ByStockQuantityLessThanOrderByStockQuantityAsc(10);
        return variants.stream().map(v -> 
            DashboardResponse.LowStockVariant.builder()
                    .sku(v.getSku())
                    .name(v.getProduct() != null ? v.getProduct().getName() : "Unknown")
                    .variant(v.getRam() + "/" + v.getRom() + " - " + v.getColorName())
                    .stock(v.getStockQuantity())
                    .image(v.getImageUrl())
                    .build()
        ).collect(Collectors.toList());
    }

    private List<DashboardResponse.TopBrand> buildTopBrands(LocalDateTime start, LocalDateTime end) {
        String[] colors = {"#000000", "#1428A0", "#FF6700", "#E3000F", "#0082D6"};
        List<DashboardProjections.TopBrandProjection> brands = orderItemRepository.getTopBrands(start, end);
        List<DashboardResponse.TopBrand> result = new ArrayList<>();
        
        for (int i = 0; i < brands.size(); i++) {
            DashboardProjections.TopBrandProjection b = brands.get(i);
            result.add(DashboardResponse.TopBrand.builder()
                    .name(b.getName())
                    .revenue(b.getRevenue())
                    .fill(colors[i % colors.length])
                    .build());
        }
        return result;
    }

    private List<DashboardResponse.ActiveVoucher> buildActiveVouchers() {
        List<Voucher> vouchers = voucherRepository.findActiveVouchers();
        return vouchers.stream().limit(5).map(v -> 
            DashboardResponse.ActiveVoucher.builder()
                    .code(v.getCode())
                    .used(v.getUsedCount())
                    .limit(v.getUsageLimit())
                    .expiry(v.getExpiryDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                    .build()
        ).collect(Collectors.toList());
    }

    private DashboardResponse.SupportStats buildSupportStats(LocalDateTime start, LocalDateTime end) {
        long botHandled = chatbotInteractionRepository.countInteractions(start, end);
        // Human handled có thể đo bằng tin nhắn đã được Reply, ở đây giả định lấy số lượng ChatMessage
        long humanHandled = chatMessageRepository.count(); 
        
        return DashboardResponse.SupportStats.builder()
                .chatbotHandled(botHandled)
                .humanHandled(humanHandled)
                .avgRating(4.8) // Mock rating vì chưa có entity Review/Comment
                .build();
    }


    private List<DashboardResponse.CancellationStat> buildCancellationStats(LocalDateTime start, LocalDateTime end) {
        return orderRepository.countByReason(start, end).stream()
                .map(p -> DashboardResponse.CancellationStat.builder()
                        .reason(p.getReason())
                        .count(p.getCount())
                        .build())
                .collect(Collectors.toList());
    }

    private DashboardResponse.BusinessPerformance buildBusinessPerformance(LocalDateTime start, LocalDateTime end) {
        DashboardProjections.PerformanceProjection p = orderRepository.getPerformanceData(start, end);
        
        if (p == null || p.getTotalOrders() == 0) {
            return DashboardResponse.BusinessPerformance.builder()
                    .conversionRate(0.0).returnRate(0.0).lostRevenue(BigDecimal.ZERO).build();
        }

        double total = p.getTotalOrders().doubleValue();
        double conversion = (p.getCompletedCount() / total) * 100;
        double returns = (p.getReturnedCount() / total) * 100;

        return DashboardResponse.BusinessPerformance.builder()
                .conversionRate(BigDecimal.valueOf(conversion).setScale(1, RoundingMode.HALF_UP).doubleValue())
                .returnRate(BigDecimal.valueOf(returns).setScale(1, RoundingMode.HALF_UP).doubleValue())
                .lostRevenue(p.getLostRevenue() != null ? p.getLostRevenue() : BigDecimal.ZERO)
                .build();
    }

    

    // Tiện ích convert timeFilter string sang Date Range
    private LocalDateTime[] getDateRange(String timeFilter, String startDateStr, String endDateStr) {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime start;
    LocalDateTime end = now;

    if (timeFilter == null) timeFilter = "this_month";

    // Xử lý trường hợp Tùy chọn ngày
    if ("custom".equals(timeFilter) && startDateStr != null && endDateStr != null) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            // Parse ngày bắt đầu từ 00:00:00
            start = LocalDate.parse(startDateStr, formatter).atStartOfDay();
            // Parse ngày kết thúc đến 23:59:59
            end = LocalDate.parse(endDateStr, formatter).atTime(LocalTime.MAX);
            return new LocalDateTime[]{start, end};
        } catch (Exception e) {
            // Nếu parse lỗi, mặc định về tháng này để tránh crash
            timeFilter = "this_month";
        }
    }

    // Logic cho các mốc thời gian cố định
    switch (timeFilter) {
        case "today":
            start = now.toLocalDate().atStartOfDay();
            break;
        case "this_week":
            start = now.toLocalDate().with(java.time.DayOfWeek.MONDAY).atStartOfDay();
            break;
        case "this_year":
            start = now.toLocalDate().withDayOfYear(1).atStartOfDay();
            break;
        case "this_month":
        default:
            start = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
            break;
    }
    return new LocalDateTime[]{start, end};
}
}
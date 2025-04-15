package com.sondv.phone.controller;

import com.sondv.phone.dto.CategoryRevenueDTO;
import com.sondv.phone.dto.DailyRevenueDTO;
import com.sondv.phone.dto.TopProductDTO;
import com.sondv.phone.dto.ProfitStatDTO;
import com.sondv.phone.service.AdminService;
import com.sondv.phone.service.ReportService;
import com.itextpdf.text.DocumentException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final AdminService adminService;

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/revenue")
    public ResponseEntity<BigDecimal> getRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(reportService.getRevenue(start, end));
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/top-products")
    public ResponseEntity<List<TopProductDTO>> getTopProducts(@RequestParam LocalDateTime start,
                                                              @RequestParam LocalDateTime end,
                                                              @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(reportService.getTopSellingProducts(start, end, limit));
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/orders-by-status")
    public ResponseEntity<Map<String, Long>> getOrderByStatus(@RequestParam LocalDateTime start,
                                                              @RequestParam LocalDateTime end) {
        return ResponseEntity.ok(reportService.getOrderCountByStatus(start, end));
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/low-stock")
    public ResponseEntity<List<Map<String, Object>>> getLowStockProducts(
            @RequestParam(defaultValue = "5") int threshold) {
        return ResponseEntity.ok(adminService.getLowStockProducts(threshold));
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/profit")
    public ResponseEntity<List<ProfitStatDTO>> getProfitStats(
            @RequestParam String type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        return ResponseEntity.ok(reportService.getProfitStats(type, start, end));
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/daily-revenue-optimized")
    public ResponseEntity<List<DailyRevenueDTO>> getDailyRevenueOptimized(
            @RequestParam LocalDate start,
            @RequestParam LocalDate end) {
        return ResponseEntity.ok(reportService.getDailyRevenueOptimized(start, end));
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/revenue-by-category")
    public ResponseEntity<List<CategoryRevenueDTO>> getRevenueByCategory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(reportService.getRevenueByCategory(start, end));
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/export/word")
    public ResponseEntity<ByteArrayResource> exportWord(@RequestParam LocalDateTime start,
                                                        @RequestParam LocalDateTime end) throws IOException {
        ByteArrayResource resource = reportService.exportWordReport(start, end);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.docx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/export/excel")
    public ResponseEntity<ByteArrayResource> exportExcel(@RequestParam LocalDateTime start,
                                                         @RequestParam LocalDateTime end) throws IOException {
        ByteArrayResource resource = reportService.exportExcelReport(start, end);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/export/pdf")
    public ResponseEntity<ByteArrayResource> exportPdf(@RequestParam LocalDateTime start,
                                                       @RequestParam LocalDateTime end) throws IOException, DocumentException {
        ByteArrayResource resource = reportService.exportPdfReport(start, end);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }
}

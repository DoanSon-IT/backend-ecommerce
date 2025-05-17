package com.sondv.phone.service;

import com.itextpdf.text.Document;
import com.sondv.phone.dto.CategoryRevenueDTO;
import com.sondv.phone.dto.DailyRevenueDTO;
import com.sondv.phone.dto.ProfitStatDTO;
import com.sondv.phone.dto.TopProductDTO;
import com.sondv.phone.repository.OrderDetailRepository;
import com.sondv.phone.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.BaseFont;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;

    public BigDecimal getRevenue(LocalDateTime startDate, LocalDateTime endDate) {
        Double result = orderRepository.sumTotalRevenueByDateRange(startDate, endDate);
        return result == null ? BigDecimal.ZERO : BigDecimal.valueOf(result);
    }

    public List<TopProductDTO> getTopSellingProducts(LocalDateTime startDate, LocalDateTime endDate, int limit) {
        return orderDetailRepository.findTopSellingProducts(startDate, endDate, PageRequest.of(0, limit));
    }

    public Map<String, Long> getOrderCountByStatus(LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Long> map = new HashMap<>();
        Arrays.stream(com.sondv.phone.entity.OrderStatus.values()).forEach(status -> {
            long count = orderRepository.countByStatusAndCreatedAtBetween(status, startDate, endDate);
            map.put(status.name(), count);
        });
        return map;
    }

    public List<ProfitStatDTO> getProfitStats(String type, LocalDate start, LocalDate end) {
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.plusDays(1).atStartOfDay(); // include end date

        List<Object[]> results = orderRepository.getProfitGroupedBy(type, startDateTime, endDateTime);

        return results.stream().map(row -> new ProfitStatDTO(
                row[0].toString(), // period
                row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO, // totalProfit
                row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO // totalRevenue
        )).collect(Collectors.toList());
    }

    public List<CategoryRevenueDTO> getRevenueByCategory(LocalDate start, LocalDate end) {
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.atTime(LocalTime.MAX);
        return orderDetailRepository.getRevenueByCategory(startTime, endTime);
    }

    public List<DailyRevenueDTO> getDailyRevenueOptimized(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        List<Object[]> results = orderRepository.getRevenueGroupedByDate(start, end);
        List<DailyRevenueDTO> revenueList = new ArrayList<>();

        for (Object[] row : results) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            BigDecimal total = row[1] != null ? BigDecimal.valueOf(((Number) row[1]).doubleValue()) : BigDecimal.ZERO;
            revenueList.add(new DailyRevenueDTO(date, total));
        }

        return revenueList;
    }

    public ByteArrayResource exportWordReport(LocalDateTime start, LocalDateTime end) throws IOException {
        XWPFDocument doc = new XWPFDocument();

        // Thông tin công ty
        XWPFParagraph companyInfo = doc.createParagraph();
        XWPFRun companyRun = companyInfo.createRun();
        companyRun.setText("CỬA HÀNG DSON MOBILE");
        companyRun.addBreak();
        companyRun.setText("Địa chỉ: số 8, ngõ 134 Cầu Diễn, Bắc Từ Liêm, Hà Nội");
        companyRun.addBreak();
        companyRun.setText("Hotline: 0585068096");
        companyRun.addBreak();
        companyRun.setText("Email: sondv76@gmail.com");
        companyRun.setBold(true);
        companyRun.setFontSize(12);

        // Tiêu đề báo cáo
        XWPFParagraph title = doc.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = title.createRun();
        run.setText("BÁO CÁO DOANH THU & LỢI NHUẬN");
        run.setBold(true);
        run.setFontSize(20);
        XWPFParagraph dateInfo = doc.createParagraph();
        XWPFRun dateRun = dateInfo.createRun();
        dateRun.setText("Từ: " + start + " đến: " + end);
        dateRun.addBreak();
        dateRun.setText("Ngày xuất: " + java.time.LocalDate.now());
        dateRun.setFontSize(11);

        // Tổng quan
        BigDecimal revenue = getRevenue(start, end);
        List<ProfitStatDTO> profitStats = getProfitStats("day", start.toLocalDate(), end.toLocalDate());
        BigDecimal totalProfit = profitStats.stream().map(ProfitStatDTO::getTotalProfit).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        long totalOrders = orderRepository.findByCreatedAtBetween(start, end).size();
        XWPFParagraph overviewTitle = doc.createParagraph();
        XWPFRun overviewRun = overviewTitle.createRun();
        overviewRun.setText("Tổng quan");
        overviewRun.setBold(true);
        overviewRun.setFontSize(14);
        XWPFTable overviewTable = doc.createTable(4, 2);
        overviewTable.getRow(0).getCell(0).setText("Chỉ tiêu");
        overviewTable.getRow(0).getCell(1).setText("Giá trị");
        overviewTable.getRow(1).getCell(0).setText("Tổng doanh thu");
        overviewTable.getRow(1).getCell(1).setText(revenue.toPlainString());
        overviewTable.getRow(2).getCell(0).setText("Tổng lợi nhuận");
        overviewTable.getRow(2).getCell(1).setText(totalProfit.toPlainString());
        overviewTable.getRow(3).getCell(0).setText("Tổng số đơn hàng");
        overviewTable.getRow(3).getCell(1).setText(String.valueOf(totalOrders));

        // Doanh thu & lợi nhuận từng ngày
        XWPFParagraph profitTitle = doc.createParagraph();
        XWPFRun profitRun = profitTitle.createRun();
        profitRun.setText("1. Doanh thu & lợi nhuận theo ngày");
        profitRun.setBold(true);
        profitRun.setFontSize(13);
        XWPFTable profitTable = doc.createTable(profitStats.size() + 1, 4);
        profitTable.getRow(0).getCell(0).setText("Ngày");
        profitTable.getRow(0).getCell(1).setText("Doanh thu");
        profitTable.getRow(0).getCell(2).setText("Lợi nhuận");
        profitTable.getRow(0).getCell(3).setText("Biên lợi nhuận (%)");
        for (int i = 0; i < profitStats.size(); i++) {
            ProfitStatDTO stat = profitStats.get(i);
            profitTable.getRow(i + 1).getCell(0).setText(stat.getPeriod());
            profitTable.getRow(i + 1).getCell(1).setText(stat.getTotalRevenue().toPlainString());
            profitTable.getRow(i + 1).getCell(2).setText(stat.getTotalProfit().toPlainString());
            String margin = stat.getTotalRevenue().compareTo(BigDecimal.ZERO) > 0
                    ? stat.getTotalProfit().multiply(BigDecimal.valueOf(100))
                            .divide(stat.getTotalRevenue(), 2, BigDecimal.ROUND_HALF_UP).toPlainString()
                    : "0.00";
            profitTable.getRow(i + 1).getCell(3).setText(margin);
        }

        // Top sản phẩm bán chạy
        XWPFParagraph topProductTitle = doc.createParagraph();
        XWPFRun topProductRun = topProductTitle.createRun();
        topProductRun.setText("2. Top sản phẩm bán chạy");
        topProductRun.setBold(true);
        topProductRun.setFontSize(13);
        List<TopProductDTO> topProducts = getTopSellingProducts(start, end, 10);
        XWPFTable topProductTable = doc.createTable(topProducts.size() + 1, 3);
        topProductTable.getRow(0).getCell(0).setText("Mã SP");
        topProductTable.getRow(0).getCell(1).setText("Tên sản phẩm");
        topProductTable.getRow(0).getCell(2).setText("Số lượng bán");
        for (int i = 0; i < topProducts.size(); i++) {
            TopProductDTO p = topProducts.get(i);
            topProductTable.getRow(i + 1).getCell(0).setText(String.valueOf(p.getProductId()));
            topProductTable.getRow(i + 1).getCell(1).setText(p.getProductName());
            topProductTable.getRow(i + 1).getCell(2).setText(String.valueOf(p.getTotalSold()));
        }

        // Doanh thu theo danh mục
        XWPFParagraph categoryTitle = doc.createParagraph();
        XWPFRun categoryRun = categoryTitle.createRun();
        categoryRun.setText("3. Doanh thu theo danh mục");
        categoryRun.setBold(true);
        categoryRun.setFontSize(13);
        List<CategoryRevenueDTO> categoryRevenues = getRevenueByCategory(start.toLocalDate(), end.toLocalDate());
        XWPFTable categoryTable = doc.createTable(categoryRevenues.size() + 1, 3);
        categoryTable.getRow(0).getCell(0).setText("Danh mục");
        categoryTable.getRow(0).getCell(1).setText("Doanh thu");
        categoryTable.getRow(0).getCell(2).setText("Số đơn hàng");
        for (int i = 0; i < categoryRevenues.size(); i++) {
            CategoryRevenueDTO c = categoryRevenues.get(i);
            categoryTable.getRow(i + 1).getCell(0).setText(c.getCategory());
            categoryTable.getRow(i + 1).getCell(1).setText(c.getTotalRevenue().toPlainString());
            categoryTable.getRow(i + 1).getCell(2).setText(String.valueOf(c.getOrderCount()));
        }

        // Đơn hàng theo trạng thái
        XWPFParagraph statusTitle = doc.createParagraph();
        XWPFRun statusRun = statusTitle.createRun();
        statusRun.setText("4. Đơn hàng theo trạng thái");
        statusRun.setBold(true);
        statusRun.setFontSize(13);
        Map<String, Long> orderStatus = getOrderCountByStatus(start, end);
        XWPFTable statusTable = doc.createTable(orderStatus.size() + 1, 2);
        statusTable.getRow(0).getCell(0).setText("Trạng thái");
        statusTable.getRow(0).getCell(1).setText("Số lượng");
        int idx = 1;
        for (Map.Entry<String, Long> entry : orderStatus.entrySet()) {
            statusTable.getRow(idx).getCell(0).setText(getVietnameseOrderStatus(entry.getKey()));
            statusTable.getRow(idx).getCell(1).setText(String.valueOf(entry.getValue()));
            idx++;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.write(out);
        doc.close();
        return new ByteArrayResource(out.toByteArray());
    }

    public ByteArrayResource exportExcelReport(LocalDateTime start, LocalDateTime end) throws IOException {
        Workbook workbook = new XSSFWorkbook();

        // Sheet 1: Tổng quan
        Sheet overviewSheet = workbook.createSheet("Tổng quan");
        Row header1 = overviewSheet.createRow(0);
        header1.createCell(0).setCellValue("Chỉ tiêu");
        header1.createCell(1).setCellValue("Giá trị");
        BigDecimal revenue = getRevenue(start, end);
        List<ProfitStatDTO> profitStats = getProfitStats("day", start.toLocalDate(), end.toLocalDate());
        BigDecimal totalProfit = profitStats.stream().map(ProfitStatDTO::getTotalProfit).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        long totalOrders = orderRepository.findByCreatedAtBetween(start, end).size();
        overviewSheet.createRow(1).createCell(0).setCellValue("Tổng doanh thu");
        overviewSheet.getRow(1).createCell(1).setCellValue(revenue.doubleValue());
        overviewSheet.createRow(2).createCell(0).setCellValue("Tổng lợi nhuận");
        overviewSheet.getRow(2).createCell(1).setCellValue(totalProfit.doubleValue());
        overviewSheet.createRow(3).createCell(0).setCellValue("Tổng số đơn hàng");
        overviewSheet.getRow(3).createCell(1).setCellValue(totalOrders);

        // Sheet 2: Doanh thu & lợi nhuận từng ngày
        Sheet profitSheet = workbook.createSheet("Doanh thu & Lợi nhuận từng ngày");
        Row header2 = profitSheet.createRow(0);
        header2.createCell(0).setCellValue("Ngày");
        header2.createCell(1).setCellValue("Doanh thu");
        header2.createCell(2).setCellValue("Lợi nhuận");
        header2.createCell(3).setCellValue("Biên lợi nhuận (%)");
        int rowIdx2 = 1;
        for (ProfitStatDTO stat : profitStats) {
            Row row = profitSheet.createRow(rowIdx2++);
            row.createCell(0).setCellValue(stat.getPeriod());
            row.createCell(1).setCellValue(stat.getTotalRevenue().doubleValue());
            row.createCell(2).setCellValue(stat.getTotalProfit().doubleValue());
            String margin = stat.getTotalRevenue().compareTo(BigDecimal.ZERO) > 0
                    ? stat.getTotalProfit().multiply(BigDecimal.valueOf(100))
                            .divide(stat.getTotalRevenue(), 2, BigDecimal.ROUND_HALF_UP).toPlainString()
                    : "0.00";
            row.createCell(3).setCellValue(margin);
        }

        // Sheet 3: Top sản phẩm bán chạy
        Sheet topProductSheet = workbook.createSheet("Top sản phẩm");
        Row header3 = topProductSheet.createRow(0);
        header3.createCell(0).setCellValue("Mã SP");
        header3.createCell(1).setCellValue("Tên sản phẩm");
        header3.createCell(2).setCellValue("Số lượng bán");
        List<TopProductDTO> topProducts = getTopSellingProducts(start, end, 10);
        int rowIdx3 = 1;
        for (TopProductDTO p : topProducts) {
            Row row = topProductSheet.createRow(rowIdx3++);
            row.createCell(0).setCellValue(p.getProductId());
            row.createCell(1).setCellValue(p.getProductName());
            row.createCell(2).setCellValue(p.getTotalSold());
        }

        // Sheet 4: Doanh thu theo danh mục
        Sheet categorySheet = workbook.createSheet("Doanh thu theo danh mục");
        Row header4 = categorySheet.createRow(0);
        header4.createCell(0).setCellValue("Danh mục");
        header4.createCell(1).setCellValue("Doanh thu");
        header4.createCell(2).setCellValue("Số đơn hàng");
        List<CategoryRevenueDTO> categoryRevenues = getRevenueByCategory(start.toLocalDate(), end.toLocalDate());
        int rowIdx4 = 1;
        for (CategoryRevenueDTO c : categoryRevenues) {
            Row row = categorySheet.createRow(rowIdx4++);
            row.createCell(0).setCellValue(c.getCategory());
            row.createCell(1).setCellValue(c.getTotalRevenue().doubleValue());
            row.createCell(2).setCellValue(c.getOrderCount());
        }

        // Sheet 5: Đơn hàng theo trạng thái
        Sheet statusSheet = workbook.createSheet("Đơn hàng theo trạng thái");
        Row header5 = statusSheet.createRow(0);
        header5.createCell(0).setCellValue("Trạng thái");
        header5.createCell(1).setCellValue("Số lượng");
        Map<String, Long> orderStatus = getOrderCountByStatus(start, end);
        int rowIdx5 = 1;
        for (Map.Entry<String, Long> entry : orderStatus.entrySet()) {
            Row row = statusSheet.createRow(rowIdx5++);
            row.createCell(0).setCellValue(getVietnameseOrderStatus(entry.getKey()));
            row.createCell(1).setCellValue(entry.getValue());
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return new ByteArrayResource(out.toByteArray());
    }

    private String getVietnameseOrderStatus(String status) {
        return switch (status) {
            case "CANCELLED" -> "Đã hủy";
            case "COMPLETED" -> "Hoàn thành";
            case "CONFIRMED" -> "Đã xác nhận";
            case "PENDING" -> "Chờ xử lý";
            case "SHIPPED" -> "Đã giao";
            default -> status;
        };
    }

    public ByteArrayResource exportPdfReport(LocalDateTime start, LocalDateTime end)
            throws IOException, DocumentException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, out);
        document.open();

        // Load font Unicode (arial.ttf) từ thư mục fonts trong resources
        BaseFont unicodeFont = BaseFont.createFont("fonts/arial.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        com.itextpdf.text.Font font = new com.itextpdf.text.Font(unicodeFont, 12);
        com.itextpdf.text.Font fontBold = new com.itextpdf.text.Font(unicodeFont, 14, com.itextpdf.text.Font.BOLD);

        // Thông tin công ty
        Paragraph companyInfo = new Paragraph(
                "CỬA HÀNG DSON MOBILE\nĐịa chỉ: số 8, ngõ 134 Cầu Diễn, Bắc Từ Liêm, Hà Nội\nHotline: 0585068096\nEmail: sondv76@gmail.com",
                fontBold);
        companyInfo.setAlignment(Element.ALIGN_LEFT);
        document.add(companyInfo);
        document.add(new Paragraph(" ", font));

        // Tiêu đề báo cáo
        Paragraph title = new Paragraph("BÁO CÁO DOANH THU & LỢI NHUẬN", fontBold);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
        document.add(new Paragraph("Từ: " + start + " đến: " + end, font));
        document.add(new Paragraph("Ngày xuất: " + java.time.LocalDate.now(), font));
        document.add(new Paragraph(" ", font));

        // Tổng quan
        BigDecimal revenue = getRevenue(start, end);
        List<ProfitStatDTO> profitStats = getProfitStats("day", start.toLocalDate(), end.toLocalDate());
        BigDecimal totalProfit = profitStats.stream().map(ProfitStatDTO::getTotalProfit).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        long totalOrders = orderRepository.findByCreatedAtBetween(start, end).size();
        PdfPTable summaryTable = new PdfPTable(2);
        summaryTable.setSpacingBefore(10);
        summaryTable.setWidthPercentage(60);
        summaryTable.addCell(new PdfPCell(new Phrase("Chỉ tiêu", fontBold)));
        summaryTable.addCell(new PdfPCell(new Phrase("Giá trị", fontBold)));
        summaryTable.addCell(new PdfPCell(new Phrase("Tổng doanh thu", font)));
        summaryTable.addCell(new PdfPCell(new Phrase(revenue.toPlainString(), font)));
        summaryTable.addCell(new PdfPCell(new Phrase("Tổng lợi nhuận", font)));
        summaryTable.addCell(new PdfPCell(new Phrase(totalProfit.toPlainString(), font)));
        summaryTable.addCell(new PdfPCell(new Phrase("Tổng số đơn hàng", font)));
        summaryTable.addCell(new PdfPCell(new Phrase(String.valueOf(totalOrders), font)));
        document.add(summaryTable);
        document.add(new Paragraph(" ", font));

        // Bảng doanh thu & lợi nhuận từng ngày
        Paragraph section1 = new Paragraph("1. Doanh thu & lợi nhuận theo ngày", fontBold);
        document.add(section1);
        PdfPTable profitTable = new PdfPTable(4);
        profitTable.setWidthPercentage(100);
        profitTable.addCell(new PdfPCell(new Phrase("Ngày", fontBold)));
        profitTable.addCell(new PdfPCell(new Phrase("Doanh thu", fontBold)));
        profitTable.addCell(new PdfPCell(new Phrase("Lợi nhuận", fontBold)));
        profitTable.addCell(new PdfPCell(new Phrase("Biên lợi nhuận (%)", fontBold)));
        for (ProfitStatDTO stat : profitStats) {
            profitTable.addCell(new PdfPCell(new Phrase(stat.getPeriod(), font)));
            profitTable.addCell(new PdfPCell(new Phrase(stat.getTotalRevenue().toPlainString(), font)));
            profitTable.addCell(new PdfPCell(new Phrase(stat.getTotalProfit().toPlainString(), font)));
            String margin = stat.getTotalRevenue().compareTo(BigDecimal.ZERO) > 0
                    ? stat.getTotalProfit().multiply(BigDecimal.valueOf(100))
                            .divide(stat.getTotalRevenue(), 2, BigDecimal.ROUND_HALF_UP).toPlainString()
                    : "0.00";
            profitTable.addCell(new PdfPCell(new Phrase(margin, font)));
        }
        document.add(profitTable);
        document.add(new Paragraph(" ", font));

        // Top sản phẩm bán chạy
        Paragraph section2 = new Paragraph("2. Top sản phẩm bán chạy", fontBold);
        document.add(section2);
        List<TopProductDTO> topProducts = getTopSellingProducts(start, end, 10);
        PdfPTable topProductTable = new PdfPTable(4);
        topProductTable.setWidthPercentage(100);
        topProductTable.addCell(new PdfPCell(new Phrase("Mã SP", fontBold)));
        topProductTable.addCell(new PdfPCell(new Phrase("Tên sản phẩm", fontBold)));
        topProductTable.addCell(new PdfPCell(new Phrase("Số lượng bán", fontBold)));
        topProductTable.addCell(new PdfPCell(new Phrase("Doanh thu", fontBold)));
        for (TopProductDTO p : topProducts) {
            topProductTable.addCell(new PdfPCell(new Phrase(String.valueOf(p.getProductId()), font)));
            topProductTable.addCell(new PdfPCell(new Phrase(p.getProductName(), font)));
            topProductTable.addCell(new PdfPCell(new Phrase(String.valueOf(p.getTotalSold()), font)));
            topProductTable.addCell(new PdfPCell(new Phrase("-", font)));
        }
        document.add(topProductTable);
        document.add(new Paragraph(" ", font));

        // Doanh thu theo danh mục
        Paragraph section3 = new Paragraph("3. Doanh thu theo danh mục", fontBold);
        document.add(section3);
        List<CategoryRevenueDTO> categoryRevenues = getRevenueByCategory(start.toLocalDate(), end.toLocalDate());
        PdfPTable categoryTable = new PdfPTable(3);
        categoryTable.setWidthPercentage(100);
        categoryTable.addCell(new PdfPCell(new Phrase("Danh mục", fontBold)));
        categoryTable.addCell(new PdfPCell(new Phrase("Doanh thu", fontBold)));
        categoryTable.addCell(new PdfPCell(new Phrase("Số đơn hàng", fontBold)));
        for (CategoryRevenueDTO c : categoryRevenues) {
            categoryTable.addCell(new PdfPCell(new Phrase(c.getCategory(), font)));
            categoryTable.addCell(new PdfPCell(new Phrase(c.getTotalRevenue().toPlainString(), font)));
            categoryTable.addCell(new PdfPCell(new Phrase(String.valueOf(c.getOrderCount()), font)));
        }
        document.add(categoryTable);
        document.add(new Paragraph(" ", font));

        // Đơn hàng theo trạng thái
        Paragraph section4 = new Paragraph("4. Đơn hàng theo trạng thái", fontBold);
        document.add(section4);
        Map<String, Long> orderStatus = getOrderCountByStatus(start, end);
        PdfPTable statusTable = new PdfPTable(2);
        statusTable.setWidthPercentage(60);
        statusTable.addCell(new PdfPCell(new Phrase("Trạng thái", fontBold)));
        statusTable.addCell(new PdfPCell(new Phrase("Số lượng", fontBold)));
        for (Map.Entry<String, Long> entry : orderStatus.entrySet()) {
            statusTable.addCell(new PdfPCell(new Phrase(getVietnameseOrderStatus(entry.getKey()), font)));
            statusTable.addCell(new PdfPCell(new Phrase(String.valueOf(entry.getValue()), font)));
        }
        document.add(statusTable);

        document.close();
        return new ByteArrayResource(out.toByteArray());
    }
}
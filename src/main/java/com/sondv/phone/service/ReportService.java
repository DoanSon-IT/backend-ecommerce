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
        Arrays.stream(com.sondv.phone.model.OrderStatus.values()).forEach(status -> {
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
                row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO  // totalRevenue
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
        XWPFParagraph title = doc.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = title.createRun();
        run.setText("BÁO CÁO DOANH THU");
        run.setBold(true);
        run.setFontSize(20);

        BigDecimal revenue = getRevenue(start, end);
        XWPFTable table = doc.createTable();
        table.getRow(0).getCell(0).setText("Chỉ tiêu");
        table.getRow(0).addNewTableCell().setText("Giá trị");
        table.createRow().getCell(0).setText("Tổng doanh thu");
        table.getRow(1).getCell(1).setText(revenue.toPlainString());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.write(out);
        doc.close();
        return new ByteArrayResource(out.toByteArray());
    }

    public ByteArrayResource exportExcelReport(LocalDateTime start, LocalDateTime end) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Thống kê doanh thu");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Ngày");
        header.createCell(1).setCellValue("Doanh thu");

        int rowIdx = 1;
        List<DailyRevenueDTO> dailyList = getDailyRevenueOptimized(start.toLocalDate(), end.toLocalDate());

        for (DailyRevenueDTO dto : dailyList) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(dto.getDate().toString());
            row.createCell(1).setCellValue(dto.getRevenue().doubleValue());
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return new ByteArrayResource(out.toByteArray());
    }

    public ByteArrayResource exportPdfReport(LocalDateTime start, LocalDateTime end) throws IOException, DocumentException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, out);
        document.open();
        document.add(new Paragraph("BÁO CÁO DOANH THU"));
        document.add(new Paragraph("Từ: " + start + " đến: " + end));

        BigDecimal revenue = getRevenue(start, end);
        PdfPTable table = new PdfPTable(2);
        table.addCell(new PdfPCell(new Phrase("Chỉ tiêu")));
        table.addCell(new PdfPCell(new Phrase("Giá trị")));
        table.addCell("Tổng doanh thu");
        table.addCell(revenue.toPlainString());

        document.add(table);
        document.close();
        return new ByteArrayResource(out.toByteArray());
    }
}
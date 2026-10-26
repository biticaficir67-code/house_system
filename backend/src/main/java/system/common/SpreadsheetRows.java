package system.common;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Bounded, value-only spreadsheet reader. It never evaluates formulas or accepts SQL. */
@Component
public class SpreadsheetRows {
    public static final int MAX_ROWS = 1000;
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    public List<List<String>> read(MultipartFile file) {
        Checks.require(!file.isEmpty() && file.getSize() <= MAX_BYTES, "请选择不超过5MB的文件");
        String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        try {
            byte[] data = file.getBytes();
            if (name.endsWith(".csv")) return csv(data);
            Checks.require(
                    name.endsWith(".xlsx") || name.endsWith(".xls"),
                    "仅支持.xlsx、.xls和UTF-8/GB18030编码的.csv文件");
            if (name.endsWith(".xlsx")) checkZip(data);
            return excel(data);
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(400, "文件无法读取，请使用模板并将公式粘贴为值；CSV请保存为UTF-8或GB18030编码");
        }
    }

    private void checkZip(byte[] data) throws IOException {
        Checks.require(data.length > 4 && data[0] == 'P' && data[1] == 'K', "文件内容不是有效的Excel工作簿");
        try (var zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            long unpacked = 0;
            int entries = 0;
            byte[] chunk = new byte[8192];
            while (zip.getNextEntry() != null) {
                Checks.require(++entries <= 300, "工作簿内容过多，请复制数据到导入模板");
                for (int size; (size = zip.read(chunk)) != -1; ) {
                    unpacked += size;
                    Checks.require(unpacked <= 20L * 1024 * 1024, "工作簿展开内容过大，请仅保留需要导入的数据");
                }
            }
        }
    }

    private List<List<String>> excel(byte[] data) throws IOException {
        try (Workbook book = WorkbookFactory.create(new ByteArrayInputStream(data))) {
            Checks.require(book.getNumberOfSheets() > 0, "工作簿没有数据表");
            Sheet sheet = book.getSheetAt(0);
            Checks.require(sheet.getLastRowNum() <= 10000, "请删除空白尾行，每次最多导入1000条数据");
            var result = new ArrayList<List<String>>();
            int columns = 0;
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    result.add(List.of());
                    continue;
                }
                Checks.require(row.getLastCellNum() <= 100, "数据列过多，请使用导入模板");
                columns = Math.max(columns, row.getLastCellNum());
                var values = new ArrayList<String>();
                for (int c = 0; c < columns; c++) {
                    Cell cell = row.getCell(c);
                    String value = "";
                    if (cell != null) {
                        Checks.require(
                                cell.getCellType() != CellType.FORMULA,
                                "第" + (r + 1) + "行包含公式，请复制并粘贴为值后导入");
                        value =
                                switch (cell.getCellType()) {
                                    case STRING -> cell.getStringCellValue();
                                    case NUMERIC ->
                                            DateUtil.isCellDateFormatted(cell)
                                                    ? cell.getLocalDateTimeCellValue()
                                                            .toLocalDate()
                                                            .toString()
                                                    : BigDecimal.valueOf(cell.getNumericCellValue())
                                                            .stripTrailingZeros()
                                                            .toPlainString();
                                    case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
                                    case ERROR ->
                                            throw new BusinessException(
                                                    400, "第" + (r + 1) + "行存在Excel错误单元格");
                                    default -> "";
                                };
                    }
                    Checks.require(value.length() <= 20000, "第" + (r + 1) + "行单元格文字过长");
                    values.add(value.trim());
                }
                result.add(values);
            }
            return result;
        }
    }

    private List<List<String>> csv(byte[] bytes) throws CharacterCodingException {
        String text;
        try {
            text = decode(bytes, StandardCharsets.UTF_8);
        } catch (CharacterCodingException error) {
            text = decode(bytes, Charset.forName("GB18030"));
        }
        if (text.startsWith("\ufeff")) text = text.substring(1);
        var result = new ArrayList<List<String>>();
        var row = new ArrayList<String>();
        var cell = new StringBuilder();
        boolean quoted = false, closed = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                        closed = true;
                    }
                } else cell.append(ch);
            } else if (ch == ',' || ch == '\n' || ch == '\r') {
                row.add(cell.toString().trim());
                cell.setLength(0);
                closed = false;
                if (ch != ',') {
                    if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                    result.add(row);
                    row = new ArrayList<>();
                    Checks.require(result.size() <= 10001, "文件行数过多，请每次最多导入1000条数据");
                }
            } else if (ch == '"') {
                Checks.require(cell.isEmpty() && !closed, "CSV引号格式不正确，请重新另存为UTF-8 CSV");
                quoted = true;
            } else {
                Checks.require(!closed || Character.isWhitespace(ch), "CSV引号之后存在多余文字");
                if (!closed) cell.append(ch);
            }
            Checks.require(cell.length() <= 20000 && row.size() <= 100, "数据列过多或单元格文字过长");
        }
        Checks.require(!quoted, "CSV引号未闭合");
        if (!row.isEmpty() || !cell.isEmpty() || closed) {
            row.add(cell.toString().trim());
            result.add(row);
        }
        return result;
    }

    private String decode(byte[] bytes, Charset charset) throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }
}

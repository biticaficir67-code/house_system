package system.report;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import system.common.ApiResponse;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAuthority('report:read')")
public class ReportController {
    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<?> overview(@ModelAttribute ReportService.Filter filter) {
        return ApiResponse.success(service.overview(filter));
    }

    @GetMapping("/{kind}")
    public ApiResponse<?> report(
            @PathVariable String kind, @ModelAttribute ReportService.Filter filter) {
        return ApiResponse.success(service.query(kind, filter));
    }

    @GetMapping("/houses/export")
    public void export(@ModelAttribute ReportService.Filter filter, HttpServletResponse response)
            throws IOException {
        var rows = service.exportHouses(filter);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=houses.csv");
        var writer = response.getWriter();
        writer.write('\uFEFF');
        List<String> fields =
                List.of(
                        "id",
                        "city",
                        "district",
                        "communityName",
                        "buildingAreaSqm",
                        "listingPriceYuan",
                        "status",
                        "dataOrigin");
        writer.println(String.join(",", fields));
        for (var row : rows) {
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) writer.write(",");
                String value = java.util.Objects.toString(row.get(fields.get(i)), "");
                String significant = value.stripLeading();
                if (!significant.isEmpty() && "=+-@".indexOf(significant.charAt(0)) >= 0)
                    value = "'" + value;
                writer.write("\"" + value.replace("\"", "\"\"") + "\"");
            }
            writer.println();
        }
    }
}

package cl.restaurante.api.health;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/empleados/importacion")
@CrossOrigin(origins = "*")
public class EmployeeImportController {

    private static final int MAX_FILAS = 500;
    private static final int MAX_TAMANO_ARCHIVO = 10 * 1024 * 1024;

    private final JdbcTemplate jdbcTemplate;
    private final ActivityService activity;
    private final RoleAccessService access;

    public EmployeeImportController(
            JdbcTemplate jdbcTemplate,
            ActivityService activity,
            RoleAccessService access
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.activity = activity;
        this.access = access;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> preview(
            @RequestParam("archivo") MultipartFile archivo,
            HttpSession session
    ) throws IOException {
        RoleAccessService.User user = access.current(session);
        if (!Set.of("ADMIN", "EMPRESA").contains(user.role())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No tienes permiso para importar personal."));
        }

        if (archivo == null || archivo.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Selecciona un archivo Excel."));
        }

        if (archivo.getSize() > MAX_TAMANO_ARCHIVO) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El archivo no puede superar los 10 MB."));
        }

        try (Workbook workbook = WorkbookFactory.create(archivo.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "El Excel no contiene hojas."));
            }

            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());

            if (header == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "La primera hoja está vacía."));
            }

            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            Map<String, Integer> columns = new HashMap<>();

            for (int i = 0; i < header.getLastCellNum(); i++) {
                String title = normalizeHeader(formatter.formatCellValue(header.getCell(i)));
                if (!title.isBlank()) {
                    columns.put(title, i);
                }
            }

            List<String> requiredColumns = List.of("NOMBRE", "RUT");
            List<String> missingColumns = new ArrayList<>();

            for (String column : requiredColumns) {
                if (!columns.containsKey(column)) {
                    missingColumns.add(column);
                }
            }

            if (!missingColumns.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Faltan columnas obligatorias en la primera fila.",
                        "columnasFaltantes", missingColumns
                ));
            }

            List<Map<String, Object>> rows = new ArrayList<>();

            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                String name = readCell(row, columns.get("NOMBRE"), formatter);
                String role = columns.containsKey("CARGO") ? readCell(row, columns.get("CARGO"), formatter) : "TRABAJADOR";
                String area = columns.containsKey("AREA") ? readCell(row, columns.get("AREA"), formatter) : "SIN ASIGNAR";
                String rut = readCell(row, columns.get("RUT"), formatter);

                if (name.isBlank() && rut.isBlank()) {
                    continue;
                }

                if (rows.size() >= MAX_FILAS) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "El archivo no puede tener más de 500 trabajadores."));
                }

                List<String> errors = new ArrayList<>();
                if (name.isBlank()) errors.add("Falta el nombre.");
                if (rut.isBlank()) errors.add("Falta el RUT.");

                Map<String, Object> previewRow = new HashMap<>();
                previewRow.put("fila", i + 1);
                previewRow.put("nombre", name);
                previewRow.put("cargo", role);
                previewRow.put("area", area);
                previewRow.put("rut", rut);
                previewRow.put("valida", errors.isEmpty());
                previewRow.put("errores", errors);
                rows.add(previewRow);
            }

            return ResponseEntity.ok(Map.of("total", rows.size(), "filas", rows));
        }
    }

    @PostMapping("/confirm")
    @Transactional
    public ResponseEntity<?> confirm(
            @RequestBody ConfirmRequest request,
            HttpSession session
    ) {
        RoleAccessService.User user = access.current(session);
        if (!Set.of("ADMIN", "EMPRESA").contains(user.role())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No tienes permiso para importar personal."));
        }

        if (request == null || request.filas() == null || request.filas().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No hay trabajadores para guardar."));
        }

        if (request.filas().size() > MAX_FILAS) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Solo puedes importar hasta 500 trabajadores por archivo."));
        }

        List<String> errors = new ArrayList<>();
        Set<String> rutsInFile = new HashSet<>();

        for (int i = 0; i < request.filas().size(); i++) {
            ImportEmployee employee = request.filas().get(i);
            int spreadsheetRow = i + 2;

            if (employee == null) {
                errors.add("Fila " + spreadsheetRow + ": está vacía.");
                continue;
            }

            String name = clean(employee.nombre());
            String role = clean(employee.cargo()).isBlank() ? "TRABAJADOR" : clean(employee.cargo());
            String area = clean(employee.area()).isBlank() ? "SIN ASIGNAR" : clean(employee.area());
            String rut = clean(employee.rut());
            String rutKey = normalizeRut(rut);

            if (name.isBlank()) errors.add("Fila " + spreadsheetRow + ": falta el nombre.");
            if (rutKey.isBlank()) errors.add("Fila " + spreadsheetRow + ": falta el RUT.");

            if (name.length() > 160) errors.add("Fila " + spreadsheetRow + ": el nombre supera 160 caracteres.");
            if (role.length() > 80) errors.add("Fila " + spreadsheetRow + ": el cargo supera 80 caracteres.");
            if (area.length() > 80) errors.add("Fila " + spreadsheetRow + ": el área supera 80 caracteres.");
            if (rut.length() > 20) errors.add("Fila " + spreadsheetRow + ": el RUT supera 20 caracteres.");

            if (!rutKey.isBlank() && !rutsInFile.add(rutKey)) {
                errors.add("Fila " + spreadsheetRow + ": el RUT está repetido en el Excel.");
            }

            if (!rutKey.isBlank()) {
                Integer existing = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM APP_EMPLEADO " +
                                "WHERE UPPER(REPLACE(REPLACE(REPLACE(RUT, '.', ''), '-', ''), ' ', '')) = ?",
                        Integer.class,
                        rutKey
                );
                if (existing != null && existing > 0) {
                    errors.add("Fila " + spreadsheetRow + ": ese RUT ya existe en el personal.");
                }
            }
        }

        if (!errors.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Revisa las filas indicadas. No se guardó ningún trabajador.",
                    "detalles", errors
            ));
        }

        String sql = "INSERT INTO APP_EMPLEADO (NOMBRE, CARGO, AREA, RUT, ESTADO) " +
                "VALUES (?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, request.filas(), request.filas().size(), (statement, employee) -> {
            statement.setString(1, clean(employee.nombre()));
            statement.setString(2, clean(employee.cargo()));
            statement.setString(3, clean(employee.area()));
            statement.setString(4, clean(employee.rut()).toUpperCase(Locale.ROOT));
            statement.setString(5, "ACTIVO");
        });

        activity.record(
                session,
                "IMPORTAR",
                "Personal",
                "Se importaron " + request.filas().size() + " trabajadores"
        );

        return ResponseEntity.ok(Map.of(
                "status", "guardado",
                "importados", request.filas().size()
        ));
    }

    private static String readCell(Row row, int column, DataFormatter formatter) {
        if (column < 0 || row.getCell(column) == null) {
            return "";
        }
        return formatter.formatCellValue(row.getCell(column)).trim();
    }

    private static String normalizeHeader(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeRut(String rut) {
        return clean(rut)
                .toUpperCase(Locale.ROOT)
                .replace(".", "")
                .replace("-", "")
                .replace(" ", "");
    }

    public record ConfirmRequest(List<ImportEmployee> filas) {}

    public record ImportEmployee(String nombre, String cargo, String area, String rut) {}
}

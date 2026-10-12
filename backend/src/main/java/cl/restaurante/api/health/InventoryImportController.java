package cl.restaurante.api.health;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/inventario/importacion")
public class InventoryImportController {

    private static final int MAX_FILAS = 500;
    private static final long MAX_ARCHIVO = 10L * 1024L * 1024L;

    private static final List<String> COLUMNAS = List.of(
            "INSUMO",
            "CATEGORIA",
            "STOCK_ACTUAL",
            "UNIDAD",
            "STOCK_MINIMO",
            "STOCK_MAXIMO",
            "GRUPO"
    );

    private final JdbcTemplate jdbc;
    private final ActivityService activity;
    private final RoleAccessService access;

    public InventoryImportController(
            JdbcTemplate jdbc,
            ActivityService activity,
            RoleAccessService access
    ) {
        this.jdbc = jdbc;
        this.activity = activity;
        this.access = access;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> preview(
            @RequestParam("archivo") MultipartFile archivo,
            HttpSession session
    ) throws IOException {
        if (!access.isOwner(access.current(session))) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No tienes permiso para importar inventario."));
        }

        if (archivo == null || archivo.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Selecciona un archivo Excel."));
        }

        if (archivo.getSize() > MAX_ARCHIVO) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El archivo no puede superar los 10 MB."));
        }

        try (Workbook workbook = WorkbookFactory.create(archivo.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "El Excel no contiene hojas."));
            }

            Sheet sheet = workbook.getSheetAt(0);
            Row encabezado = sheet.getRow(sheet.getFirstRowNum());

            if (encabezado == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "La primera hoja está vacía."));
            }

            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            Map<String, Integer> posiciones = new HashMap<>();

            for (int i = 0; i < encabezado.getLastCellNum(); i++) {
                String nombre = normalizarEncabezado(
                        formatter.formatCellValue(encabezado.getCell(i))
                );
                if (!nombre.isBlank()) {
                    posiciones.put(nombre, i);
                }
            }

            List<String> faltantes = COLUMNAS.stream()
                    .filter(columna -> !posiciones.containsKey(columna))
                    .toList();

            if (!faltantes.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Revisa los encabezados del Excel.",
                        "columnasFaltantes", faltantes
                ));
            }

            List<Map<String, Object>> filas = new ArrayList<>();

            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row fila = sheet.getRow(i);
                if (fila == null) continue;

                Insumo insumo = new Insumo(
                        celda(fila, posiciones.get("INSUMO"), formatter),
                        celda(fila, posiciones.get("CATEGORIA"), formatter),
                        celda(fila, posiciones.get("STOCK_ACTUAL"), formatter),
                        celda(fila, posiciones.get("UNIDAD"), formatter),
                        celda(fila, posiciones.get("STOCK_MINIMO"), formatter),
                        celda(fila, posiciones.get("STOCK_MAXIMO"), formatter),
                        celda(fila, posiciones.get("GRUPO"), formatter)
                );

                if (insumo.vacio()) continue;

                if (filas.size() >= MAX_FILAS) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Puedes importar hasta 500 insumos."));
                }

                List<String> errores = validar(insumo);
                Map<String, Object> resultado = new HashMap<>();
                resultado.put("fila", i + 1);
                resultado.put("insumo", insumo.insumo());
                resultado.put("categoria", insumo.categoria());
                resultado.put("stockActual", insumo.stockActual());
                resultado.put("unidad", insumo.unidad());
                resultado.put("stockMinimo", insumo.stockMinimo());
                resultado.put("stockMaximo", insumo.stockMaximo());
                resultado.put("grupo", insumo.grupo());
                resultado.put("valida", errores.isEmpty());
                resultado.put("errores", errores);
                filas.add(resultado);
            }

            return ResponseEntity.ok(Map.of("total", filas.size(), "filas", filas));
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No se pudo leer el Excel. Revisa el archivo."));
        }
    }

    @PostMapping("/confirm")
    @Transactional
    public ResponseEntity<?> confirm(
            @RequestBody ImportRequest request,
            HttpSession session
    ) {
        if (!access.isOwner(access.current(session))) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No tienes permiso para importar inventario."));
        }

        if (request == null || request.filas() == null || request.filas().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No hay insumos para guardar."));
        }

        if (request.filas().size() > MAX_FILAS) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Puedes importar hasta 500 insumos."));
        }

        List<String> errores = new ArrayList<>();

        for (int i = 0; i < request.filas().size(); i++) {
            Insumo insumo = request.filas().get(i);
            if (insumo == null) {
                errores.add("Fila " + (i + 2) + ": está vacía.");
                continue;
            }

            for (String error : validar(insumo)) {
                errores.add("Fila " + (i + 2) + ": " + error);
            }
        }

        if (!errores.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Revisa las filas indicadas. No se guardó ningún insumo.",
                    "detalles", errores
            ));
        }

        String sql = """
                INSERT INTO APP_INSUMO
                    (NOMBRE, CATEGORIA, STOCK_ACTUAL, UNIDAD,
                     STOCK_MINIMO, STOCK_MAXIMO, GRUPO)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        jdbc.batchUpdate(sql, request.filas(), request.filas().size(), (ps, insumo) -> {
            ps.setString(1, insumo.insumo().trim());
            ps.setString(2, insumo.categoria().trim());
            ps.setBigDecimal(3, numero(insumo.stockActual()));
            ps.setString(4, insumo.unidad().trim());
            ps.setBigDecimal(5, numero(insumo.stockMinimo()));

            if (insumo.stockMaximo() == null || insumo.stockMaximo().isBlank()) {
                ps.setNull(6, java.sql.Types.NUMERIC);
            } else {
                ps.setBigDecimal(6, numero(insumo.stockMaximo()));
            }

            ps.setString(7, insumo.grupo().trim().toUpperCase(Locale.ROOT));
        });

        activity.record(
                session,
                "IMPORTAR",
                "Inventario",
                "Se importaron " + request.filas().size() + " insumos"
        );

        return ResponseEntity.ok(Map.of(
                "status", "guardado",
                "importados", request.filas().size()
        ));
    }

    private static List<String> validar(Insumo insumo) {
        List<String> errores = new ArrayList<>();

        if (texto(insumo.insumo()).isBlank()) errores.add("falta el nombre del insumo.");
        if (texto(insumo.categoria()).isBlank()) errores.add("falta la categoría.");
        if (texto(insumo.unidad()).isBlank()) errores.add("falta la unidad.");

        if (texto(insumo.insumo()).length() > 160) errores.add("el insumo supera 160 caracteres.");
        if (texto(insumo.categoria()).length() > 80) errores.add("la categoría supera 80 caracteres.");
        if (texto(insumo.unidad()).length() > 30) errores.add("la unidad supera 30 caracteres.");

        BigDecimal actual = validarNumero(insumo.stockActual(), "stock actual", errores);
        BigDecimal minimo = validarNumero(insumo.stockMinimo(), "stock mínimo", errores);
        BigDecimal maximo = insumo.stockMaximo() == null || insumo.stockMaximo().isBlank()
                ? null
                : validarNumero(insumo.stockMaximo(), "stock máximo", errores);

        if (actual != null && actual.signum() < 0) errores.add("el stock actual no puede ser negativo.");
        if (minimo != null && minimo.signum() < 0) errores.add("el stock mínimo no puede ser negativo.");
        if (maximo != null && maximo.signum() < 0) errores.add("el stock máximo no puede ser negativo.");
        if (maximo != null && minimo != null && maximo.compareTo(minimo) < 0) {
            errores.add("el stock máximo debe ser igual o mayor que el mínimo.");
        }

        String grupo = texto(insumo.grupo()).toUpperCase(Locale.ROOT);
        if (!grupo.equals("COCINA") && !grupo.equals("BARRA")) {
            errores.add("el grupo debe ser COCINA o BARRA.");
        }

        return errores;
    }

    private static BigDecimal validarNumero(String valor, String nombre, List<String> errores) {
        try {
            if (valor == null || valor.isBlank()) {
                errores.add("falta el " + nombre + ".");
                return null;
            }
            return numero(valor);
        } catch (NumberFormatException error) {
            errores.add("el " + nombre + " debe ser un número.");
            return null;
        }
    }

    private static BigDecimal numero(String valor) {
        String limpio = valor.trim();
        if (limpio.contains(",") && !limpio.contains(".")) {
            limpio = limpio.replace(',', '.');
        }
        return new BigDecimal(limpio);
    }

    private static String celda(Row fila, int columna, DataFormatter formatter) {
        if (columna < 0 || fila.getCell(columna) == null) return "";
        return formatter.formatCellValue(fila.getCell(columna)).trim();
    }

    private static String normalizarEncabezado(String valor) {
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_");
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    public record ImportRequest(List<Insumo> filas) {}

    public record Insumo(
            String insumo,
            String categoria,
            String stockActual,
            String unidad,
            String stockMinimo,
            String stockMaximo,
            String grupo
    ) {
        public boolean vacio() {
            return texto(insumo).isBlank()
                    && texto(categoria).isBlank()
                    && texto(stockActual).isBlank()
                    && texto(unidad).isBlank()
                    && texto(stockMinimo).isBlank()
                    && texto(stockMaximo).isBlank()
                    && texto(grupo).isBlank();
        }
    }
}
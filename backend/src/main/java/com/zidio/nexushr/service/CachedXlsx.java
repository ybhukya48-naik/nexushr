package com.zidio.nexushr.service;

import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xssf.model.StylesTable;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reads cached Excel values with a streaming parser.
 * The salary workbooks expand to hundreds of thousands of cells; a full
 * in-memory workbook plus formula recalculation exhausts the Render heap
 * and the proxy answers 502 before the application can send CORS headers.
 */
public final class CachedXlsx {

    /** Header and title rows are always kept. Payroll headers sit inside this window. */
    public static final int HEADER_LAST_ROW = 20;

    /** Stop retaining cells past this point so one workbook cannot exhaust the heap. */
    static final int MAX_STORED_CELLS = 20_000;

    private CachedXlsx() {
    }

    public interface SheetPlan {
        /**
         * Columns to retain after {@link #HEADER_LAST_ROW}.
         * {@code null} keeps every column. An empty set keeps none.
         */
        Set<Integer> columnsAfterHeader(String sheetName, XlsxSheet headerRows);

        /**
         * A later row is stored only when every one of these columns has a real value.
         * {@code null} keeps every later row that still has retained cells.
         */
        Set<Integer> identityColumns(String sheetName, XlsxSheet headerRows);
    }

    public static List<String> sheetNames(Path path) throws IOException {
        ZipSecureFile.setMinInflateRatio(0.001);
        try (OPCPackage pkg = OPCPackage.open(path.toFile())) {
            XSSFReader reader = new XSSFReader(pkg);
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            List<String> names = new ArrayList<>();
            while (sheets.hasNext()) {
                try (InputStream ignored = sheets.next()) {
                    names.add(sheets.getSheetName());
                }
            }
            return names;
        } catch (OpenXML4JException ex) {
            throw new IOException("Unable to read workbook sheets", ex);
        }
    }

    public static List<XlsxSheet> read(Path path, SheetPlan plan) throws IOException {
        ZipSecureFile.setMinInflateRatio(0.001);
        try (OPCPackage pkg = OPCPackage.open(path.toFile())) {
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg, false);
            XSSFReader reader = new XSSFReader(pkg);
            StylesTable styles = reader.getStylesTable();
            DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            List<XlsxSheet> loaded = new ArrayList<>();

            while (sheets.hasNext()) {
                try (InputStream in = sheets.next()) {
                    XlsxSheet sheet = new XlsxSheet(sheets.getSheetName());
                    XMLReader parser = XMLHelper.newXMLReader();
                    parser.setContentHandler(new XSSFSheetXMLHandler(
                            styles,
                            strings,
                            new SheetCollector(sheet, plan),
                            formatter,
                            false
                    ));
                    parser.parse(new InputSource(in));
                    loaded.add(sheet);
                }
            }
            return loaded;
        } catch (OpenXML4JException | SAXException | ParserConfigurationException ex) {
            throw new IOException("Unable to read workbook sheets", ex);
        }
    }

    public static final class XlsxSheet {
        private final String name;
        private final NavigableMap<Integer, XlsxRow> rows = new TreeMap<>();

        XlsxSheet(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public int getLastRowNum() {
            return rows.isEmpty() ? -1 : rows.lastKey();
        }

        public XlsxRow getRow(int rowIndex) {
            return rows.get(rowIndex);
        }

        void put(XlsxRow row) {
            rows.put(row.index(), row);
        }

        public int storedCellCount() {
            int count = 0;
            for (XlsxRow row : rows.values()) {
                count += row.cells.size();
            }
            return count;
        }
    }

    public static final class XlsxRow {
        private final int index;
        private final Map<Integer, String> cells = new HashMap<>();

        XlsxRow(int index) {
            this.index = index;
        }

        public int index() {
            return index;
        }

        public String get(int column) {
            if (column < 0) {
                return "";
            }
            return cells.getOrDefault(column, "");
        }

        public int getLastCellNum() {
            int last = -1;
            for (int column : cells.keySet()) {
                if (column > last) {
                    last = column;
                }
            }
            return last + 1;
        }

        public Iterable<Map.Entry<Integer, String>> cells() {
            return cells.entrySet();
        }

        void put(int column, String value) {
            cells.put(column, value);
        }
    }

    private static final class SheetCollector implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final XlsxSheet sheet;
        private final SheetPlan plan;
        private XlsxRow current;
        private boolean planned;
        private boolean full;
        private int storedCells;
        private Set<Integer> keepColumns;
        private Set<Integer> identityColumns;

        private SheetCollector(XlsxSheet sheet, SheetPlan plan) {
            this.sheet = sheet;
            this.plan = plan;
        }

        @Override
        public void startRow(int rowNum) {
            if (!planned && rowNum > HEADER_LAST_ROW) {
                freezePlan();
            }
            current = new XlsxRow(rowNum);
        }

        @Override
        public void endRow(int rowNum) {
            if (current == null) {
                return;
            }
            if (planned && rowNum > HEADER_LAST_ROW && !keepIdentity(current)) {
                current = null;
                return;
            }
            if (current.getLastCellNum() > 0) {
                int cells = 0;
                for (Map.Entry<Integer, String> ignored : current.cells()) {
                    cells++;
                }
                if (storedCells + cells > MAX_STORED_CELLS && rowNum > HEADER_LAST_ROW) {
                    full = true;
                    current = null;
                    return;
                }
                storedCells += cells;
                sheet.put(current);
            }
            current = null;
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            if (full || current == null || cellReference == null || formattedValue == null) {
                return;
            }
            String value = formattedValue.trim();
            if (value.isEmpty()) {
                return;
            }
            int column = columnIndex(cellReference);
            if (column < 0) {
                return;
            }
            if (planned
                    && current.index() > HEADER_LAST_ROW
                    && keepColumns != null
                    && !keepColumns.contains(column)) {
                return;
            }
            current.put(column, value);
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
        }

        private void freezePlan() {
            planned = true;
            if (plan == null) {
                return;
            }
            keepColumns = plan.columnsAfterHeader(sheet.getName(), sheet);
            identityColumns = plan.identityColumns(sheet.getName(), sheet);
        }

        private boolean keepIdentity(XlsxRow row) {
            if (identityColumns == null || identityColumns.isEmpty()) {
                return true;
            }
            for (int column : identityColumns) {
                if (!meaningful(row.get(column))) {
                    return false;
                }
            }
            return true;
        }
    }

    static boolean meaningful(String value) {
        if (value == null) {
            return false;
        }
        String text = value.trim();
        return !text.isEmpty() && !text.equals("0") && !text.equals("0.0") && !text.equals("0.00");
    }

    static int columnIndex(String cellReference) {
        int column = 0;
        int i = 0;
        while (i < cellReference.length()) {
            char ch = cellReference.charAt(i);
            if (ch >= 'a' && ch <= 'z') {
                ch = (char) (ch - 32);
            }
            if (ch < 'A' || ch > 'Z') {
                break;
            }
            column = column * 26 + (ch - 'A' + 1);
            i++;
        }
        return i == 0 ? -1 : column - 1;
    }
}

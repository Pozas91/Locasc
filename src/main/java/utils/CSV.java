package utils;

import models.enums.Header;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class CSV {
    /**
     * Save data given into a CSV file
     *
     * @param data     Data to save into CSV file
     * @param fileName Name of the CSV file
     */
    public static void
    save(List<Header> headers, Map<Header, List<Object>> data, String fileName) throws IOException {
        // Today
        final String date = (new SimpleDateFormat("yyyy_MM_dd")).format(new Date());
        // Get file and create folders
        File file = ProjectPath.fromDataFile(date, String.format("%s.csv", fileName));
        // Create and open the file
        FileWriter out = new FileWriter(file);
        // Getting keys of maps for the headers
        String[] HEADERS = headers.stream().map(Enum::toString).toArray(String[]::new);
        // Get data size
        int dataSize = data.get(headers.get(0)).size();

        // Write data with transpose information
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader(HEADERS))) {
            // Get size of data
            for (int i = 0; i < dataSize; i++) {
                // Prepare record
                List<Object> record = new ArrayList<>();
                // For each header, get data from correct position and save it.
                for (Header h : headers) {
                    record.add(data.get(h).get(i));
                }
                // Print the record
                printer.printRecord(record);
            }
        } catch (IndexOutOfBoundsException e) {
            for (Map.Entry<Header, List<Object>> entry : data.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    System.out.printf("%s attribute information is empty.%n", entry.getKey());
                }
            }
            throw new IllegalArgumentException("Below attributes to save are empty, please check out!");
        }
    }
}

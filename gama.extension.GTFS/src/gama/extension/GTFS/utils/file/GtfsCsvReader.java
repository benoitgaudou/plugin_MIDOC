package gama.extension.GTFS.utils.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

public class GtfsCsvReader {
	
    public static char detectSeparator(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Ignore les lignes vides
                if (line.trim().isEmpty()) continue;

                // Compte virgules/points-virgules hors guillemets
                int commaCount = 0, semicolonCount = 0;
                boolean inQuotes = false;
                for (char c : line.toCharArray()) {
                    if (c == '"') inQuotes = !inQuotes;
                    if (!inQuotes) {
                        if (c == ',') commaCount++;
                        if (c == ';') semicolonCount++;
                    }
                }
                if (semicolonCount > commaCount) return ';';
                else return ','; // Virgule par défaut
            }
        }
        // Par défaut, virgule
        return ',';
    }
    
    public static String[] parseCsvLine(String line, char separator) {
        try {
            CSVParser parser = new CSVParserBuilder().withSeparator(separator).build();
            return parser.parseLine(line);
        } catch (Exception e) {
            System.err.println("[ERROR] CSV parsing failed: " + line);
            return null;
        }
    }

    /**
     * Reads a CSV file 
     */
    public static List<String[]> readCsvFileOpenCSV(File file, Map<String, Integer> headerMap) throws IOException, CsvValidationException {
        List<String[]> content = new ArrayList<>();
        if (!file.isFile()) {
            throw new IOException(file.getAbsolutePath() + " is not a valid file.");
        }

        char separator = GtfsCsvReader.detectSeparator(file);
 
        try (CSVReader reader = new CSVReaderBuilder(new FileReader(file))
                                    .withSkipLines(0)
                                    .withCSVParser(new CSVParserBuilder().withSeparator(separator).build())
                                    .build()) {
            // Lis et nettoie le header
            String[] headers = reader.readNext();
            while (headers != null && headers.length == 1 && headers[0].trim().isEmpty()) {
                headers = reader.readNext();
            }
            if (headers != null) {
                for (int i = 0; i < headers.length; i++) {
                    String col = headers[i].trim().replace("\uFEFF", "").toLowerCase();
                    headerMap.put(col, i);
                }
            }
            //System.out.println("Headers trouvés dans " + file.getName() + " : " + headerMap.keySet());
            String[] line;
            while ((line = reader.readNext()) != null) {
                // Complète les champs manquants (à droite)
                if (line.length < headerMap.size()) {
                    String[] newLine = new String[headerMap.size()];
                    System.arraycopy(line, 0, newLine, 0, line.length);
                    for (int i = line.length; i < headerMap.size(); i++) {
                        newLine[i] = "";
                    }
                    line = newLine;
                }
                // Ignore les lignes totalement vides
                boolean isEmpty = true;
                for (String field : line) {
                    if (field != null && !field.trim().isEmpty()) {
                        isEmpty = false;
                        break;
                    }
                }
                if (isEmpty) continue;
                content.add(line); // Ajoute le tableau de champs
            }
        }
        //System.out.println("⇒ Fichier '" + file.getName() + "' : " + content.size() + " lignes lues.");
        return content;
    }
    

}

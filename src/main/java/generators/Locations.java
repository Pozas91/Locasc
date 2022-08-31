package generators;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import models.geo.Location;
import utils.ProjectPath;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public final class Locations {
    private static Locations _instance;
    private final List<Location> _selectedPoints;

    private Locations() {
        File file = ProjectPath.fromDataFile("csv", "countries.csv");
        Reader reader;
        List<String[]> rawCountries;

        try {
            reader = new FileReader(file);
            rawCountries = readCountries(reader);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Cannot read countries file successfully, please check it.");
        }

        // Get only a sample of points to operate with them
        List<Location> locations = rawCountries.parallelStream().map(Location::of).collect(Collectors.toList());
        Collections.shuffle(locations);
        _selectedPoints = locations.subList(0, 10);
    }

    public static List<Location> get() {
        if (_instance == null) {
            _instance = new Locations();
        }

        return _instance._selectedPoints;
    }

    private static List<String[]> readCountries(Reader reader) throws IOException, CsvException {
        // Read from reader indicated
        CSVReader csvReader = new CSVReader(reader);
        List<String[]> list = csvReader.readAll();

        // Remove header
        list.remove(0);

        // Close files
        reader.close();
        csvReader.close();

        return list;
    }

    public static Location getRandom(Random rnd) {
        List<Location> locations = get();

        return locations.get(rnd.nextInt(locations.size()));
    }
}

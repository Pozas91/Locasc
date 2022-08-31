package models.analyzers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import models.applications.Application;
import models.applications.Provider;
import models.enums.QoS;
import models.patterns.Architecture;
import models.patterns.Component;
import models.patterns.IndexService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

enum Format {
    Json("json"), Yaml("yml"), CSV("csv");

    private final String _name;

    Format(String name) {
        _name = name;
    }

    public static Format from(String format) {
        return switch (format) {
            case "json" -> Format.Json;
            case "yml" -> Format.Yaml;
            default -> Format.CSV;
        };
    }

    public String toString() {
        return _name;
    }
}

public class Extractor {
    private final Application _app;
    private final Format _format;

    public Extractor(Application app) {
        _app = app;
        _format = Format.Json;
    }

    public Extractor(Application app, String format) {
        _app = app;
        _format = Format.from(format);
    }

    /**
     * Extract architecture from the indicated application in the path selected
     *
     * @param path A string to indicate the path to save information
     */
    public void extractArchitecture(String path) {
        // Build a json architecture
        JsonObject root = build(_app.getArchitecture());
        // Prepare instance to the writer
        FileWriter fileWriter;

        try {
            // Get instance of writer
            fileWriter = _preparePath(path + ".json");
            // Save information
            fileWriter.write(root.toString());
            // Always must close writer
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Extract solution from a genotype given
     *
     * @param path     A string to indicate the path to save information
     * @param genotype A list with the solutions for each service
     */
    public void extractSolution(String path, List<Integer> genotype) {
        switch (_format) {
            case Json -> extractSolutionJson(path, genotype);
            case CSV -> extractSolutionCSV(path, genotype);
            case Yaml -> throw new IllegalArgumentException("Unknown format indicated");
        }
    }

    public void extractSolutionJson(String path, List<Integer> genotype) {
        // Build a json architecture
        JsonArray root = new JsonArray();

        for (int i = 0; i < genotype.size(); i++) {
            // Create instance of json object
            JsonObject element = new JsonObject();

            // Set properties to the json element
            String name = build(_app.getArchitecture(), i);
            element.addProperty("name", name);
            element.addProperty("provider", genotype.get(i));

            // Add element
            root.add(element);
        }

        // Prepare instance to the writer
        FileWriter fileWriter;

        try {
            // Get instance of writer
            fileWriter = _preparePath(String.format("%s.%s", path, _format));
            // Save information
            fileWriter.write(root.toString());
            // Always must close writer
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void extractSolutionCSV(String path, List<Integer> genotype) {
        try {
            FileWriter fileWriter = _preparePath(String.format("%s.%s", path, _format));
            String[] headers = new String[]{"PATH", "PROVIDER"};
            // Define CSV printer
            CSVPrinter printer = new CSVPrinter(fileWriter, CSVFormat.DEFAULT.withHeader(headers));

            for (int i = 0; i < genotype.size(); i++) {
                String name = build(_app.getArchitecture(), i);
                String provider = String.valueOf(genotype.get(i));

                printer.printRecord(name, provider);
            }

            printer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Extract providers from the indicated application in the path selected
     *
     * @param path A string to indicate the path to save information
     */
    public void extractProviders(String path) {
        // Build a json providers
        JsonArray root = build(_app.getProviders());
        // Prepare instance to the writer
        FileWriter fileWriter;

        try {
            // Get instance of writer
            fileWriter = _preparePath(path + ".json");
            // Save information
            fileWriter.write(root.toString());
            // Always must close writer
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Internal recursive method to build a JsonObject from architecture
     *
     * @param root Root architecture
     * @return A JsonObject that represent the architecture
     */
    private JsonObject build(Architecture root) {
        // Prepare json root
        JsonObject jsonRoot = new JsonObject();

        // Prepare components array
        JsonArray components = new JsonArray();

        // Set root
        jsonRoot.addProperty("type", root.getName());
        jsonRoot.add("components", components);

        // Prepare services list
        JsonArray services = new JsonArray();

        // For each component
        for (Component son : root.getComponents()) {
            if (son.isBase()) {
                // If component is base, then is a single service and get it index.
                IndexService simpleSon = (IndexService) son;
                services.add(simpleSon.getIService());
                jsonRoot.add("services", services);
            } else {
                // Else, component is an architecture and call recursively this method.
                Architecture complexSon = son.getArchitecture();
                components.add(build(complexSon));
            }
        }

        // If this architecture hasn't any nested component, then remove this property.
        if (components.isEmpty()) {
            jsonRoot.remove("components");
        }

        return jsonRoot;
    }

    /**
     * Internal method to build a JsonObject from providers
     *
     * @param providers List of providers to build the response
     * @return A JsonObject that represent the architecture
     */
    private JsonArray build(List<Provider> providers) {
        // Prepare providers json array
        JsonArray jsonProviders = new JsonArray();

        // For each provider
        for (Provider p : providers) {
            // Prepare json provider information
            JsonObject jsonProvider = new JsonObject();
            // Add name
            jsonProvider.addProperty("name", p.getName());

            // For each qos attribute
            for (QoS qos : p.getAttributes().keySet()) {
                jsonProvider.addProperty(qos.toString(), p.getAttributeValue(qos));
            }

            // Add this provider to list
            jsonProviders.add(jsonProvider);
        }

        return jsonProviders;
    }

    /**
     * Internal method to build a JsonObject from solution
     *
     * @param component The component where search the service indicate
     * @param iService  Index of the service to search
     */
    private String build(Component component, Integer iService) {

        if (component.isBase()) {
            IndexService service = (IndexService) component;

            if (service.getIService().equals(iService)) {
                return "";
            }
        } else {
            Architecture architecture = component.getArchitecture();

            for (Component subComponent : architecture.getComponents()) {
                String name = build(subComponent, iService);

                if (name != null) {
                    return String.format("%s %s", architecture.getName(), name);
                }
            }
        }

        return null;
    }

    /**
     * This method returns the file to save all information to export
     *
     * @param path Name of the file
     * @return A File instance
     */
    private FileWriter _preparePath(String path) throws IOException {
        // 1. Get today
        final String date = (new SimpleDateFormat("yyyy_MM_dd")).format(new Date());
        final String now = (new SimpleDateFormat("HH_mm_ss_SSS")).format(new Date());
        // 2. Define full path
        String fullPath = String.format("%s\\data\\%s\\%s - %s", System.getProperty("user.dir"), date, now, path);
        // 3. Get the file instance
        File file = new File(fullPath);
        // 4. Create parents if is necessary
        file.getParentFile().mkdirs();
        // 5. Create and open the file
        return new FileWriter(file);
    }
}

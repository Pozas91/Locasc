package utils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ProjectPath {
    public static Path fromDataPath(String... paths) {
        // Create base path from project's data directory
        Path path = Paths.get(System.getProperty("user.dir"), "data");

        // Add all fragments to get full path
        for (String p : paths) {
            path = path.resolve(p);
        }

        return path;
    }

    public static File fromDataFile(String... paths) {
        // Prepare full path
        Path path = fromDataPath(paths);

        // Get file from path
        File file = path.toFile();

        // Make sure that parents of that file exists
        boolean parentsCreated = file.getParentFile().mkdirs();

        return file;
    }
}

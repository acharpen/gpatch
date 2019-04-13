package com.yac.gpatch.util;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileUtils {

    public static List<Path> getAllPathsByExtension(Path path, String extension) throws IOException {
        String formattedExtension = extension.startsWith(".") ? extension
                : new StringBuilder().append(".").append(extension).toString();
        PathMatcher pathMatcher = FileSystems.getDefault()
                .getPathMatcher(new StringBuilder().append("glob:**/*").append(formattedExtension).toString());

        try (Stream<Path> walk = Files.walk(path)) {
            return walk.filter(Files::isRegularFile).filter(Files::isReadable).filter(Files::isWritable)
                    .filter(pathMatcher::matches).collect(Collectors.toList());
        }
    }

}

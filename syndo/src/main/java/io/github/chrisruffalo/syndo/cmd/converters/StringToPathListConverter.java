package io.github.chrisruffalo.syndo.cmd.converters;

import com.beust.jcommander.IStringConverter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class StringToPathListConverter implements IStringConverter<List<Path>> {

    @Override
    public List<Path> convert(String s) {
        if (s == null || s.isEmpty()) {
            return Collections.singletonList(Paths.get(""));
        }
        s = s.trim();
        if (!s.contains(",")) {
            return Collections.singletonList(Paths.get(s));
        }
        final String[] paths = s.split(",");
        return Arrays.stream(paths)
                .filter(Objects::nonNull)
                .map(pathString -> Paths.get(pathString.trim()))
                .collect(Collectors.toList());
    }
}

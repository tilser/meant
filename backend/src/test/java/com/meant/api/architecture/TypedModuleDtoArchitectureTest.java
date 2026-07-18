package com.meant.api.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TypedModuleDtoArchitectureTest {

    private static final Pattern GUARDED_SOURCE = Pattern.compile("com/meant/api/.*\\.java");
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^package\\s+([\\w.]+)\\s*;");

    @Test
    void productionRecordsUseConcreteComponentTypes() throws IOException, ClassNotFoundException {
        Path sourceRoot = productionSourceRoot();
        List<String> violations = new ArrayList<>();
        List<Path> guardedSources = guardedSources(sourceRoot);

        assertThat(guardedSources)
                .as("production Java sources")
                .isNotEmpty();

        for (Path source : guardedSources) {
            Class<?> sourceType = Class.forName(className(source), false, getClass().getClassLoader());
            inspect(sourceType, violations);
        }

        assertThat(violations)
                .as("production records must not use Object, raw containers, or wildcards%n%s",
                        String.join(System.lineSeparator(), violations))
                .isEmpty();
    }

    private void inspect(Class<?> type, List<String> violations) {
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                if (containsForbiddenType(component.getGenericType())) {
                    violations.add(type.getName() + "." + component.getName()
                            + ": " + component.getGenericType().getTypeName());
                }
            }
        }
        Arrays.stream(type.getDeclaredClasses()).forEach(nested -> inspect(nested, violations));
    }

    private boolean containsForbiddenType(Type type) {
        if (type == Object.class) {
            return true;
        }
        if (type instanceof Class<?> typeClass) {
            if (Map.class.isAssignableFrom(typeClass) || Collection.class.isAssignableFrom(typeClass)) {
                return true;
            }
            return typeClass.isArray() && containsForbiddenType(typeClass.getComponentType());
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return Arrays.stream(parameterizedType.getActualTypeArguments())
                    .anyMatch(this::containsForbiddenType);
        }
        if (type instanceof GenericArrayType arrayType) {
            return containsForbiddenType(arrayType.getGenericComponentType());
        }
        if (type instanceof WildcardType) {
            return true;
        }
        if (type instanceof TypeVariable<?>) {
            return false;
        }
        return false;
    }

    private List<Path> guardedSources(Path sourceRoot) throws IOException {
        try (var sources = Files.walk(sourceRoot.resolve("com/meant/api"))) {
            return sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> GUARDED_SOURCE.matcher(relativePath(sourceRoot, path)).matches())
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private String className(Path source) throws IOException {
        var packageMatcher = PACKAGE_DECLARATION.matcher(Files.readString(source));
        if (!packageMatcher.find()) {
            throw new IllegalStateException("Production source has no package declaration: " + source);
        }
        String simpleName = source.getFileName().toString().replaceFirst("\\.java$", "");
        return packageMatcher.group(1) + "." + simpleName;
    }

    private String relativePath(Path sourceRoot, Path source) {
        return sourceRoot.relativize(source).toString().replace('\\', '/');
    }

    private Path productionSourceRoot() {
        Path backendRoot = Path.of("src/main/java");
        return Files.isDirectory(backendRoot) ? backendRoot : Path.of("backend/src/main/java");
    }
}

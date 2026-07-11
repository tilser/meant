package com.meant.api.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PackageOwnershipArchitectureTest {

    private static final Pattern PLUGIN_FORBIDDEN_IMPORT = Pattern.compile(
            "^import com\\.meant\\.api\\.(module|provider)\\..*"
    );
    private static final Pattern MODULE_PROVIDER_IMPORT = Pattern.compile(
            "^import com\\.meant\\.api\\.provider\\..*"
    );
    private static final Pattern PROVIDER_INTERNAL_MODULE_IMPORT = Pattern.compile(
            "^import com\\.meant\\.api\\.module\\..*\\.(repository|entity)\\..*"
    );
    private static final Pattern PROVIDER_EXTENSION_IMPORT = Pattern.compile(
            "^import com\\.meant\\.api\\.plugin\\.catalog\\.extension\\.[^.]+\\..*"
    );

    @Test
    void productionPackagesRespectPluginProviderAndModuleOwnership() throws IOException {
        Path sourceRoot = productionSourceRoot();
        List<String> violations = new ArrayList<>();

        for (Path source : javaSources(sourceRoot)) {
            String relative = sourceRoot.relativize(source).toString().replace('\\', '/');
            List<String> lines = Files.readAllLines(source);
            if (relative.startsWith("com/meant/api/plugin/")) {
                addMatchingImports(violations, relative, lines, PLUGIN_FORBIDDEN_IMPORT,
                        "plugin must not import module/provider");
                inspectPluginOwnership(violations, relative, lines);
            }
            if (relative.startsWith("com/meant/api/module/")) {
                addMatchingImports(violations, relative, lines, MODULE_PROVIDER_IMPORT,
                        "module must not import provider");
            }
            if (relative.startsWith("com/meant/api/provider/")) {
                addMatchingImports(violations, relative, lines, PROVIDER_INTERNAL_MODULE_IMPORT,
                        "provider must not import module repository/entity");
            }
            if (isGenericCatalogCapability(relative)) {
                addMatchingImports(violations, relative, lines, PROVIDER_EXTENSION_IMPORT,
                        "generic catalog capability must use CatalogExtensionRegistry");
            }
        }

        assertThat(violations)
                .as("package ownership violations%n%s", String.join(System.lineSeparator(), violations))
                .isEmpty();
    }

    private void inspectPluginOwnership(List<String> violations, String relative, List<String> lines) {
        boolean persistencePackage = relative.contains("/entity/") || relative.contains("/repository/");
        boolean persistenceType = lines.stream().anyMatch(line -> line.contains("jakarta.persistence.Entity")
                || line.contains("org.springframework.data.repository")
                || line.contains("org.springframework.data.jpa.repository")
                || line.contains("@Entity")
                || line.contains("extends JpaRepository")
                || line.contains("extends CrudRepository"));
        if (persistencePackage || persistenceType) {
            violations.add(relative + ": plugin must not own JPA entities/repositories");
        }

        boolean forbiddenShopifyLocation = relative.startsWith("com/meant/api/plugin/transport/")
                || relative.startsWith("com/meant/api/plugin/catalog/")
                && !relative.startsWith("com/meant/api/plugin/catalog/extension/shopify/");
        if (forbiddenShopifyLocation && sourceTypeName(relative).contains("Shopify")) {
            violations.add(relative + ": Shopify adapter must live under provider.shopify or catalog extension");
        }
    }

    private void addMatchingImports(
            List<String> violations,
            String relative,
            List<String> lines,
            Pattern pattern,
            String message
    ) {
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (pattern.matcher(line).matches()) {
                violations.add(relative + ":" + (index + 1) + ": " + message + ": " + line);
            }
        }
    }

    private boolean isGenericCatalogCapability(String relative) {
        return relative.startsWith("com/meant/api/plugin/catalog/search/")
                || relative.startsWith("com/meant/api/plugin/catalog/lookup/")
                || relative.startsWith("com/meant/api/plugin/catalog/getproduct/");
    }

    private String sourceTypeName(String relative) {
        int slash = relative.lastIndexOf('/');
        return slash < 0 ? relative : relative.substring(slash + 1);
    }

    private List<Path> javaSources(Path sourceRoot) throws IOException {
        try (var sources = Files.walk(sourceRoot)) {
            return sources.filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private Path productionSourceRoot() {
        Path backendRoot = Path.of("src/main/java");
        return Files.isDirectory(backendRoot) ? backendRoot : Path.of("backend/src/main/java");
    }
}

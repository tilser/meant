package com.meant.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

class MeantApiApplicationTest {

    @Test
    void configurationPropertiesScanCoversOnlyTheirOwningPackages() {
        ConfigurationPropertiesScan scan = MeantApiApplication.class.getAnnotation(ConfigurationPropertiesScan.class);
        Set<String> configuredPackages = Arrays.stream(scan.basePackageClasses())
                .map(Class::getPackageName)
                .collect(Collectors.toSet());

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ConfigurationProperties.class));
        Set<String> propertyPackages = scanner.findCandidateComponents("com.meant.api").stream()
                .map(candidate -> candidate.getBeanClassName())
                .map(className -> className.substring(0, className.lastIndexOf('.')))
                .collect(Collectors.toSet());

        assertThat(configuredPackages).containsExactlyInAnyOrderElementsOf(propertyPackages);
    }
}

package com.intact_id;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Module boundaries (Spring Modulith). Phase 1 only reports violations; once packages are
 * grouped into modules (phase 2), replace the report with MODULES.verify().
 */
class ModularityTests {

    static final ApplicationModules MODULES = ApplicationModules.of(IntactIdApplication.class);

    @Test
    void reportsModuleViolations() throws IOException {
        Path report = Path.of("target", "modulith-violations.txt");
        Files.createDirectories(report.getParent());
        Files.write(report, MODULES.detectViolations().getMessages());
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(MODULES).writeDocumentation();
    }
}

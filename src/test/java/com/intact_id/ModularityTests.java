package com.intact_id;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Module boundaries (Spring Modulith): fails on any module reaching into another's internals or
 * any dependency cycle. config, constant, util and dto are open, shared packages.
 */
class ModularityTests {

    static final ApplicationModules MODULES = ApplicationModules.of(IntactIdApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        MODULES.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(MODULES).writeDocumentation();
    }
}

package com.sithum.wallet;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    // Fails the build if a module reaches into another module's internals
    // or if modules form a dependency cycle.
    @Test
    void modulesAreWellFormed() {
        ApplicationModules.of(WalletApplication.class).verify();
    }
}

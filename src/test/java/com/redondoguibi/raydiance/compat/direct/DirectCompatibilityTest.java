package com.redondoguibi.raydiance.compat.direct;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DirectCompatibilityTest {
    @Test void refusesUnsafeOrUnknownShaderApi(){
        assertFalse(DirectCompatibility.safeShaderApi("1.0.0"));
        assertFalse(DirectCompatibility.safeShaderApi("2.0.0"));
        assertFalse(DirectCompatibility.safeShaderApi("unknown"));
        assertTrue(DirectCompatibility.safeShaderApi("1.1.0"));
        assertTrue(DirectCompatibility.safeShaderApi("1.12.3"));
    }
}

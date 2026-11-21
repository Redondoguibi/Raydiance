package com.redondoguibi.raydiance.client.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BufferFlipperTest {
    @Test void readsAndWritesNeverAliasAndInputIsNeverWritten() {
        var flipper = new BufferFlipper(2);
        flipper.startFromInput(0);
        assertEquals(BufferFlipper.INPUT, flipper.read(0));
        assertEquals(0, flipper.write(0), "the first write of colortex0 goes to an owned texture");
        flipper.flip(0);
        assertEquals(0, flipper.read(0));
        assertEquals(1, flipper.write(0));
        flipper.flip(0);
        assertEquals(1, flipper.read(0));
        assertEquals(0, flipper.write(0));
        for (int i = 0; i < 5; i++) { assertNotEquals(flipper.read(1), flipper.write(1)); flipper.flip(1); }
    }

    @Test void unclearedBuffersKeepTheirSideAcrossFrames() {
        var flipper = new BufferFlipper(2);
        flipper.flip(1);
        flipper.startFromInput(0);
        assertEquals(1, flipper.read(1), "history written last frame stays readable");
        flipper.reset(1);
        assertEquals(0, flipper.read(1));
    }
}

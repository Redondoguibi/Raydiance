package com.redondoguibi.raydiance.client.render;

import java.util.Arrays;

/**
 * Ping-pong bookkeeping for format-3 buffers (compare Iris' BufferFlipper), independent of GL.
 *
 * <p>Each buffer has sides 0 and 1. A program reads the current side and writes the other one, then the buffer
 * flips, so a program can sample and write the same buffer without feedback. Buffer 0 can start on
 * {@link #INPUT}: the borrowed frame input, which is never written. Uncleared buffers keep their side across
 * frames, so their history survives (temporal effects).</p>
 */
public final class BufferFlipper {
    public static final int INPUT = -1;
    private final int[] current;
    public BufferFlipper(int buffers) { current = new int[buffers]; }
    public void startFromInput(int buffer) { current[buffer] = INPUT; }
    public void reset(int buffer) { current[buffer] = 0; }
    public void resetAll() { Arrays.fill(current, 0); }
    public int read(int buffer) { return current[buffer]; }
    public int write(int buffer) { return current[buffer] == 0 ? 1 : 0; }
    public void flip(int buffer) { current[buffer] = write(buffer); }
}

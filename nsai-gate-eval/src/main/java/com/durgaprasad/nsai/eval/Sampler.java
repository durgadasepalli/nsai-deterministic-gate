package com.durgaprasad.nsai.eval;

import java.io.IOException;

/** Produces one model answer for a prompt. Replaced by a fake in tests. */
interface Sampler {
    String generate(String model, String prompt, long seed) throws IOException, InterruptedException;
}

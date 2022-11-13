package com.thebombzen.jxlatte.frame.vardct;

import java.io.IOException;

import com.thebombzen.jxlatte.io.Bitreader;

public class Quantizer {
    public final int globalScale;
    public final int quantLF;
    public final double[] scaledDequant = new double[3];
    public Quantizer(Bitreader reader, double[] lfDequant) throws IOException {
        this.globalScale = reader.readU32(1, 11, 2049, 11, 4097, 12, 8193, 16);
        this.quantLF = reader.readU32(16, 0, 1, 5, 1, 8, 1, 16);
        for (int i = 0; i < 3; i++) {
            scaledDequant[i] = lfDequant[i] / (globalScale * quantLF);
        }
    }
}
package com.thebombzen.jxlatte.frame.lfglobal;

import java.io.IOException;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.entropy.EntropyStream;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.MathHelper;
import com.thebombzen.jxlatte.util.Point;

public class Splines {

    public final int numSplines;
    public final int quantAdjust;
    public final Point[] splinePos;
    public final int[] controlCount;
    public final Point[][] controlPoints;
    public final int[][] coeffX;
    public final int[][] coeffY;
    public final int[][] coeffB;
    public final int[][] coeffSigma;

    public Splines(Bitreader reader) throws IOException {
        EntropyStream stream = new EntropyStream(reader, 6);
        numSplines = 1 + stream.readSymbol(reader, 2);
        splinePos = new Point[numSplines];
        for (int i = 0; i < numSplines; i++) {
            int x = stream.readSymbol(reader, 1);
            int y = stream.readSymbol(reader, 1);
            if (i != 0) {
                x = MathHelper.unpackSigned(x) + splinePos[i - 1].x;
                y = MathHelper.unpackSigned(y) + splinePos[i - 1].y;
            }
            splinePos[i] = new Point(x, y);
        }
        quantAdjust = MathHelper.unpackSigned(stream.readSymbol(reader, 0));
        controlCount = new int[numSplines];
        controlPoints = new Point[numSplines][];
        for (int i = 0; i < numSplines; i++) {
            controlCount[i] = 1 + stream.readSymbol(reader, 3);
            controlPoints[i] = new Point[controlCount[i]];
            controlPoints[i][0] = new Point(splinePos[i]);
            int[] deltaX = new int[controlCount[i] - 1];
            int[] deltaY = new int[controlCount[i] - 1];
            for (int j = 0; j < controlCount[i] - 1; j++) {
                deltaX[j] = MathHelper.unpackSigned(stream.readSymbol(reader, 4));
                deltaY[j] = MathHelper.unpackSigned(stream.readSymbol(reader, 4));
            }
            Point current = new Point(controlPoints[i][0]);
            Point delta = new Point(0, 0);
            for (int j = 1; j < controlCount[i]; j++) {
                delta.x += deltaX[j - 1];
                delta.y += deltaY[j - 1];
                current.x += delta.x;
                current.y += delta.y;
                controlPoints[i][j] = new Point(current);
            }
        }
        coeffX = new int[numSplines][32];
        coeffY = new int[numSplines][32];
        coeffB = new int[numSplines][32];
        coeffSigma = new int[numSplines][32];
        for (int i = 0; i < numSplines; i++) {
            for (int j = 0; j < 32; j++)
                coeffX[i][j] = MathHelper.unpackSigned(stream.readSymbol(reader, 5));
            for (int j = 0; j < 32; j++)
                coeffY[i][j] = MathHelper.unpackSigned(stream.readSymbol(reader, 5));
            for (int j = 0; j < 32; j++)
                coeffB[i][j] = MathHelper.unpackSigned(stream.readSymbol(reader, 5));
            for (int j = 0; j < 32; j++)
                coeffSigma[i][j] = MathHelper.unpackSigned(stream.readSymbol(reader, 5));
        }
        if (!stream.validateFinalState())
            throw new InvalidBitstreamException("Illegal final ANS state");
    }
}

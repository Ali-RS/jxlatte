package com.thebombzen.jxlatte.frame.vardct;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.group.LFGroup;
import com.thebombzen.jxlatte.frame.modular.ModularChannelInfo;
import com.thebombzen.jxlatte.frame.modular.ModularStream;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.IntPoint;
import com.thebombzen.jxlatte.util.MathHelper;

public class HFMetadata {
    public final int nbBlocks;
    public final TransformType[][] dctSelect;
    public final IntPoint[] blockList;
    public final Map<IntPoint, IntPoint> blockMap = new HashMap<>();
    public final int[][] hfMultiplier;
    public final int[][][] hfStreamBuffer;

    public HFMetadata(Bitreader reader, LFGroup parent, Frame frame) throws IOException {
        IntPoint size = frame.getLFGroupSize(parent.lfGroupID).shiftRight(3);
        int n = MathHelper.ceilLog2(size.x * size.y);
        nbBlocks = 1 + reader.readBits(n);
        IntPoint aFromYSize = size.ceilDiv(8);
        ModularChannelInfo xFromY = new ModularChannelInfo(aFromYSize.x, aFromYSize.y, 0, 0);
        ModularChannelInfo bFromY = new ModularChannelInfo(aFromYSize.x, aFromYSize.y, 0, 0);
        ModularChannelInfo blockInfo = new ModularChannelInfo(nbBlocks, 2, 0, 0);
        ModularChannelInfo sharpness = new ModularChannelInfo(size.x, size.y, 0, 0);
        ModularStream hfStream = new ModularStream(reader, frame, 1 + 2*frame.getNumLFGroups() + parent.lfGroupID,
            new ModularChannelInfo[]{xFromY, bFromY, blockInfo, sharpness});
        hfStream.decodeChannels(reader);
        hfStreamBuffer = hfStream.getDecodedBuffer();
        hfStream = null;
        dctSelect = new TransformType[size.y][size.x];
        hfMultiplier = new int[size.y][size.x];
        int[][] blockInfoBuffer = hfStreamBuffer[2];
        List<IntPoint> blocks = new ArrayList<>();
        IntPoint lastBlock = new IntPoint();
        for (int i = 0; i < nbBlocks; i++) {
            int type = blockInfoBuffer[0][i];
            if (type > 26 || type < 0)
                throw new InvalidBitstreamException("Invalid Transform Type: " + type);
            blocks.add(placeBlock(lastBlock, TransformType.get(type), 1 + blockInfoBuffer[1][i]));
        }
        blockList = blocks.stream().toArray(IntPoint[]::new);
    }

    public String getBlockMapAsciiArt() {
        String[][] strings = new String[2 * dctSelect.length + 1][2 * dctSelect[0].length + 1];
        int k = 0;
        for (IntPoint block : blockList) {
            int dw = dctSelect[block.y][block.x].dctSelectWidth;
            int dh = dctSelect[block.y][block.x].dctSelectHeight;
            strings[2*block.y + 1][2*block.x + 1] = String.format("%03d", k++ % 1000);
            for (int x = 0; x < dw; x++) {
                strings[2*block.y][2*(block.x + x)] = "+";
                strings[2*block.y][2*(block.x + x) + 1] = "---";
                strings[2*(block.y+dh)][2*(block.x + x)] = "+";
                strings[2*(block.y+dh)][2*(block.x + x)+1] = "---";
            }
            for (int y = 0; y < dh; y++) {
                strings[2*(block.y + y)][2*block.x] = "+";
                strings[2*(block.y + y) + 1][2*block.x] = "|";
                strings[2*(block.y + y)][2*(block.x+dw)] = "+";
                strings[2*(block.y + y) + 1][2*(block.x+dw)] = "|";
            }
            strings[2*(block.y + dh)][2*(block.x + dw)] = "+";
        }
        StringBuilder builder = new StringBuilder();
        for (int y = 0; y < strings.length; y++) {
            for (int x = 0; x < strings[y].length; x++) {
                String s = strings[y][x];
                if (s == null) {
                    if (x % 2 == 0)
                        s = " ";
                    else
                        s = "   ";
                }
                builder.append(s);
            }
            builder.append(String.format("%n"));
        }
        return builder.toString();
    }

    private IntPoint placeBlock(IntPoint lastBlock, TransformType block, int mul) throws InvalidBitstreamException {
        for (int y = lastBlock.y; y < dctSelect.length; y++) {
            outer:
            for (int x = lastBlock.x; x < dctSelect[y].length; x++) {
                // block too big, horizontally, to put here
                if (block.dctSelectWidth + x > dctSelect[y].length)
                    continue;
                // block too big, vertically, to put here
                if (block.dctSelectHeight + y > dctSelect.length)
                    continue;
                // space occupied
                for (int iy = 0; iy < block.dctSelectHeight; iy++) {
                    for (int ix = 0; ix < block.dctSelectWidth; ix++) {
                        if (dctSelect[y + iy][x + ix] != null)
                            continue outer;
                    }
                }
                IntPoint pos = new IntPoint(x, y);
                for (int iy = 0; iy < block.dctSelectHeight; iy++) {
                    Arrays.fill(dctSelect[y + iy], x, x + block.dctSelectWidth, block);
                    Arrays.fill(hfMultiplier[y + iy], x, x + block.dctSelectWidth, mul);
                    for (int ix = 0; ix < block.dctSelectWidth; ix++) {
                        blockMap.put(pos.plus(new IntPoint(ix, iy)), pos);
                    }
                }
                lastBlock = pos;
                return pos;
            }
        }
        throw new InvalidBitstreamException("Could not find place for block: " + block.type);
    }
}

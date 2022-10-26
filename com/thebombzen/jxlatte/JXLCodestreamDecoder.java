package com.thebombzen.jxlatte;

import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;

import com.thebombzen.jxlatte.bundle.ImageHeader;
import com.thebombzen.jxlatte.entropy.EntropyDecoder;
import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.image.ChannelType;
import com.thebombzen.jxlatte.image.JxlImage;
import com.thebombzen.jxlatte.image.JxlImageFormat;
import com.thebombzen.jxlatte.io.Bitreader;

public class JXLCodestreamDecoder {
    private Bitreader bitreader;
    private ImageHeader imageHeader;

    public JXLCodestreamDecoder(Bitreader in) {
        this.bitreader = in;
    }

    private static int getICCContext(byte[] buffer, int index) {
        if (index <= 128)
            return 0;
        int b1 = (int)buffer[index - 1] & 0xFF;
        int b2 = (int)buffer[index - 2] & 0xFF;
        int p1, p2;
        if (b1 >= 'a' && b1 <= 'z' || b1 >= 'A' && b1 <= 'Z')
            p1 = 0;
        else if (b1 >= '0' && b1 <= '9' || b1 == '.' || b1 == ',')
            p1 = 1;
        else if (b1 <= 1)
            p1 = 2 + b1;
        else if (b1 > 1 && b1 < 16)
            p1 = 4;
        else if (b1 > 240 && b1 < 255)
            p1 = 5;
        else if (b1 == 255)
            p1 = 6;
        else
            p1 = 7;
        
        if (b2 >= 'a' && b2 <= 'z' || b2 >= 'A' && b2 <= 'Z')
            p2 = 0;
        else if (b2 >= '0' && b2 <= '9' || b2 == '.' || b2 == ',')
            p2 = 1;
        else if (b2 < 16)
            p2 = 2;
        else if (b2 > 240)
            p2 = 3;
        else
            p2 = 4;

        return 1 + p1 + 8 * p2;
    }

    public JxlImage decode(int level) throws IOException {
        this.imageHeader = ImageHeader.parse(bitreader, level);
        if (imageHeader.getColorEncoding().useIccProfile) {
            int encodedSize = Math.toIntExact(bitreader.readU64());
            byte[] encodedIcc = new byte[encodedSize];
            EntropyDecoder iccDistribution = new EntropyDecoder(bitreader, 41);
            for (int i = 0; i < encodedSize; i++)
                encodedIcc[i] = (byte)iccDistribution.readSymbol(bitreader, getICCContext(encodedIcc, i));
        }
        bitreader.zeroPadToByte();
        Frame frame;
        do {
            frame = new Frame(bitreader, imageHeader);
            frame.readHeader();
            int[][][] buffer = frame.decodeFrame();
            bitreader.zeroPadToByte();
            BufferedImage image = new BufferedImage(frame.getFrameHeader().width,
                frame.getFrameHeader().height, BufferedImage.TYPE_3BYTE_BGR);
            int bitDepth = imageHeader.getBitDepthHeader().bitsPerSample;
            int max = (1 << bitDepth) - 1;
            for (int x = 0; x < image.getWidth(); x++) {
                for (int y = 0; y < image.getHeight(); y++) {
                    int r = buffer[0][x][y] * 255 / max;
                    int g = buffer[0][x][y] * 255 / max;
                    int b = buffer[0][x][y] * 255 / max;
                    int rgb = (r << 16) | (g << 8) | b;
                    image.setRGB(x, y, rgb);
                }
            }
            ImageIO.write(image, "png", new File("output.png"));
        } while (!frame.getFrameHeader().isLast);

        int width = imageHeader.getSize().width;
        int height = imageHeader.getSize().height;
        JxlImage image =  new JxlImage(new JxlImageFormat(8, 0, ChannelType.PACKED_RGB), width, height);
        return image;
    }
}

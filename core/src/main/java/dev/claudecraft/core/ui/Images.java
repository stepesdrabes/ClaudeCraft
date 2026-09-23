package dev.claudecraft.core.ui;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Images {
    static final ExecutorService DECODER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "claudecraft-images");
        thread.setDaemon(true);
        return thread;
    });

    private Images() {
    }

    public static void async(Runnable work) {
        DECODER.execute(work);
    }

    public static Image decode(byte[] data, int maxSize) throws IOException {
        return fit(read(new ByteArrayInputStream(data)), maxSize);
    }

    public static Image read(InputStream in) throws IOException {
        BufferedImage source = ImageIO.read(in);
        if (source == null) throw new IOException("Unsupported image format");
        int width = source.getWidth();
        int height = source.getHeight();
        return new Image(width, height, source.getRGB(0, 0, width, height, null, 0, width));
    }

    public static Image fit(Image image, int maxSize) {
        int longest = Math.max(image.width(), image.height());
        if (longest <= maxSize) return image;
        int width = Math.max(1, image.width() * maxSize / longest);
        int height = Math.max(1, image.height() * maxSize / longest);
        int[] source = image.argb();
        int[] target = new int[width * height];
        for (int y = 0; y < height; y++) {
            int y0 = y * image.height() / height, y1 = Math.max(y0 + 1, (y + 1) * image.height() / height);
            for (int x = 0; x < width; x++) {
                int x0 = x * image.width() / width, x1 = Math.max(x0 + 1, (x + 1) * image.width() / width);
                long a = 0, r = 0, g = 0, b = 0;
                for (int sy = y0; sy < y1; sy++) {
                    for (int sx = x0; sx < x1; sx++) {
                        int pixel = source[sy * image.width() + sx];
                        int alpha = pixel >>> 24;
                        a += alpha;
                        r += ((pixel >> 16) & 0xFF) * alpha;
                        g += ((pixel >> 8) & 0xFF) * alpha;
                        b += (pixel & 0xFF) * alpha;
                    }
                }
                int count = (y1 - y0) * (x1 - x0);
                target[y * width + x] = a == 0 ? 0 : (int) (a / count) << 24 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
            }
        }
        return new Image(width, height, target);
    }

    public static byte[] png(Image image) throws IOException {
        BufferedImage buffered = new BufferedImage(image.width(), image.height(), BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, image.width(), image.height(), image.argb(), 0, image.width());
        return write(buffered, "png");
    }

    public static byte[] jpeg(Image image) throws IOException {
        BufferedImage buffered = new BufferedImage(image.width(), image.height(), BufferedImage.TYPE_INT_RGB);
        buffered.setRGB(0, 0, image.width(), image.height(), image.argb(), 0, image.width());
        return write(buffered, "jpg");
    }

    private static byte[] write(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, out)) throw new IOException("No " + format + " encoder");
        return out.toByteArray();
    }
}

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** One-off generator for Android launcher PNGs from the supplied logo exports. */
public final class IconGenerator {
    private static BufferedImage readLogo(String path) throws Exception {
        BufferedImage source = ImageIO.read(new File(path));
        int minX = source.getWidth(), minY = source.getHeight(), maxX = 0, maxY = 0;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int brightness = source.getRGB(x, y) & 0xff;
                if (brightness > 128) {
                    minX = Math.min(minX, x); minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                }
            }
        }
        return source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static void writeIcon(BufferedImage logo, int size, boolean foreground, File out) throws Exception {
        BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        if (!foreground) {
            graphics.setColor(new Color(7, 9, 11));
            graphics.fillRect(0, 0, size, size);
        }
        double maxWidth = size * (foreground ? 0.64 : 0.84);
        double maxHeight = size * (foreground ? 0.40 : 0.58);
        double scale = Math.min(maxWidth / logo.getWidth(), maxHeight / logo.getHeight());
        int width = (int) Math.round(logo.getWidth() * scale);
        int height = (int) Math.round(logo.getHeight() * scale);
        BufferedImage alphaLogo = new BufferedImage(logo.getWidth(), logo.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < logo.getHeight(); y++) {
            for (int x = 0; x < logo.getWidth(); x++) {
                int brightness = logo.getRGB(x, y) & 0xff;
                int alpha = brightness < 8 ? 0 : brightness;
                alphaLogo.setRGB(x, y, (alpha << 24) | 0x00ffffff);
            }
        }
        graphics.drawImage(alphaLogo, (size - width) / 2, (size - height) / 2, width, height, null);
        graphics.dispose();
        out.getParentFile().mkdirs();
        ImageIO.write(result, "png", out);
    }

    public static void main(String[] args) throws Exception {
        BufferedImage logo = readLogo(args[0]);
        File res = new File(args[1]);
        String qualifier = args.length > 3 ? args[3] : "";
        String[] densities = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        int[] sizes = {48, 72, 96, 144, 192};
        for (int i = 0; i < densities.length; i++) {
            String directory = qualifier.isEmpty()
                    ? "mipmap-" + densities[i]
                    : "mipmap" + qualifier + "-" + densities[i];
            writeIcon(logo, sizes[i], false, new File(res, directory + "/ic_launcher.png"));
        }
        writeIcon(logo, 432, true, new File(res, "drawable" + qualifier + "-nodpi/lotot_logo_foreground.png"));
        writeIcon(logo, 512, false, new File(args[2]));
    }
}

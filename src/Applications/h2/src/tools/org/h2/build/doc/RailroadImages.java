/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.build.doc;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Create the images used in the railroad diagrams.
 */
public class RailroadImages {

    private static final int SIZE = 64;
    private static final int LINE_REPEAT = 32;
    private static final int DIV = 4;
    private static final int STROKE = 4;

    private String outDir;

    /**
     * This method is called when executing this application from the command
     * line.
     *
     * @param args the command line parameters
     */
    public static void main(String... args) {
        new RailroadImages().run("docs/html/images");
    }

    /**
     * Create the images.
     *
     * @param out the target directory
     */
    void run(String out) {
        this.outDir = out;
        new File(out).mkdirs();
        BufferedImage img;
        Graphics2D g;
        img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(STROKE));
        g.drawLine(0, SIZE / 2, SIZE, SIZE / 2);
        g.dispose();
        savePng(img, "div-d.png");
        img = new BufferedImage(SIZE, SIZE * LINE_REPEAT, BufferedImage.TYPE_INT_ARGB);
        g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(STROKE));
        g.drawLine(0, SIZE / 2, SIZE, SIZE / 2);
        g.drawLine(SIZE / 2, SIZE, SIZE / 2, SIZE * LINE_REPEAT);

        // g.drawLine(0, SIZE / 2, SIZE / 2, SIZE);
        g.drawArc(-SIZE / 2, SIZE / 2, SIZE, SIZE, 0, 90);

        g.dispose();
        savePng(img, "div-ts.png");
        savePng(flipHorizontal(img), "div-te.png");
        img = new BufferedImage(SIZE, SIZE * LINE_REPEAT, BufferedImage.TYPE_INT_ARGB);
        g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(STROKE));

        g.drawArc(SIZE / 2, -SIZE / 2, SIZE, SIZE, 180, 270);
        // g.drawLine(SIZE / 2, 0, SIZE, SIZE / 2);

        savePng(img, "div-ls.png");
        savePng(flipHorizontal(img), "div-le.png");
        g.drawLine(SIZE / 2, 0, SIZE / 2, SIZE * LINE_REPEAT);
        g.dispose();
        savePng(img, "div-ks.png");
        savePng(flipHorizontal(img), "div-ke.png");
    }

    private void savePng(BufferedImage img, String fileName) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage smaller = new BufferedImage(w / DIV, h / DIV, img.getType());
        Graphics2D g = smaller.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, w / DIV, h / DIV, 0, 0, w, h, null);
        g.dispose();
        try {
            ImageIO.write(smaller, "png", new File(outDir + "/" + fileName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private BufferedImage flipHorizontal(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage copy = new BufferedImage(w, h, img.getType());
        Graphics2D g = copy.createGraphics();
        g.drawImage(img, 0, 0, w, h, w, 0, 0, h, null);
        g.dispose();
        return copy;
    }

}

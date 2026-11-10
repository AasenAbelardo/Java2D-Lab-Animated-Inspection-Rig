//Abelardo, Aasen Sofia P. 
package abelardo_aasen_inspectionrig;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.*;


public class Abelardo_Aasen_InspectionRig {
    static final int VIEW_W = 720;
    static final int VIEW_H = 440;
    static final double PERIOD = 8.0;

    static final int RAIL_LEFT = 160;
    static final int RAIL_RIGHT = 560;
    static final int RAIL_Y = 220;
    static final int BEACON_X = 360;
    static final int BEACON_Y = 70;

    static final double HUB_LOCAL_X = 0;
    static final double HUB_LOCAL_Y = -38;
    static final double TIP_LOCAL_X = 40;
    static final double TIP_LOCAL_Y = 0;

    static final Color BG = new Color(231, 238, 242);
    static final Color RAIL = new Color(46, 54, 62);
    static final Color RAIL_TIE = new Color(196, 204, 210);
    static final Color RAIL_STOP = new Color(30, 36, 42);
    static final Color BEACON = new Color(176, 48, 42);
    static final Color BEACON_CORE = new Color(255, 209, 92);
    static final Color BODY = new Color(47, 93, 134);
    static final Color BODY_EDGE = new Color(24, 52, 78);
    static final Color WINDOW = new Color(22, 52, 79);
    static final Color WHEEL = new Color(28, 33, 38);
    static final Color WHEEL_RIM = new Color(176, 186, 194);
    static final Color WHEEL_HUB = new Color(214, 220, 224);
    static final Color ROTOR = new Color(224, 122, 47);
    static final Color ROTOR_EDGE = new Color(110, 52, 18);
    static final Color TIP_CAP = new Color(255, 236, 214);
    static final Color HUB = new Color(255, 214, 74);
    static final Color HUB_EDGE = new Color(48, 36, 12);
    static final Color TETHER = new Color(210, 0, 48);
    static final Color TIP_RING = new Color(255, 255, 255);
    static final Color TIP_RING_DARK = new Color(20, 24, 28);
    static final Color INK = new Color(28, 34, 40);
    static final Color MUTED = new Color(70, 78, 86);
    static final Color PAUSED = new Color(160, 48, 32);
    static final Color HUD_BG = new Color(255, 255, 255, 228);
    static final Color HUD_EDGE = new Color(180, 188, 196);

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && "--check".equals(args[0])) {
            int failures = Verification.run();
            System.exit(failures == 0 ? 0 : 1);
        }
        if (args.length > 0 && "--export".equals(args[0])) {
            File dir = new File(args.length > 1 ? args[1] : ".");
            Verification.export(dir);
            return;
        }
        SwingUtilities.invokeLater(Abelardo_Aasen_InspectionRig::show);
    }

    static void show() {
        RigPanel panel = new RigPanel();
        JFrame frame = new JFrame("Inspection Rig");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setContentPane(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent event) {
                panel.requestFocusInWindow();
            }
        });
        frame.setVisible(true);
        SwingUtilities.invokeLater(panel::requestFocusInWindow);
    }

    /** Wrap elapsed seconds onto the animation interval [0, 8). */
    static double wrap(double seconds) {
        double t = seconds % PERIOD;
        if (t < 0) {
            t += PERIOD;
        }
        return t;
    }

    /**
     * Carriage travel parameter. Moves from 0 to 1 over the first half of the
     * cycle, then back from 1 to 0. At t = 4 the value is 1.
     */
    static double travel(double t) {
        return t < 4.0 ? t / 4.0 : (PERIOD - t) / 4.0;
    }

    /** Pose of the rig and the rotor child transform at wrapped time t. */
    static final class Pose {
        final double t;
        final double p;
        final double anchorX;
        final double anchorY;
        final double scale;
        final int tipX;
        final int tipY;
        final AffineTransform rig;
        final AffineTransform rotor;

        Pose(double t, double p, double anchorX, double anchorY, double scale,
                int tipX, int tipY, AffineTransform rig, AffineTransform rotor) {
            this.t = t;
            this.p = p;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.scale = scale;
            this.tipX = tipX;
            this.tipY = tipY;
            this.rig = rig;
            this.rotor = rotor;
        }

        static Pose at(double seconds) {
            double t = wrap(seconds);
            double p = travel(t);
            double anchorX = RAIL_LEFT + (RAIL_RIGHT - RAIL_LEFT) * p;
            double anchorY = RAIL_Y;
            double scale = 1.0 + 0.5 * p;

            // Rig motion is a translation, so the housing and wheels stay upright.
            AffineTransform rig = new AffineTransform();
            rig.translate(anchorX, anchorY);

            // Child of the rig transform. Scale and rotation are concatenated
            // after the hub translation, so both run about the rotor origin
            // (the hub) before that origin is moved to rig-local (0, -38).
            AffineTransform rotor = new AffineTransform(rig);
            rotor.translate(HUB_LOCAL_X, HUB_LOCAL_Y);
            rotor.rotate(Math.toRadians(90.0 * t));
            rotor.scale(scale, scale);

            Point2D tip = rotor.transform(new Point2D.Double(TIP_LOCAL_X, TIP_LOCAL_Y), null);
            int tipX = (int) Math.round(tip.getX());
            int tipY = (int) Math.round(tip.getY());
            return new Pose(t, p, anchorX, anchorY, scale, tipX, tipY, rig, rotor);
        }
    }

    static void drawScene(Graphics2D g2, int width, int height, double seconds, boolean paused) {
        AffineTransform base = g2.getTransform();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Pose pose = Pose.at(seconds);

        g2.setColor(BG);
        g2.fillRect(0, 0, width, height);
        paintTrack(g2);
        paintBeacon(g2);

        g2.setTransform(base);
        g2.transform(pose.rig);
        paintCarriage(g2);

        g2.setTransform(base);
        g2.transform(pose.rotor);
        paintRotor(g2);

        g2.setTransform(base);
        g2.transform(pose.rig);
        paintHub(g2);

        g2.setTransform(base);
        paintTipRing(g2, pose);

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setColor(TETHER);
        Bresenham.draw(g2, BEACON_X, BEACON_Y, pose.tipX, pose.tipY);

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        paintHud(g2, width, height, pose, paused);
        g2.setTransform(base);
    }

    /** Rail and its ties, in screen coordinates. */
    static void paintTrack(Graphics2D g2) {
        g2.setColor(RAIL_TIE);
        for (int x = 176; x <= 544; x += 24) {
            g2.fillRect(x, 212, 10, 16);
        }
        g2.setColor(RAIL_STOP);
        g2.fillRect(152, 206, 8, 28);
        g2.fillRect(560, 206, 8, 28);
        g2.setColor(RAIL);
        g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g2.draw(new Line2D.Double(RAIL_LEFT, RAIL_Y, RAIL_RIGHT, RAIL_Y));
    }

    /** Fixed beacon, in screen coordinates. */
    static void paintBeacon(Graphics2D g2) {
        g2.setColor(RAIL_STOP);
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Line2D.Double(BEACON_X, BEACON_Y + 8, BEACON_X, BEACON_Y + 34));
        g2.setColor(BEACON);
        g2.fill(new Ellipse2D.Double(BEACON_X - 9, BEACON_Y - 9, 18, 18));
        g2.setColor(BEACON_CORE);
        g2.fill(new Ellipse2D.Double(BEACON_X - 3.5, BEACON_Y - 3.5, 7, 7));
    }

    /** Rectangular housing and two wheels, in rig-local coordinates. */
    static void paintCarriage(Graphics2D g2) {
        g2.setColor(BODY);
        g2.fill(new Rectangle2D.Double(-40, -86, 80, 76));
        g2.setColor(BODY_EDGE);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(new Rectangle2D.Double(-40, -86, 80, 76));

        g2.setColor(WINDOW);
        g2.fill(new Rectangle2D.Double(-24, -78, 48, 16));

        g2.setColor(WHEEL);
        g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Line2D.Double(-28, -15, 28, -15));
        paintWheel(g2, -28, -15, 15);
        paintWheel(g2, 28, -15, 15);
    }

    static void paintWheel(Graphics2D g2, double cx, double cy, double r) {
        g2.setColor(WHEEL);
        g2.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g2.setColor(WHEEL_HUB);
        g2.fill(new Ellipse2D.Double(cx - 5, cy - 5, 10, 10));
        g2.setColor(WHEEL_RIM);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
    }

    /**
     * Asymmetric rotor in rotor-local coordinates. The hub is the origin and
     * the tip is (40, 0). One lobe is much larger than the other.
     */
    static Path2D.Double rotorGeometry() {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(TIP_LOCAL_X, TIP_LOCAL_Y);
        path.lineTo(24, 8);
        path.lineTo(8, 3);
        path.lineTo(-4, 12);
        path.lineTo(-18, 7);
        path.lineTo(-8, 1);
        path.lineTo(0, 0);
        path.closePath();

        path.moveTo(0, 0);
        path.lineTo(12, -3);
        path.lineTo(6, -11);
        path.lineTo(-14, -5);
        path.closePath();
        return path;
    }

    static void paintRotor(Graphics2D g2) {
        Path2D.Double blade = rotorGeometry();
        g2.setColor(ROTOR);
        g2.fill(blade);
        g2.setColor(ROTOR_EDGE);
        g2.setStroke(new BasicStroke(1.4f));
        g2.draw(blade);
        g2.setColor(TIP_CAP);
        g2.fill(new Ellipse2D.Double(TIP_LOCAL_X - 2.4, TIP_LOCAL_Y - 2.4, 4.8, 4.8));
    }

    /** Constant-size hub cap in rig-local coordinates, drawn over the rotor. */
    static void paintHub(Graphics2D g2) {
        double y = HUB_LOCAL_Y;
        g2.setColor(HUB_EDGE);
        g2.fill(new Ellipse2D.Double(-7, y - 7, 14, 14));
        g2.setColor(HUB);
        g2.fill(new Ellipse2D.Double(-4.2, y - 4.2, 8.4, 8.4));
        g2.setColor(HUB_EDGE);
        g2.fill(new Ellipse2D.Double(-1.5, y - 1.5, 3, 3));
    }

    static void paintTipRing(Graphics2D g2, Pose pose) {
        g2.setColor(TIP_RING);
        g2.setStroke(new BasicStroke(2.2f));
        g2.draw(new Ellipse2D.Double(pose.tipX - 6.5, pose.tipY - 6.5, 13, 13));
        g2.setColor(TIP_RING_DARK);
        g2.setStroke(new BasicStroke(1.15f));
        g2.draw(new Ellipse2D.Double(pose.tipX - 4.2, pose.tipY - 4.2, 8.4, 8.4));
    }

    static void paintHud(Graphics2D g2, int width, int height, Pose pose, boolean paused) {
        g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        FontMetrics fm = g2.getFontMetrics();
        String timeLine = String.format(Locale.US, "t = %.3f s", pose.t);
        String tipLine = String.format(Locale.US, "tip = (%d, %d)", pose.tipX, pose.tipY);
        String hint = "Space pause/resume    R reset    1-4 checkpoints";
        String pausedLabel = "paused";

        int pad = 10;
        int gap = 16;
        int textW = fm.stringWidth(timeLine);
        if (paused) {
            textW += gap + fm.stringWidth(pausedLabel);
        }
        textW = Math.max(textW, Math.max(fm.stringWidth(tipLine), fm.stringWidth(hint)));
        int lineH = fm.getHeight();
        int boxW = textW + pad * 2;
        int boxH = lineH * 3 + pad * 2;
        int boxX = 14;
        int boxY = height - boxH - 14;
        if (boxX + boxW > width) {
            boxW = width - boxX - 8;
        }

        g2.setColor(HUD_BG);
        g2.fillRoundRect(boxX, boxY, boxW, boxH, 10, 10);
        g2.setColor(HUD_EDGE);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(boxX, boxY, boxW, boxH, 10, 10);

        int textX = boxX + pad;
        int baseline = boxY + pad + fm.getAscent();
        g2.setColor(INK);
        g2.drawString(timeLine, textX, baseline);
        if (paused) {
            g2.setColor(PAUSED);
            g2.drawString(pausedLabel, textX + fm.stringWidth(timeLine) + gap, baseline);
        }
        g2.setColor(INK);
        g2.drawString(tipLine, textX, baseline + lineH);
        g2.setColor(MUTED);
        g2.drawString(hint, textX, baseline + 2 * lineH);
    }

    static BufferedImage render(double seconds, boolean paused) {
        BufferedImage image = new BufferedImage(VIEW_W, VIEW_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        try {
            drawScene(g2, VIEW_W, VIEW_H, seconds, paused);
        } finally {
            g2.dispose();
        }
        return image;
    }

    @SuppressWarnings("serial")
    static final class RigPanel extends JPanel {
        private final Clock clock = new Clock();
        private final Timer timer;
        private boolean spaceDown;

        RigPanel() {
            Dimension size = new Dimension(VIEW_W, VIEW_H);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
            setBackground(BG);
            setOpaque(true);
            setFocusable(true);
            setFocusTraversalKeysEnabled(false);
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent event) {
                    onKeyPressed(event);
                }

                @Override
                public void keyReleased(KeyEvent event) {
                    if (event.getKeyCode() == KeyEvent.VK_SPACE) {
                        spaceDown = false;
                    }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    requestFocusInWindow();
                }
            });
            timer = new Timer(16, event -> repaint());
            timer.start();
        }

        private void onKeyPressed(KeyEvent event) {
            switch (event.getKeyCode()) {
                case KeyEvent.VK_SPACE -> {
                    if (!spaceDown) {
                        spaceDown = true;
                        clock.togglePause();
                    }
                }
                case KeyEvent.VK_R -> clock.resetAndResume();
                case KeyEvent.VK_1, KeyEvent.VK_NUMPAD1 -> clock.pauseAt(0);
                case KeyEvent.VK_2, KeyEvent.VK_NUMPAD2 -> clock.pauseAt(1);
                case KeyEvent.VK_3, KeyEvent.VK_NUMPAD3 -> clock.pauseAt(4);
                case KeyEvent.VK_4, KeyEvent.VK_NUMPAD4 -> clock.pauseAt(6);
                default -> {
                    return;
                }
            }
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(VIEW_W, VIEW_H);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                drawScene(g2, getWidth(), getHeight(), clock.animationTime(), clock.paused());
            } finally {
                g2.dispose();
            }
        }
    }

    /** Elapsed-time clock. Pausing freezes t; reset starts again at zero. */
    static final class Clock {
        private long markNanos = System.nanoTime();
        private double markSeconds;
        private boolean paused;

        double seconds() {
            if (paused) {
                return markSeconds;
            }
            return markSeconds + (System.nanoTime() - markNanos) * 1e-9;
        }

        double animationTime() {
            return wrap(seconds());
        }

        boolean paused() {
            return paused;
        }

        void togglePause() {
            if (paused) {
                resume();
            } else {
                pause();
            }
        }

        void pause() {
            if (!paused) {
                markSeconds = seconds();
                paused = true;
            }
        }

        void resume() {
            if (paused) {
                markNanos = System.nanoTime();
                paused = false;
            }
        }

        void resetAndResume() {
            markSeconds = 0;
            markNanos = System.nanoTime();
            paused = false;
        }

        void pauseAt(double t) {
            markSeconds = t;
            paused = true;
        }
    }

    /**
     * Integer Bresenham line. Both endpoints are included.
     * Steep and shallow slopes, either sign, reversed endpoints, and the
     * single-pixel case are handled by one error term.
     */
    static final class Bresenham {
        private Bresenham() {
        }

        static List<Point> line(int x0, int y0, int x1, int y1) {
            List<Point> pixels = new ArrayList<>();
            int dx = Math.abs(x1 - x0);
            int dy = Math.abs(y1 - y0);
            int sx = x0 < x1 ? 1 : -1;
            int sy = y0 < y1 ? 1 : -1;
            int err = dx - dy;

            while (true) {
                pixels.add(new Point(x0, y0));
                if (x0 == x1 && y0 == y1) {
                    break;
                }
                int e2 = 2 * err;
                if (e2 > -dy) {
                    err -= dy;
                    x0 += sx;
                }
                if (e2 < dx) {
                    err += dx;
                    y0 += sy;
                }
            }
            return pixels;
        }

        static void draw(Graphics2D g, int x0, int y0, int x1, int y1) {
            for (Point pixel : line(x0, y0, x1, y1)) {
                g.fillRect(pixel.x, pixel.y, 1, 1);
            }
        }
    }

    /** Checkpoint, Bresenham, and raster checks used to build the README evidence. */
    static final class Verification {
        private static int failures;

        static int run() throws IOException {
            failures = 0;
            checkClock();
            checkPoses();
            checkHubStaysFixed();
            checkBresenham();
            checkRenderedTether();
            System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECKS FAILED");
            return failures;
        }

        static void export(File directory) throws IOException {
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Cannot create " + directory);
            }
            ImageIO.write(render(1, true), "png", new File(directory, "screenshot_1s.png"));
            ImageIO.write(render(6, true), "png", new File(directory, "screenshot_6s.png"));
            System.out.println("Wrote screenshot_1s.png and screenshot_6s.png");
        }

        private static void checkClock() throws IOException {
            Clock clock = new Clock();
            clock.pauseAt(0);
            expect(clock.paused() && clock.animationTime() == 0, "key 1 pauses at t = 0");
            clock.pauseAt(1);
            expect(clock.paused() && clock.animationTime() == 1, "key 2 pauses at t = 1");
            clock.pauseAt(4);
            expect(clock.paused() && clock.animationTime() == 4, "key 3 pauses at t = 4");
            clock.pauseAt(6);
            expect(clock.paused() && clock.animationTime() == 6, "key 4 pauses at t = 6");

            clock.togglePause();
            sleep(60);
            double advanced = clock.animationTime();
            expect(!clock.paused() && advanced > 6 && advanced < 6.5,
                    "space resumes from t = 6, observed t = " + advanced);

            clock.resetAndResume();
            expect(!clock.paused() && clock.animationTime() < 0.05, "R resets to 0 and resumes");
            clock.pause();
            double frozen = clock.animationTime();
            sleep(50);
            expect(clock.paused() && clock.animationTime() == frozen, "space pause holds t");
            clock.resume();
            sleep(50);
            expect(!clock.paused() && clock.animationTime() > frozen, "second space resumes");

            expect(wrap(0) == 0, "wrap 0");
            expect(wrap(8) == 0, "wrap 8");
            expect(Math.abs(wrap(9.5) - 1.5) < 1e-12, "wrap 9.5");
            expect(Math.abs(travel(0)) < 1e-12, "p(0) = 0");
            expect(Math.abs(travel(4) - 1) < 1e-12, "p(4) = 1");
            expect(Math.abs(travel(6) - 0.5) < 1e-12, "p(6) = 0.5");
        }

        private static void checkPoses() {
            expectPose(0, 160, 220, 200, 182);
            expectPose(1, 260, 220, 260, 227);
            expectPose(4, 560, 220, 620, 182);
            expectPose(6, 360, 220, 310, 182);
        }

        private static void expectPose(double t, int anchorX, int anchorY, int tipX, int tipY) {
            Pose pose = Pose.at(t);
            Point2D raw = pose.rotor.transform(new Point2D.Double(TIP_LOCAL_X, TIP_LOCAL_Y), null);
            boolean anchorOk = Math.abs(pose.anchorX - anchorX) < 1e-9
                    && Math.abs(pose.anchorY - anchorY) < 1e-9;
            boolean tipOk = pose.tipX == tipX && pose.tipY == tipY;
            expect(anchorOk && tipOk, String.format(Locale.US,
                    "t=%g anchor=(%.4f, %.4f) tip=(%d, %d) raw=(%.6f, %.6f) expected anchor=(%d, %d) tip=(%d, %d)",
                    t, pose.anchorX, pose.anchorY, pose.tipX, pose.tipY,
                    raw.getX(), raw.getY(), anchorX, anchorY, tipX, tipY));
        }

        private static void checkHubStaysFixed() {
            double[] samples = {0, 0.5, 1, 2.5, 4, 5.25, 6, 7.2};
            for (double t : samples) {
                Pose pose = Pose.at(t);
                Point2D hub = pose.rotor.transform(new Point2D.Double(0, 0), null);
                Point2D carriageOrigin = pose.rig.transform(new Point2D.Double(0, 0), null);
                Point2D carriageRight = pose.rig.transform(new Point2D.Double(10, 0), null);
                Point2D rawTip = pose.rotor.transform(new Point2D.Double(TIP_LOCAL_X, TIP_LOCAL_Y), null);
                double hubError = Math.hypot(hub.getX() - pose.anchorX, hub.getY() - (pose.anchorY + HUB_LOCAL_Y));
                double tipRadius = Math.hypot(rawTip.getX() - hub.getX(), rawTip.getY() - hub.getY());
                boolean upright = Math.abs(carriageOrigin.getX() - pose.anchorX) < 1e-9
                        && Math.abs(carriageOrigin.getY() - pose.anchorY) < 1e-9
                        && Math.abs(carriageRight.getX() - (pose.anchorX + 10)) < 1e-9
                        && Math.abs(carriageRight.getY() - pose.anchorY) < 1e-9;
                expect(hubError < 1e-6 && Math.abs(tipRadius - 40 * pose.scale) < 1e-6 && upright,
                        String.format(Locale.US, "hub fixed at t=%.2f error=%.3g radius=%.5f", t, hubError, tipRadius));
            }
        }

        private static void checkBresenham() {
            checkSegment(20, 20, 27, 23, 8);
            checkSegment(27, 23, 20, 20, 8);
            checkSegment(30, 15, 26, 28, 14);
            checkSegment(26, 28, 30, 15, 14);
            checkSegment(5, 5, 5, 5, 1);

            List<Point> shallow = Bresenham.line(20, 20, 27, 23);
            int[][] shallowExpected = {
                    {20, 20}, {21, 20}, {22, 21}, {23, 21},
                    {24, 22}, {25, 22}, {26, 23}, {27, 23}
            };
            expect(matches(shallow, shallowExpected), "shallow pixels " + format(shallow));

            List<Point> steep = Bresenham.line(30, 15, 26, 28);
            boolean steepY = steep.size() == 14;
            for (int i = 0; steepY && i < steep.size(); i++) {
                steepY = steep.get(i).y == 15 + i;
            }
            expect(steepY && steep.get(0).x == 30 && steep.get(13).x == 26,
                    "steep pixels " + format(steep));

            List<Point> steepBack = Bresenham.line(26, 28, 30, 15);
            boolean steepBackY = steepBack.size() == 14;
            for (int i = 0; steepBackY && i < steepBack.size(); i++) {
                steepBackY = steepBack.get(i).y == 28 - i;
            }
            expect(steepBackY && steepBack.get(0).equals(new Point(26, 28))
                    && steepBack.get(13).equals(new Point(30, 15)),
                    "reversed steep pixels " + format(steepBack));

            expect(samePoints(Bresenham.line(20, 20, 27, 23), Bresenham.line(27, 23, 20, 20)),
                    "shallow reverse covers the same pixels");
            expect(samePoints(steep, steepBack), "steep reverse covers the same pixels");

            expect(countDrawn(20, 20, 27, 23) == 8, "raster count (20,20)-(27,23)");
            expect(countDrawn(27, 23, 20, 20) == 8, "raster count reverse shallow");
            expect(countDrawn(30, 15, 26, 28) == 14, "raster count (30,15)-(26,28)");
            expect(countDrawn(26, 28, 30, 15) == 14, "raster count reverse steep");
            expect(countDrawn(5, 5, 5, 5) == 1, "raster count single pixel");

            System.out.println("reverse shallow " + format(Bresenham.line(27, 23, 20, 20)));
            checkSegment(0, 0, 5, 0, 6);
            checkSegment(0, 0, 0, 5, 6);
            checkSegment(3, 1, -2, -2, 6);
            checkSegment(-4, 2, -4, 2, 1);
        }

        private static void checkSegment(int x0, int y0, int x1, int y1, int expectedCount) {
            List<Point> pixels = Bresenham.line(x0, y0, x1, y1);
            int span = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) + 1;
            boolean endpoints = !pixels.isEmpty()
                    && pixels.get(0).equals(new Point(x0, y0))
                    && pixels.get(pixels.size() - 1).equals(new Point(x1, y1));
            expect(pixels.size() == expectedCount && pixels.size() == span && endpoints,
                    String.format(Locale.US,
                            "(%d,%d)-(%d,%d) expected %d actual %d endpoints %s -> %s",
                            x0, y0, x1, y1, expectedCount, pixels.size(),
                            pixels.isEmpty() ? "?" : point(pixels.get(0)),
                            pixels.isEmpty() ? "?" : point(pixels.get(pixels.size() - 1))));
        }

        private static void checkRenderedTether() {
            checkFrame(1, 260, 227);
            checkFrame(6, 310, 182);
        }

        private static void checkFrame(double t, int tipX, int tipY) {
            Pose pose = Pose.at(t);
            expect(pose.tipX == tipX && pose.tipY == tipY, "frame tip t=" + t);
            BufferedImage image = render(t, true);
            expect(image.getWidth() == VIEW_W && image.getHeight() == VIEW_H, "frame size t=" + t);
            int tether = TETHER.getRGB();
            int drawn = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if (image.getRGB(x, y) == tether) {
                        drawn++;
                    }
                }
            }
            List<Point> expected = Bresenham.line(BEACON_X, BEACON_Y, pose.tipX, pose.tipY);
            boolean endpoints = image.getRGB(BEACON_X, BEACON_Y) == tether
                    && image.getRGB(pose.tipX, pose.tipY) == tether;
            boolean onlyLine = drawn == expected.size();
            if (onlyLine) {
                for (Point pixel : expected) {
                    if (image.getRGB(pixel.x, pixel.y) != tether) {
                        onlyLine = false;
                        break;
                    }
                }
            }
            expect(endpoints && onlyLine, String.format(Locale.US,
                    "t=%g tether pixels expected %d actual %d beacon=%s tip=%s",
                    t, expected.size(), drawn, endpoints, onlyLine));
        }

        private static int countDrawn(int x0, int y0, int x1, int y1) {
            int minX = Math.min(x0, x1) - 2;
            int minY = Math.min(y0, y1) - 2;
            int maxX = Math.max(x0, x1) + 2;
            int maxY = Math.max(y0, y1) + 2;
            int w = maxX - minX + 1;
            int h = maxY - minY + 1;
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = image.createGraphics();
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, w, h);
            g2.translate(-minX, -minY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setColor(Color.BLACK);
            Bresenham.draw(g2, x0, y0, x1, y1);
            g2.dispose();
            int count = 0;
            int black = Color.BLACK.getRGB();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (image.getRGB(x, y) == black) {
                        count++;
                    }
                }
            }
            return count;
        }

        private static boolean matches(List<Point> pixels, int[][] expected) {
            if (pixels.size() != expected.length) {
                return false;
            }
            for (int i = 0; i < expected.length; i++) {
                if (pixels.get(i).x != expected[i][0] || pixels.get(i).y != expected[i][1]) {
                    return false;
                }
            }
            return true;
        }

        private static boolean samePoints(List<Point> a, List<Point> b) {
            return new HashSet<>(a).equals(new HashSet<Point>(b));
        }

        private static String format(List<Point> pixels) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < pixels.size(); i++) {
                if (i > 0) {
                    text.append(' ');
                }
                text.append(point(pixels.get(i)));
            }
            return text.toString();
        }

        private static String point(Point pixel) {
            return "(" + pixel.x + "," + pixel.y + ")";
        }

        private static void sleep(long millis) throws IOException {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException(exception);
            }
        }

        private static void expect(boolean ok, String message) {
            if (ok) {
                System.out.println("OK   " + message);
            } else {
                failures++;
                System.out.println("FAIL " + message);
            }
        }
    }
}

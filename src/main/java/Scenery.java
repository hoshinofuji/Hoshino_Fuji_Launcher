import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 海报里的小动态：水面闪光、蹭腿的小猫（眨眼 / 摇尾巴 / 冒爱心）、四处慢飞的萤火虫。
 * 所有坐标都是 poster-base.png 的像素坐标，由 SkinView 传入缩放和偏移。
 */
final class Scenery {
    enum Layer { BACK, FRONT }

    // ---- 小猫（原位置在 x≈492，往左挪到她腿边）----
    private static final double CAT_DX = -52;
    private static final double BODY_X = 492, BODY_Y = 518;
    private static final double TAIL_X = 587, TAIL_Y = 562;
    private static final double TAIL_PIVOT_X = 603, TAIL_PIVOT_Y = 640;
    private static final double FEET_X = 528, FEET_Y = 668;   // 蹭腿时绕左前脚倾斜

    private final Image catBody = load("/theme/cat-body.png");
    private final Image catTail = load("/theme/cat-tail.png");
    private final Image catBlink = load("/theme/cat-blink.png");
    private final Random random;

    private double catBlinkStart = -10, nextCatBlink = 2.0;
    private double nextHeart = 1.0;
    private final List<double[]> hearts = new ArrayList<>();     // x, y, born, drift
    private final List<double[]> glints = new ArrayList<>();     // x, y, born, life, size
    private double nextGlint = 0;
    private int[] glintSpots;
    private final double[][] flies;                              // x, y, heading, speed, phase, twinkle
    private double lastTime = -1;

    Scenery(Random random) {
        this.random = random;
        flies = new double[16][];
        for (int i = 0; i < flies.length; i++)
            flies[i] = new double[] {
                    20 + random.nextDouble() * 610, 200 + random.nextDouble() * 520,
                    random.nextDouble() * Math.PI * 2, 7 + random.nextDouble() * 10,
                    random.nextDouble() * 100, 0.6 + random.nextDouble() * 1.1};
    }

    void setGlintSpots(int[] xy) { glintSpots = xy; }

    void draw(GraphicsContext g, double ox, double oy, double s, double now, Layer layer) {
        if (layer == Layer.BACK) {
            double dt = lastTime < 0 ? 0 : Math.min(0.1, now - lastTime);
            lastTime = now;
            moveFlies(dt, now);
            drawGlints(g, ox, oy, s, now);
            drawCat(g, ox, oy, s, now);
        } else {
            drawHearts(g, ox, oy, s, now);
            drawFlies(g, ox, oy, s, now);
        }
    }

    // ---------------------------------------------------------------- 水面闪光

    private void drawGlints(GraphicsContext g, double ox, double oy, double s, double now) {
        if (glintSpots == null || glintSpots.length == 0) return;
        while (now >= nextGlint) {
            int k = random.nextInt(glintSpots.length / 2);
            glints.add(new double[] {glintSpots[2 * k], glintSpots[2 * k + 1], now,
                    0.5 + random.nextDouble() * 0.8, 1.2 + random.nextDouble() * 1.8});
            nextGlint = now + 0.08 + random.nextDouble() * 0.16;
        }
        g.save();
        for (Iterator<double[]> it = glints.iterator(); it.hasNext(); ) {
            double[] p = it.next();
            double u = (now - p[2]) / p[3];
            if (u >= 1) { it.remove(); continue; }
            double a = Math.sin(Math.PI * u);
            a *= a;
            double x = ox + p[0] * s, y = oy + p[1] * s, r = p[4] * s * (0.6 + 0.4 * a);
            g.setFill(Color.rgb(255, 236, 200, 0.55 * a));
            g.fillRect(x - r * 1.8, y - s * 0.35, r * 3.6, s * 0.7);      // 横向的细光
            g.setFill(Color.rgb(255, 250, 235, 0.8 * a));
            g.fillRect(x - r * 0.45, y - r * 0.45, r * 0.9, r * 0.9);
        }
        g.restore();
    }

    // ---------------------------------------------------------------- 小猫

    /** 蹭腿：0..1，大约 1.5 秒一次，力度有轻有重。 */
    private static double rub(double now) {
        double base = 0.5 - 0.5 * Math.cos(2 * Math.PI * now / 1.5);
        return base * (0.8 + 0.2 * Math.sin(now * 0.37));
    }

    private void drawCat(GraphicsContext g, double ox, double oy, double s, double now) {
        if (catBody == null) return;
        double rub = rub(now);
        double lean = -(1.0 + 6.5 * rub);                  // 负值：往左（往她腿上）倒
        double squash = 1 - 0.025 * rub;

        // 地面阴影
        g.setFill(Color.rgb(8, 20, 12, 0.32));
        g.fillOval(ox + (FEET_X + CAT_DX - 22) * s, oy + (FEET_Y - 5) * s, 110 * s, 11 * s);

        g.save();
        g.translate(ox + (FEET_X + CAT_DX - 2.5 * rub) * s, oy + FEET_Y * s);
        g.rotate(lean);
        g.scale(1, squash);
        g.translate(-FEET_X * s, -FEET_Y * s);

        if (catTail != null) {                             // 尾巴先画，在身体后面
            double wag = 9 * Math.sin(2 * Math.PI * now / 2.2) + 4 * Math.sin(2 * Math.PI * now / 0.9);
            g.save();
            g.translate(TAIL_PIVOT_X * s, TAIL_PIVOT_Y * s);
            g.rotate(wag);
            g.drawImage(catTail, (TAIL_X - TAIL_PIVOT_X) * s, (TAIL_Y - TAIL_PIVOT_Y) * s,
                    catTail.getWidth() * s, catTail.getHeight() * s);
            g.restore();
        }
        g.drawImage(catBody, BODY_X * s, BODY_Y * s, catBody.getWidth() * s, catBody.getHeight() * s);

        // 眨眼；蹭得最用力的时候会眯眼享受
        if (now >= nextCatBlink) { catBlinkStart = now; nextCatBlink = now + 2.2 + random.nextDouble() * 3.5; }
        double blink = blinkCurve(now - catBlinkStart);
        int cycle = (int) Math.floor(now / 1.5);
        if (cycle % 3 == 1) blink = Math.max(blink, smooth((rub - 0.62) / 0.25));
        if (catBlink != null && blink > 0.01) {
            g.setGlobalAlpha(blink);
            g.drawImage(catBlink, BODY_X * s, BODY_Y * s, catBlink.getWidth() * s, catBlink.getHeight() * s);
            g.setGlobalAlpha(1);
        }
        g.restore();

        // 蹭到最里面的时候冒爱心
        if (now >= nextHeart && rub > 0.82) {
            hearts.add(new double[] {FEET_X + CAT_DX + 15 - 18 * rub, BODY_Y + 4, now, random.nextDouble() * 6.28});
            nextHeart = now + 1.1 + random.nextDouble() * 1.6;
        }
    }

    private static final String[] HEART = {
            ".##.##.",
            "#######",
            "#######",
            ".#####.",
            "..###..",
            "...#..."};

    private void drawHearts(GraphicsContext g, double ox, double oy, double s, double now) {
        for (Iterator<double[]> it = hearts.iterator(); it.hasNext(); ) {
            double[] h = it.next();
            double u = (now - h[2]) / 2.0;
            if (u >= 1) { it.remove(); continue; }
            double alpha = Math.min(1, u / 0.12) * (1 - smooth((u - 0.6) / 0.4));
            double size = (1.6 + 1.0 * smooth(u / 0.25)) * s;
            double cx = ox + (h[0] + 5 * Math.sin(h[3] + u * 7)) * s;
            double cy = oy + (h[1] - 48 * smooth(u * 1.1)) * s;
            double x0 = cx - 3.5 * size, y0 = cy - 3 * size;
            g.setGlobalAlpha(alpha);
            g.setFill(Color.web("#b8325f"));               // 深色描边
            for (int r = 0; r < HEART.length; r++)
                for (int c = 0; c < 7; c++)
                    if (HEART[r].charAt(c) == '#')
                        g.fillRect(x0 + c * size - size * 0.35, y0 + r * size - size * 0.35, size * 1.7, size * 1.7);
            g.setFill(Color.web("#ff6f9f"));
            for (int r = 0; r < HEART.length; r++)
                for (int c = 0; c < 7; c++)
                    if (HEART[r].charAt(c) == '#') g.fillRect(x0 + c * size, y0 + r * size, size + 0.5, size + 0.5);
            g.setFill(Color.web("#ffd3e2"));
            g.fillRect(x0 + 1 * size, y0 + 1 * size, size + 0.5, size + 0.5);
            g.setGlobalAlpha(1);
        }
    }

    // ---------------------------------------------------------------- 萤火虫

    private void moveFlies(double dt, double now) {
        for (double[] f : flies) {
            f[2] += dt * (0.9 * Math.sin(now * 0.7 + f[4]) + 0.6 * Math.sin(now * 1.9 + f[4] * 1.7));
            // 轻轻拉回画面中间区域
            double cx = 325 - f[0], cy = 470 - f[1];
            if (f[0] < 10 || f[0] > 640 || f[1] < 170 || f[1] > 780) {
                double want = Math.atan2(cy, cx), diff = Math.atan2(Math.sin(want - f[2]), Math.cos(want - f[2]));
                f[2] += diff * Math.min(1, dt * 2.5);
            }
            f[0] += Math.cos(f[2]) * f[3] * dt;
            f[1] += Math.sin(f[2]) * f[3] * dt * 0.7 + Math.sin(now * 1.3 + f[4]) * 3 * dt;
        }
    }

    /** 萤火虫：恒定亮度的柔光点（不再闪烁，也不用混合模式，避免部分显卡上出现黑块）。 */
    private void drawFlies(GraphicsContext g, double ox, double oy, double s, double now) {
        for (double[] f : flies) {
            double x = ox + f[0] * s, y = oy + f[1] * s;
            for (int k = 3; k >= 1; k--) {                 // 三层同心圆叠出光晕
                double r = (2.2 + 2.6 * k) * s;
                g.setFill(Color.rgb(255, 222, 110, 0.07 + 0.03 * (3 - k)));
                g.fillOval(x - r, y - r, 2 * r, 2 * r);
            }
            double c = 1.5 * s;
            g.setFill(Color.rgb(255, 246, 190, 0.95));
            g.fillOval(x - c, y - c, 2 * c, 2 * c);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static double blinkCurve(double t) {
        final double close = 0.08, hold = 0.07, open = 0.12;
        if (t < 0 || t > close + hold + open) return 0;
        if (t < close) return smooth(t / close);
        if (t < close + hold) return 1;
        return 1 - smooth((t - close - hold) / open);
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    private static Image load(String path) {
        var in = Scenery.class.getResourceAsStream(path);
        return in == null ? null : new Image(in);
    }
}

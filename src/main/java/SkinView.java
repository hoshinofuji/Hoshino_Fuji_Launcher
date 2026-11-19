import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.zip.GZIPInputStream;

/**
 * 左侧海报：星野藤角色立绘 + 呼吸 / 发丝飘动 / 耳朵抖动 / 眨眼 / 挥手，
 * 随风摆动的草丛和小花、波光粼粼的水面，以及萤火虫和蹭腿冒爱心的小猫（见 Scenery）。
 *
 * 图层：
 *   poster-base.png   背景 + 角色（举起的手臂已移除）
 *   poster-warp.bin   位移场：角色组（呼吸、发丝、耳朵）/ 草丛组 / 水面组，外加水面闪光点位置
 *   poster-arm.png    整条手臂，绕肩部旋转实现挥手；肩部带一块圆形接口，转动时手臂始终连着身体
 *   poster-blink.png  闭眼贴片
 *
 * 对外接口与旧版一致（构造器 / start() / stop()），Launcher 无需改动。
 */
final class SkinView extends javafx.scene.layout.Region {

    // ---- 立绘坐标（单位：poster-base.png 像素）----
    private static final double ARM_X = 192, ARM_Y = 254;      // 手臂贴图左上角
    private static final double PIVOT_X = 301, PIVOT_Y = 384;  // 肩部旋转中心
    private static final double EYES_X = 310, EYES_Y = 248;    // 闭眼贴片左上角
    private static final double BREATH_PX = 2.0;               // 呼吸时上半身抬起的幅度

    private static final double WAVE_LENGTH = 2.1;
    private static final double FRAME_INTERVAL = 1 / 30.0;     // 形变刷新上限 30 fps

    private final Canvas canvas = new Canvas();
    private final Image base = load("/theme/poster-base.png");
    private final Image arm = load("/theme/poster-arm.png");
    private final Image eyes = load("/theme/poster-blink.png");
    private final Random random = new Random();
    private final AnimationTimer timer;
    private final Scenery scenery = new Scenery(random);

    // 形变数据
    private int[] basePixels;
    private int baseW, baseH;
    private int rx, ry, rw, rh;          // 形变区域
    private float[][][] fields;          // [组][分量][活动像素]；角色组 10 个分量，草丛组 2 个
    private int[][] active;              // [组][活动像素] → 区域内像素下标
    private float[][] activeX, activeY;  // 活动像素在原图中的坐标（预先算好，省掉除法）
    private float[][] scratchX, scratchY;
    private int[] regionPixels;
    private WritableImage region;

    // 时间线
    private long origin = -1;
    private double now, lastWarp = -1;
    private double nextBlink = 1.6, blinkStart = -10;
    private boolean doubleBlink;
    private double nextWave = 1.2, waveStart = -10;
    private double nextTwitch = 2.5, twitchStart = -10;
    private int twitchEars;              // 1 左耳, 2 右耳, 3 双耳
    private final double hairPhase = random.nextDouble() * 10;

    SkinView() {
        setMinSize(0, 0);
        setPrefSize(480, 420);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setAccessibleText("星野藤角色立绘，点击她会挥手");
        setCursor(Cursor.HAND);
        getChildren().add(canvas);
        setOnMouseClicked(e -> wave(true));

        try { loadWarp(); } catch (IOException | RuntimeException e) { fields = null; System.err.println("poster warp: " + e); }

        timer = new AnimationTimer() {
            @Override public void handle(long t) {
                if (origin < 0) origin = t;
                now = (t - origin) / 1e9;
                // 窗口最小化时不画，省电
                if (getScene() != null && getScene().getWindow() instanceof javafx.stage.Stage st && st.isIconified()) return;
                tick();
                render();
            }
        };

        sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) Platform.runLater(() -> UiPolish.apply(scene, this));
        });
    }

    void start() { timer.start(); }

    void stop() { timer.stop(); }

    @Override protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        render();
    }

    // ---------------------------------------------------------------- 动画时间线

    private void tick() {
        if (now >= nextBlink) {
            blinkStart = now;
            doubleBlink = random.nextDouble() < 0.22;
            nextBlink = now + 2.4 + random.nextDouble() * 3.4;
        }
        if (now >= nextWave) wave(false);
        if (now >= nextTwitch) {
            twitchStart = now;
            double r = random.nextDouble();
            twitchEars = r < 0.45 ? 1 : r < 0.85 ? 2 : 3;
            nextTwitch = now + 3.2 + random.nextDouble() * 5.5;
        }
    }

    private void wave(boolean byUser) {
        if (now - waveStart < WAVE_LENGTH * (byUser ? 0.55 : 1)) return;
        waveStart = now;
        nextWave = now + 6.5 + random.nextDouble() * 4.5;
        if (byUser) {
            blinkStart = now + 0.15;
            doubleBlink = false;
            if (now - twitchStart > 0.6) { twitchStart = now + 0.3; twitchEars = 3; }
        }
    }

    /** 呼吸 0..1，周期约 3.8 秒。 */
    private double breath() {
        return 0.5 - 0.5 * Math.cos(2 * Math.PI * now / 3.8);
    }

    private double blinkAmount() {
        double a = blinkCurve(now - blinkStart);
        if (doubleBlink) a = Math.max(a, blinkCurve(now - blinkStart - 0.28));
        return a;
    }

    private static double blinkCurve(double t) {
        final double close = 0.07, hold = 0.06, open = 0.10;
        if (t < 0 || t > close + hold + open) return 0;
        if (t < close) return smooth(t / close);
        if (t < close + hold) return 1;
        return 1 - smooth((t - close - hold) / open);
    }

    /** 耳朵抖动：快速向外一甩，回弹一点再落回。 */
    private static double twitchCurve(double t) {
        if (t < 0 || t > 0.5) return 0;
        if (t < 0.09) return smooth(t / 0.09);
        if (t < 0.20) return 1 - 1.35 * smooth((t - 0.09) / 0.11);
        return -0.35 * (1 - smooth((t - 0.20) / 0.30));
    }

    private double armAngle() {
        double idle = 1.6 * Math.sin(now * 1.25) + 0.6 * Math.sin(now * 2.9);
        double t = now - waveStart;
        if (t < 0 || t > WAVE_LENGTH) return idle;
        double env = smooth(Math.min(1, t / 0.25)) * smooth(Math.min(1, (WAVE_LENGTH - t) / 0.35));
        double swing = Math.sin(t * Math.PI * 2 * 1.55);
        return idle * (1 - env) + env * (-2 + 8 * swing);
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    // ---------------------------------------------------------------- 形变

    private void loadWarp() throws IOException {
        if (base == null) return;
        try (InputStream raw = SkinView.class.getResourceAsStream("/theme/poster-warp.bin")) {
            if (raw == null) return;
            DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new GZIPInputStream(raw, 1 << 16)));
            rx = in.readInt(); ry = in.readInt(); rw = in.readInt(); rh = in.readInt();
            int groups = in.readInt();
            float[][][] f = new float[groups][][];
            int[][] idx = new int[groups][];
            for (int g = 0; g < groups; g++) {
                int count = in.readInt(), n = in.readInt();
                idx[g] = new int[n];
                for (int i = 0; i < n; i++) idx[g][i] = in.readInt();
                f[g] = new float[count][n];
                for (int k = 0; k < count; k++)
                    for (int i = 0; i < n; i++) f[g][k][i] = Float.float16ToFloat(in.readShort());
            }
            fields = f;
            active = idx;
            if (groups > 3) {           // 第 4 组：水面上可以闪光的位置
                int[] spots = new int[idx[3].length * 2];
                for (int i = 0; i < idx[3].length; i++) {
                    spots[2 * i] = rx + idx[3][i] % rw;
                    spots[2 * i + 1] = ry + idx[3][i] / rw;
                }
                scenery.setGlintSpots(spots);
            }
            activeX = new float[groups][];
            activeY = new float[groups][];
            scratchX = new float[groups][];
            scratchY = new float[groups][];
            for (int g = 0; g < groups; g++) {
                activeX[g] = new float[idx[g].length];
                activeY[g] = new float[idx[g].length];
                scratchX[g] = new float[idx[g].length];
                scratchY[g] = new float[idx[g].length];
                for (int i = 0; i < idx[g].length; i++) {
                    activeX[g][i] = rx + idx[g][i] % rw;
                    activeY[g][i] = ry + idx[g][i] / rw;
                }
            }
        }
        baseW = (int) base.getWidth();
        baseH = (int) base.getHeight();
        basePixels = new int[baseW * baseH];
        base.getPixelReader().getPixels(0, 0, baseW, baseH, PixelFormat.getIntArgbInstance(), basePixels, 0, baseW);
        regionPixels = new int[rw * rh];
        for (int y = 0; y < rh; y++)
            System.arraycopy(basePixels, (ry + y) * baseW + rx, regionPixels, y * rw, rw);
        region = new WritableImage(rw, rh);
    }

    private void updateWarp() {
        if (fields == null || now - lastWarp < FRAME_INTERVAL) return;
        lastWarp = now;
        double t = now + hairPhase;
        // 角色：呼吸 / 左右发丝 / 左右耳朵
        float b = (float) (BREATH_PX * breath());
        float hl = (float) (1.5 * Math.sin(2 * Math.PI * t / 3.1) + 0.5 * Math.sin(2 * Math.PI * t / 1.7 + 1.1));
        float hr = (float) (1.5 * Math.sin(2 * Math.PI * t / 3.1 + 0.9) + 0.5 * Math.sin(2 * Math.PI * t / 1.9 + 2.0));
        double tw = twitchCurve(now - twitchStart);
        float el = (float) (-0.006 * Math.sin(now * 1.7) - ((twitchEars & 1) != 0 ? 0.12 * tw : 0));
        float er = (float) (0.006 * Math.sin(now * 1.9) + ((twitchEars & 2) != 0 ? 0.12 * tw : 0));
        warp(0, new float[] {b, hl, hr, el, er});
        // 草丛：一阵阵从左往右吹过的风
        if (fields.length > 1) {
            double gust = 0.75 + 0.35 * Math.sin(now * 0.55) + 0.15 * Math.sin(now * 1.9 + 0.7);
            double phase = 2 * Math.PI * now / 2.6;
            warp(1, new float[] {(float) (gust * Math.cos(phase)), (float) (gust * Math.sin(phase))});
        }
        // 水面：一圈圈往下推的细波纹
        if (fields.length > 2 && fields[2].length == 4) {
            double wp = 2 * Math.PI * now / 1.8;
            warp(2, new float[] {(float) (1.2 * Math.cos(wp)), (float) (1.2 * Math.sin(wp))});
        }
        region.getPixelWriter().setPixels(0, 0, rw, rh, PixelFormat.getIntArgbInstance(), regionPixels, 0, rw);
    }

    /** 对一组活动像素做反向映射 + 双线性采样。c[k] 对应分量 (2k, 2k+1) 的系数。 */
    private void warp(int group, float[] c) {
        float[][] f = fields[group];
        int[] idx = active[group];
        float[] ax = activeX[group], ay = activeY[group];
        int n = idx.length;
        float[] dx = scratchX[group], dy = scratchY[group];
        java.util.Arrays.fill(dx, 0f);
        java.util.Arrays.fill(dy, 0f);
        for (int k = 0; k < c.length; k++) {           // 按分量累加，内存访问连续
            float ck = c[k];
            if (Math.abs(ck) < 1e-5f) continue;
            float[] fx = f[2 * k], fy = f[2 * k + 1];
            for (int j = 0; j < n; j++) { dx[j] += ck * fx[j]; dy[j] += ck * fy[j]; }
        }
        int[] src = basePixels, out = regionPixels;
        int w = baseW, maxX = baseW - 2, maxY = baseH - 2;
        for (int j = 0; j < n; j++) {
            float sx = Math.max(0f, ax[j] + dx[j]), sy = Math.max(0f, ay[j] + dy[j]);
            int x0 = (int) sx, y0 = (int) sy;
            float tx = sx - x0, ty = sy - y0;
            if (x0 > maxX) { x0 = maxX; tx = 1; }
            if (y0 > maxY) { y0 = maxY; ty = 1; }
            int p = y0 * w + x0;
            out[idx[j]] = bilinear(src[p], src[p + 1], src[p + w], src[p + w + 1], tx, ty);
        }
    }

    private static int bilinear(int a, int b, int c, int d, float tx, float ty) {
        float w00 = (1 - tx) * (1 - ty), w10 = tx * (1 - ty), w01 = (1 - tx) * ty, w11 = tx * ty;
        int r = (int) (((a >> 16) & 0xff) * w00 + ((b >> 16) & 0xff) * w10 + ((c >> 16) & 0xff) * w01 + ((d >> 16) & 0xff) * w11 + 0.5f);
        int g = (int) (((a >> 8) & 0xff) * w00 + ((b >> 8) & 0xff) * w10 + ((c >> 8) & 0xff) * w01 + ((d >> 8) & 0xff) * w11 + 0.5f);
        int bl = (int) ((a & 0xff) * w00 + (b & 0xff) * w10 + (c & 0xff) * w01 + (d & 0xff) * w11 + 0.5f);
        return 0xff000000 | (r << 16) | (g << 8) | bl;
    }


    // ---------------------------------------------------------------- 绘制

    private void render() {
        double w = canvas.getWidth(), h = canvas.getHeight();
        if (w <= 0 || h <= 0 || base == null) return;
        updateWarp();
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setImageSmoothing(true);
        g.clearRect(0, 0, w, h);

        // cover 缩放：水平居中，垂直偏向底部（保住脚和签名）
        double iw = base.getWidth(), ih = base.getHeight();
        double s = Math.max(w / iw, h / ih);
        double ox = (w - iw * s) / 2;
        double oy = (h - ih * s) * 0.72;
        double lift = fields == null ? 0 : BREATH_PX * breath();

        g.drawImage(base, ox, oy, iw * s, ih * s);
        if (region != null) g.drawImage(region, ox + rx * s, oy + ry * s, rw * s, rh * s);
        scenery.draw(g, ox, oy, s, now, Scenery.Layer.BACK);

        if (arm != null) {
            g.save();
            g.translate(ox + PIVOT_X * s, oy + (PIVOT_Y - lift) * s);
            g.rotate(armAngle());
            g.drawImage(arm, (ARM_X - PIVOT_X) * s, (ARM_Y - PIVOT_Y) * s, arm.getWidth() * s, arm.getHeight() * s);
            g.restore();
        }

        double blink = blinkAmount();
        if (eyes != null && blink > 0.01) {
            g.setGlobalAlpha(blink);
            g.drawImage(eyes, ox + EYES_X * s, oy + (EYES_Y - lift) * s, eyes.getWidth() * s, eyes.getHeight() * s);
            g.setGlobalAlpha(1);
        }

        scenery.draw(g, ox, oy, s, now, Scenery.Layer.FRONT);

        // 顶部轻微压暗，让导航文字更清楚
        g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#0b1d18", 0.55)), new Stop(1, Color.TRANSPARENT)));
        g.fillRect(0, 0, w, Math.min(90, h));
    }

    private static Image load(String path) {
        var in = SkinView.class.getResourceAsStream(path);
        return in == null ? null : new Image(in);
    }
}

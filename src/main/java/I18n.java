import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.prefs.Preferences;

/**
 * 界面语言切换。
 *
 * 启动器的文字都是简体中文写死在代码里的，这里不改动原有逻辑，而是在显示层翻译：
 * 定时扫描所有窗口（包括下拉列表、对话框），把 Label / 按钮 / 提示文字 / 窗口标题
 * 按 /theme/i18n.tsv 词表换成目标语言；原文记在节点上，切回简体中文时还原。
 * 带变量的句子（如「正在安装 26.1」）用 {0}、{1} 模板匹配。
 */
final class I18n {
    enum Lang {
        ZH_CN("简体中文"), ZH_HK("繁體中文（香港）"), ZH_TW("繁體中文（台灣）"), EN("English"), JA("日本語");

        final String display;

        Lang(String display) { this.display = display; }

        @Override public String toString() { return display; }
    }

    private record Template(Pattern pattern, String[] targets) {}
    private static final class State { String src, shown; Lang lang; }

    private static final String KEY = "hfml.i18n";
    private static final Preferences PREFS = Preferences.userRoot().node("hoshino-fuji-launcher");
    private static final Map<String, String[]> exact = new HashMap<>();
    private static final List<Template> templates = new ArrayList<>();
    private static Lang lang = Lang.ZH_CN;
    private static Timeline sweep;

    static Lang current() { return lang; }

    static void init() {
        if (sweep != null) return;
        load();
        try { lang = Lang.valueOf(PREFS.get("lang", Lang.ZH_CN.name())); } catch (IllegalArgumentException e) { lang = Lang.ZH_CN; }
        sweep = new Timeline(new KeyFrame(Duration.millis(350), e -> sweepAll()));
        sweep.setCycleCount(Timeline.INDEFINITE);
        sweep.play();
        sweepAll();
    }

    static void set(Lang next) {
        if (next == null || next == lang) return;
        lang = next;
        PREFS.put("lang", next.name());
        sweepAll();
    }

    // ---------------------------------------------------------------- 翻译

    static String tr(String s) {
        if (s == null || s.isEmpty() || lang == Lang.ZH_CN) return s;
        int col = lang.ordinal() - 1;
        String[] hit = exact.get(s);
        if (hit != null) return hit[col].isEmpty() ? s : hit[col];
        for (Template t : templates) {
            Matcher m = t.pattern.matcher(s);
            if (!m.matches()) continue;
            String out = t.targets[col];
            if (out.isEmpty()) return s;
            for (int g = 1; g <= m.groupCount(); g++)
                out = out.replace("{" + (g - 1) + "}", tr(m.group(g)));
            return out;
        }
        return s;
    }

    private static void load() {
        try (InputStream in = I18n.class.getResourceAsStream("/theme/i18n.tsv")) {
            if (in == null) return;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            boolean header = true;
            while ((line = r.readLine()) != null) {
                if (header) { header = false; continue; }
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] cols = line.split("\t", -1);
                if (cols.length < 5) continue;
                String key = unescape(cols[0]);
                String[] targets = new String[4];
                for (int i = 0; i < 4; i++) targets[i] = unescape(cols[i + 1]);
                if (key.contains("{0}")) templates.add(new Template(compile(key), targets));
                else exact.put(key, targets);
            }
            // 更长、更具体的模板优先
            templates.sort((a, b) -> b.pattern.pattern().length() - a.pattern.pattern().length());
        } catch (Exception e) {
            System.err.println("i18n: " + e);
        }
    }

    private static Pattern compile(String key) {
        StringBuilder re = new StringBuilder("^");
        Matcher m = Pattern.compile("\\{\\d}").matcher(key);
        int last = 0;
        while (m.find()) {
            re.append(Pattern.quote(key.substring(last, m.start()))).append("(.+?)");
            last = m.end();
        }
        re.append(Pattern.quote(key.substring(last))).append("$");
        return Pattern.compile(re.toString(), Pattern.DOTALL);
    }

    private static String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\t", "\t");
    }

    // ---------------------------------------------------------------- 扫描界面

    private static void sweepAll() {
        for (Window w : new ArrayList<>(Window.getWindows())) {
            Scene scene = w.getScene();
            if (scene != null && scene.getRoot() != null) walk(scene.getRoot());
            if (w instanceof Stage stage) track(stage.getProperties(), stage.titleProperty(), stage::setTitle);
        }
    }

    private static void walk(Node node) {
        if (node instanceof Labeled l) track(l.getProperties(), l.textProperty(), l::setText);
        if (node instanceof TextInputControl t) track(t.getProperties(), t.promptTextProperty(), t::setPromptText);
        if (node.getProperties().get("javafx.scene.control.Tooltip") instanceof Tooltip tip)
            track(tip.getProperties(), tip.textProperty(), tip::setText);
        if (node instanceof Parent p)
            for (Node child : p.getChildrenUnmodifiable()) walk(child);
    }

    /** 给一个文字属性挂上翻译：记住原文，文字变化时重新翻译，语言变化时整体刷新。 */
    private static void track(Map<Object, Object> props, StringProperty text, Consumer<String> setter) {
        Object existing = props.get(KEY + text.getName());
        State st;
        if (existing instanceof State s) {
            st = s;
        } else {
            st = new State();
            st.src = text.get();
            props.put(KEY + text.getName(), st);
            text.addListener((o, old, val) -> {
                if (val == null || val.equals(st.shown)) return;
                st.src = val;
                apply(st, text, setter);
            });
        }
        if (st.lang != lang) apply(st, text, setter);
    }

    private static void apply(State st, StringProperty text, Consumer<String> setter) {
        String out = tr(st.src);
        st.shown = out;
        st.lang = lang;
        if (out != null && !out.equals(text.get()) && !text.isBound()) setter.accept(out);
    }

    private I18n() {}
}

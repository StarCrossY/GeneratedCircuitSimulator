package simcore;

/**
 * 数值/单位解析工具。
 *
 * <p>支持 SPICE 风格的带后缀数值，例如 {@code 1k}、{@code 100u}、{@code 2meg}。
 * 后缀大小写不敏感；注意 SPICE 约定里 {@code m} 表示毫 (1e-3)，兆用 {@code meg} 表示。</p>
 *
 * <p>此类属于“可迁移核心”，不依赖任何 UI 框架。</p>
 */
public final class Units {

    /** 私有构造器，禁止实例化。 */
    private Units() {
    }

    /**
     * 解析带可选后缀的数值字符串为 double。
     *
     * @param text 数值字符串，例如 "1k"、"100u"、"2meg"、"0.01"
     * @return 解析后的数值
     * @throws NumberFormatException 当字符串不是合法数值时抛出
     */
    public static double parse(String text) {
        String s = text == null ? "" : text.trim();
        if (s.isEmpty()) {
            throw new NumberFormatException("empty string cannot be parsed as a number");
        }
        String lower = s.toLowerCase();

        // 后缀表，按长度从长到短排列，避免 "meg" 被 "m" 抢先匹配。
        double scale = 1.0;
        String number = lower;
        // meg 必须先于 m 判断
        if (lower.endsWith("meg")) {
            scale = 1e6;
            number = lower.substring(0, lower.length() - 3);
        } else if (lower.endsWith("k")) {
            scale = 1e3;
            number = strip(lower);
        } else if (lower.endsWith("m")) {
            scale = 1e-3;
            number = strip(lower);
        } else if (lower.endsWith("u")) {
            scale = 1e-6;
            number = strip(lower);
        } else if (lower.endsWith("n")) {
            scale = 1e-9;
            number = strip(lower);
        } else if (lower.endsWith("p")) {
            scale = 1e-12;
            number = strip(lower);
        } else if (lower.endsWith("f")) {
            scale = 1e-15;
            number = strip(lower);
        } else if (lower.endsWith("t")) {
            scale = 1e12;
            number = strip(lower);
        } else if (lower.endsWith("g")) {
            scale = 1e9;
            number = strip(lower);
        }

        return Double.parseDouble(number) * scale;
    }

    /** 去掉最后一个后缀字符，返回剩余数值部分。 */
    private static String strip(String s) {
        return s.substring(0, s.length() - 1);
    }
}
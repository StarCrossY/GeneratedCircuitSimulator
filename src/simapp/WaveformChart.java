package simapp;

import javax.swing.JPanel;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 波形绘制组件：以时间序列绘制多条曲线，并带坐标轴、网格与图例。
 *
 * <p>这是“可视化外部调用层”的一部分，仅依赖 Swing，不依赖核心库内部实现。</p>
 */
public final class WaveformChart extends JPanel {

    /** 曲线配色。 */
    private static final Color[] PALETTE = {
            new Color(0x1f77b4), new Color(0xff7f0e), new Color(0x2ca02c),
            new Color(0xd62728), new Color(0x9467bd), new Color(0x8c564b),
            new Color(0xe377c2), new Color(0x17becf), new Color(0x7f7f7f),
    };

    /** 一条曲线。 */
    private static final class Series {
        final String name;
        final double[] values;
        final Color color;

        Series(String name, double[] values, Color color) {
            this.name = name;
            this.values = values;
            this.color = color;
        }
    }

    private double[] time = new double[0];
    private final List<Series> series = new ArrayList<>();

    public WaveformChart() {
        setBackground(Color.WHITE);
        setPreferredSize(new java.awt.Dimension(640, 420));
    }

    /**
     * 设置待绘制的数据。
     *
     * @param time   时间序列
     * @param curves 曲线名 -> 数值序列（保持传入顺序）
     */
    public void setData(double[] time, Map<String, double[]> curves) {
        this.time = time == null ? new double[0] : time;
        this.series.clear();
        if (curves != null) {
            int i = 0;
            for (Map.Entry<String, double[]> e : curves.entrySet()) {
                series.add(new Series(e.getKey(), e.getValue(), PALETTE[i % PALETTE.length]));
                i++;
            }
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }

        // 边距：左侧留出 y 轴刻度，底部留出 x 轴刻度，右侧留出图例空间
        int left = 70;
        int right = 150;
        int top = 20;
        int bottom = 40;
        int plotW = w - left - right;
        int plotH = h - top - bottom;
        if (plotW <= 0 || plotH <= 0) {
            return;
        }

        // 数据范围
        double xMin = Double.POSITIVE_INFINITY;
        double xMax = Double.NEGATIVE_INFINITY;
        double yMin = Double.POSITIVE_INFINITY;
        double yMax = Double.NEGATIVE_INFINITY;
        for (double t : time) {
            xMin = Math.min(xMin, t);
            xMax = Math.max(xMax, t);
        }
        for (Series s : series) {
            for (double v : s.values) {
                yMin = Math.min(yMin, v);
                yMax = Math.max(yMax, v);
            }
        }
        if (Double.isInfinite(xMin)) {
            xMin = 0;
            xMax = 1;
        }
        if (Double.isInfinite(yMin)) {
            yMin = -1;
            yMax = 1;
        }
        if (xMax - xMin < 1e-12) {
            xMax = xMin + 1;
        }
        if (yMax - yMin < 1e-12) {
            yMax = yMin + 1;
        }
        // y 轴留 5% 余量
        double yPad = (yMax - yMin) * 0.05;
        yMin -= yPad;
        yMax += yPad;

        // 网格与坐标轴
        g2.setColor(new Color(0xe0e0e0));
        g2.setStroke(new BasicStroke(1f));
        int gridX = 5;
        int gridY = 5;
        for (int i = 0; i <= gridX; i++) {
            int x = left + i * plotW / gridX;
            g2.drawLine(x, top, x, top + plotH);
        }
        for (int i = 0; i <= gridY; i++) {
            int y = top + i * plotH / gridY;
            g2.drawLine(left, y, left + plotW, y);
        }

        // 坐标轴框线
        g2.setColor(Color.BLACK);
        g2.drawRect(left, top, plotW, plotH);

        // 刻度文字
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        for (int i = 0; i <= gridX; i++) {
            double xv = xMin + (xMax - xMin) * i / gridX;
            int x = left + i * plotW / gridX;
            String label = format(xv);
            int tx = x - g2.getFontMetrics().stringWidth(label) / 2;
            g2.drawString(label, tx, top + plotH + 16);
        }
        for (int i = 0; i <= gridY; i++) {
            double yv = yMax - (yMax - yMin) * i / gridY;
            int y = top + i * plotH / gridY;
            String label = format(yv);
            g2.drawString(label, left - 8 - g2.getFontMetrics().stringWidth(label), y + 4);
        }

        // 曲线
        for (Series s : series) {
            g2.setColor(s.color);
            g2.setStroke(new BasicStroke(1.8f));
            double[] v = s.values;
            for (int i = 0; i + 1 < time.length && i + 1 < v.length; i++) {
                int x1 = toX(time[i], xMin, xMax, left, plotW);
                int y1 = toY(v[i], yMin, yMax, top, plotH);
                int x2 = toX(time[i + 1], xMin, xMax, left, plotW);
                int y2 = toY(v[i + 1], yMin, yMax, top, plotH);
                g2.drawLine(x1, y1, x2, y2);
            }
        }

        // 图例
        int legendX = left + plotW + 14;
        int legendY = top;
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (Series s : series) {
            g2.setColor(s.color);
            g2.fillRect(legendX, legendY, 12, 12);
            g2.setColor(Color.BLACK);
            g2.drawString(s.name, legendX + 18, legendY + 11);
            legendY += 20;
        }
    }

    private static int toX(double xv, double xMin, double xMax, int left, int plotW) {
        return left + (int) Math.round((xv - xMin) / (xMax - xMin) * plotW);
    }

    private static int toY(double yv, double yMin, double yMax, int top, int plotH) {
        return top + (int) Math.round((yMax - yv) / (yMax - yMin) * plotH);
    }

    /** 刻度数字格式化：自动选择合适精度。 */
    private static String format(double v) {
        double a = Math.abs(v);
        if (a == 0) {
            return "0";
        }
        if (a >= 1000 || a < 0.001) {
            return String.format("%.2e", v);
        }
        return String.format("%.4g", v);
    }

    /** 便捷方法：构造单条曲线时使用。 */
    public static Map<String, double[]> single(String name, double[] values) {
        Map<String, double[]> m = new LinkedHashMap<>();
        m.put(name, values);
        return m;
    }
}
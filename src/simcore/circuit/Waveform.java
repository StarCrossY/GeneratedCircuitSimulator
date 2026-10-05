package simcore.circuit;

import simcore.Units;

/**
 * 独立源（电压源 V、电流源 I）的时变波形定义。
 *
 * <p>支持三种波形：</p>
 * <ul>
 *   <li>{@code DC}：常量值。</li>
 *   <li>{@code SIN}：{@code v(t)=offset+amplitude*sin(2πf·t+phase)}。</li>
 *   <li>{@code PULSE}：分段线性脉冲 {@code pulse(v1 v2 delay rise fall width period)}。</li>
 * </ul>
 */
public abstract class Waveform {

    /**
     * 返回 t 时刻的波形值。
     *
     * @param t 时间 (s)
     * @return 波形值
     */
    public abstract double valueAt(double t);

    /** 解析波形字符串为 {@link Waveform}。 */
    public static Waveform parse(String spec) {
        String s = spec.trim().toLowerCase();
        if (s.startsWith("sin")) {
            return new SinWaveform(args(s));
        }
        if (s.startsWith("pulse")) {
            return new PulseWaveform(args(s));
        }
        // 其余视为直流：去掉可能的 "dc" 前缀
        String num = s.startsWith("dc") ? s.substring(2).trim() : s;
        return new DcWaveform(Units.parse(num));
    }

    /** 提取波形括号内的参数数组。 */
    private static String[] args(String spec) {
        int open = spec.indexOf('(');
        int close = spec.lastIndexOf(')');
        if (open < 0 || close < open) {
            return new String[0];
        }
        String body = spec.substring(open + 1, close).trim();
        return body.isEmpty() ? new String[0] : body.split("[,\\s]+");
    }

    /** 常量波形。 */
    public static final class DcWaveform extends Waveform {
        private final double value;

        public DcWaveform(double value) {
            this.value = value;
        }

        @Override
        public double valueAt(double t) {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    /** 正弦波形：offset + amplitude*sin(2πf·t + φ)。 */
    public static final class SinWaveform extends Waveform {
        private final double offset;
        private final double amplitude;
        private final double freq;
        private final double phase; // 弧度

        /** @param args [offset, amplitude, freq, phase(deg)?] */
        public SinWaveform(String[] args) {
            this.offset = args.length > 0 ? Units.parse(args[0]) : 0;
            this.amplitude = args.length > 1 ? Units.parse(args[1]) : 0;
            this.freq = args.length > 2 ? Units.parse(args[2]) : 0;
            double phaseDeg = args.length > 3 ? Units.parse(args[3]) : 0;
            this.phase = Math.toRadians(phaseDeg);
        }

        @Override
        public double valueAt(double t) {
            return offset + amplitude * Math.sin(2 * Math.PI * freq * t + phase);
        }

        @Override
        public String toString() {
            return "sin(" + offset + " " + amplitude + " " + freq + ")";
        }
    }

    /**
     * 周期脉冲波形。
     * <p>参数：v1(低电平) v2(高电平) delay(延迟) rise(上升时间) fall(下降时间)
     * width(脉宽) period(周期)。</p>
     */
    public static final class PulseWaveform extends Waveform {
        private final double v1;
        private final double v2;
        private final double delay;
        private final double rise;
        private final double fall;
        private final double width;
        private final double period;

        /** @param args [v1 v2 delay rise fall width period]，缺省顺序补齐。 */
        public PulseWaveform(String[] args) {
            this.v1 = args.length > 0 ? Units.parse(args[0]) : 0;
            this.v2 = args.length > 1 ? Units.parse(args[1]) : 1;
            this.delay = args.length > 2 ? Units.parse(args[2]) : 0;
            this.rise = args.length > 3 ? Units.parse(args[3]) : 0;
            this.fall = args.length > 4 ? Units.parse(args[4]) : 0;
            this.width = args.length > 5 ? Units.parse(args[5]) : 0;
            this.period = args.length > 6 ? Units.parse(args[6]) : 0;
        }

        @Override
        public double valueAt(double t) {
            if (period <= 0) {
                // 单次脉冲
                return singlePulse(t);
            }
            // 进入周期内的时间
            double tp = (t - delay) % period;
            if (tp < 0) {
                tp += period;
            }
            return singlePulse(tp + delay);
        }

        /** 计算单个脉冲在时刻 t 的值。 */
        private double singlePulse(double t) {
            if (t < delay) {
                return v1;
            }
            double t1 = delay + rise;          // 上升段结束
            double t2 = t1 + width;            // 高电平平台结束
            double t3 = t2 + fall;             // 下降段结束
            if (t < t1) {
                return rise > 0 ? v1 + (v2 - v1) * (t - delay) / rise : v2;
            }
            if (t < t2) {
                return v2;
            }
            if (t < t3) {
                return fall > 0 ? v2 + (v1 - v2) * (t - t2) / fall : v1;
            }
            return v1;
        }

        @Override
        public String toString() {
            return "pulse(" + v1 + " " + v2 + " " + delay + " " + rise + " "
                    + fall + " " + width + " " + period + ")";
        }
    }
}
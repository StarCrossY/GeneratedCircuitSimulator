package simcore.circuit;

import java.util.Map;

/**
 * 独立电压源：支路电流未知量 i 的正方向为从 n+ 流入源（经 n− 流出）。
 * <p>约束方程：V(n+) − V(n−) = vs(t)。直流分析时取 t=0 的波形值。</p>
 */
public final class VoltageSource extends Device {

    private final Waveform waveform;

    public VoltageSource(String name, String nPlus, String nMinus, Waveform waveform) {
        super(name, new String[] {nPlus, nMinus});
        this.waveform = waveform;
    }

    @Override
    public int numExtraVars() {
        return 1;
    }

    @Override
    public void stamp(MnaSystem sys, double[] x, boolean dc, double t, double h) {
        int p = n[0];
        int m = n[1];
        int bi = branchIndex;
        double vs = waveform.valueAt(dc ? 0.0 : t);

        if (p >= 0) {
            sys.add(p, bi, 1.0);
        }
        if (m >= 0) {
            sys.add(m, bi, -1.0);
        }
        if (p >= 0) {
            sys.add(bi, p, 1.0);
        }
        if (m >= 0) {
            sys.add(bi, m, -1.0);
        }
        sys.addRhs(bi, vs);
    }

    @Override
    public Map<String, Double> outputs(double[] x, boolean dc, double t) {
        Map<String, Double> out = newOutputs();
        // 报告源电流（支路未知量），正方向为从 n+ 流入源
        out.put("I(" + name + ")", branchCurrent(x));
        return out;
    }
}
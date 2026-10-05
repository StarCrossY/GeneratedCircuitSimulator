package simcore.circuit;

import java.util.Map;

/**
 * 电压控制电压源（VCVS，SPICE 中的 E 器件）。
 *
 * <p>语法：{@code E<名> n+ n- nc+ nc- gain}。输出电压
 * {@code V(n+) − V(n−) = gain·(V(nc+) − V(nc−))}。该器件表现为一个受控电压源，
 * 因此与 {@link VoltageSource} 一样占用一个支路电流未知量，其约束方程写入系数矩阵。</p>
 */
public final class Vcvs extends Device {

    /** 增益。 */
    private final double gain;

    /** 端顺序：0=n+，1=n−，2=nc+，3=nc−。 */
    public Vcvs(String name, String nPlus, String nMinus, String cPlus, String cMinus, double gain) {
        super(name, new String[] {nPlus, nMinus, cPlus, cMinus});
        this.gain = gain;
    }

    @Override
    public int numExtraVars() {
        return 1;
    }

    @Override
    public void stamp(MnaSystem sys, double[] x, boolean dc, double t, double h) {
        int p = n[0];
        int m = n[1];
        int cp = n[2];
        int cm = n[3];
        int bi = branchIndex;

        // 节点 KCL：支路电流 i 从 n+ 流入器件、从 n− 流出
        if (p >= 0) {
            sys.add(p, bi, 1.0);
        }
        if (m >= 0) {
            sys.add(m, bi, -1.0);
        }
        // 约束方程：V(n+) − V(n−) − gain·V(nc+) + gain·V(nc−) = 0
        if (p >= 0) {
            sys.add(bi, p, 1.0);
        }
        if (m >= 0) {
            sys.add(bi, m, -1.0);
        }
        if (cp >= 0) {
            sys.add(bi, cp, -gain);
        }
        if (cm >= 0) {
            sys.add(bi, cm, gain);
        }
    }

    @Override
    public Map<String, Double> outputs(double[] x, boolean dc, double t) {
        Map<String, Double> out = newOutputs();
        out.put("I(" + name + ")", branchCurrent(x));
        return out;
    }
}
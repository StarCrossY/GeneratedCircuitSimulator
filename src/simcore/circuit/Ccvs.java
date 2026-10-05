package simcore.circuit;

import java.util.Map;

/**
 * 电流控制电压源（CCVS，SPICE 中的 H 器件）。
 *
 * <p>语法：{@code H<名> n+ n- 被控支路名 gain}。输出电压
 * {@code V(n+) − V(n−) = gain·I(被控支路)}。表现为受控电压源，占用一个支路电流未知量。</p>
 */
public final class Ccvs extends Device {

    /** 增益。 */
    private final double gain;
    /** 被控器件名。 */
    private final String controlBranch;
    /** 被控支路电流在解向量中的索引（构建完成后解析）。 */
    private int controlIndex = -1;

    public Ccvs(String name, String nPlus, String nMinus, String controlBranch, double gain) {
        super(name, new String[] {nPlus, nMinus});
        this.controlBranch = controlBranch;
        this.gain = gain;
    }

    /** 被控支路名。 */
    public String controlBranch() {
        return controlBranch;
    }

    /** 依据“器件名 -> 支路电流索引”映射解析被控支路。 */
    public void resolveControl(Map<String, Integer> branchIndexByName) {
        Integer idx = branchIndexByName.get(controlBranch);
        if (idx == null) {
            throw new IllegalArgumentException("undefined current-control branch: " + controlBranch);
        }
        this.controlIndex = idx;
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

        // 节点 KCL：支路电流 i 从 n+ 流入器件、从 n− 流出
        if (p >= 0) {
            sys.add(p, bi, 1.0);
        }
        if (m >= 0) {
            sys.add(m, bi, -1.0);
        }
        // 约束方程：V(n+) − V(n−) − gain·I(被控支路) = 0
        if (p >= 0) {
            sys.add(bi, p, 1.0);
        }
        if (m >= 0) {
            sys.add(bi, m, -1.0);
        }
        sys.add(bi, controlIndex, -gain);
    }

    @Override
    public Map<String, Double> outputs(double[] x, boolean dc, double t) {
        Map<String, Double> out = newOutputs();
        out.put("I(" + name + ")", branchCurrent(x));
        return out;
    }
}
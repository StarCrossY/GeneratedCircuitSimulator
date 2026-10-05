package simcore.circuit;

import java.util.Map;

/**
 * 电感器：直流分析中视为短路（两端节点电位相等、支路电流自由），
 * 瞬态分析中使用梯形积分伴随模型。
 *
 * <p>梯形伴随模型：{@code V(n1) − V(n2) − rL·i = −mem}，其中 {@code rL = 2L/h}、
 * {@code mem = rL·i_prev + v_prev}。它在 MNA 中表现为一个带串联电阻 rL 的“电压源”，
 * 因此占用一个支路电流未知量。</p>
 */
public final class Inductor extends Device {

    private final double inductance;

    /** 梯形等效电阻 2L/h。 */
    private double rL;
    /** 历史项 rL·i_prev + v_prev。 */
    private double mem;
    /** 上一时间步流过电感的电流。 */
    private double iPrev;
    /** 上一时间步电感两端电压。 */
    private double vPrev;

    public Inductor(String name, String n1, String n2, double inductance) {
        super(name, new String[] {n1, n2});
        this.inductance = inductance;
    }

    /** 设置初始条件（直流工作点下的电感电流，电压为 0）。 */
    public void setInitialCondition(double current) {
        this.iPrev = current;
        this.vPrev = 0.0;
    }

    @Override
    public int numExtraVars() {
        return 1;
    }

    @Override
    public void prepareStep(double t, double h) {
        rL = 2.0 * inductance / h;
        mem = rL * iPrev + vPrev;
    }

    @Override
    public void stamp(MnaSystem sys, double[] x, boolean dc, double t, double h) {
        int i = n[0];
        int j = n[1];
        int bi = branchIndex;

        // 两端节点 KCL：流过电感的电流进入 n1、流出 n2
        if (i >= 0) {
            sys.add(i, bi, 1.0);
        }
        if (j >= 0) {
            sys.add(j, bi, -1.0);
        }
        // 支路约束方程：V(n1) − V(n2) − rL·i = −mem（直流时 rL=0、mem=0，即短路）
        if (i >= 0) {
            sys.add(bi, i, 1.0);
        }
        if (j >= 0) {
            sys.add(bi, j, -1.0);
        }
        if (!dc) {
            sys.add(bi, bi, -rL);
            sys.addRhs(bi, -mem);
        }
    }

    /** 返回当前解 x 下流过电感的支路电流。 */
    public double currentThrough(double[] x) {
        return branchCurrent(x);
    }

    /** 求解完成后更新记忆。 */
    public void commitStep(double[] x) {
        iPrev = branchCurrent(x);
        vPrev = voltage(x, n[0]) - voltage(x, n[1]);
    }

    @Override
    public Map<String, Double> outputs(double[] x, boolean dc, double t) {
        Map<String, Double> out = newOutputs();
        out.put("I(" + name + ")", branchCurrent(x));
        return out;
    }
}
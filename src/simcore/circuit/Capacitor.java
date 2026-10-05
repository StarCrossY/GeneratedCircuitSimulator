package simcore.circuit;

import java.util.Map;

/**
 * 电容器：直流分析中视为开路（不写入任何贡献），瞬态分析中使用梯形积分伴随模型。
 *
 * <p>梯形积分伴随模型：{@code i = gc·Vc − hist}，其中 {@code gc = 2C/h}，
 * {@code hist = gc·Vc_prev + i_prev}。该关系在节点间写为电导 gc 与一个历史电流源。</p>
 */
public final class Capacitor extends Device {

    private final double capacitance;

    /** 梯形伴随电导 2C/h。 */
    private double gc;
    /** 历史项 gc·Vc_prev + i_prev。 */
    private double hist;
    /** 上一时间步电容两端电压。 */
    private double vcPrev;
    /** 上一时间步流过电容的电流。 */
    private double iPrev;

    public Capacitor(String name, String n1, String n2, double capacitance) {
        super(name, new String[] {n1, n2});
        this.capacitance = capacitance;
    }

    /** 设置初始条件（直流工作点下的电容电压，电流为 0）。 */
    public void setInitialCondition(double voltage) {
        this.vcPrev = voltage;
        this.iPrev = 0.0;
    }

    @Override
    public void prepareStep(double t, double h) {
        gc = 2.0 * capacitance / h;
        hist = gc * vcPrev + iPrev;
    }

    @Override
    public void stamp(MnaSystem sys, double[] x, boolean dc, double t, double h) {
        if (dc) {
            return; // 直流开路
        }
        int i = n[0];
        int j = n[1];
        stampConductance(sys, i, j, gc);
        // 历史电流源：流入节点 i 的电流为 +hist（流入节点 j 为 -hist）
        if (i >= 0) {
            sys.addRhs(i, hist);
        }
        if (j >= 0) {
            sys.addRhs(j, -hist);
        }
    }

    /** 返回当前解 x 下电容两端电压（n1 相对 n2 的电位差）。 */
    public double voltageAcross(double[] x) {
        return voltage(x, n[0]) - voltage(x, n[1]);
    }

    /** 求解完成后更新记忆（供下一时间步使用）。 */
    public void commitStep(double[] x) {
        double vc = voltage(x, n[0]) - voltage(x, n[1]);
        vcPrev = vc;
        iPrev = gc * vc - hist;
    }

    @Override
    public Map<String, Double> outputs(double[] x, boolean dc, double t) {
        Map<String, Double> out = newOutputs();
        out.put("I(" + name + ")", dc ? 0.0 : iPrev);
        return out;
    }
}
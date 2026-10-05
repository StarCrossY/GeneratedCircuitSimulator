package simcore.circuit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 电路器件（元件）基类。
 *
 * <p>所有器件在展平后拥有已解析的全局节点名 {@link #nodeNames} 与对应的节点索引
 * {@link #n}（地节点用 -1 表示）。器件通过 {@link #stamp} 向 MNA 矩阵写入贡献：</p>
 * <ul>
 *   <li>线性器件（R/C/L/V/I）每次写入恒定的系数（与迭代变量 x 无关）；</li>
 *   <li>非线性器件（BJT）根据当前迭代点 x 做牛顿线性化（写入雅可比矩阵 + 剩余项）。</li>
 * </ul>
 */
public abstract class Device {

    /** 器件名，如 R1、Q1。 */
    public final String name;

    /** 各端所连接的全局节点名（用于结果报告）。 */
    public final String[] nodeNames;

    /** 各端对应的节点索引，地节点为 -1。 */
    protected final int[] n;

    /** 支路电流未知量在解向量中的起始索引（电压源/电感各占一个）。 */
    protected int branchIndex = -1;

    public Device(String name, String[] nodeNames) {
        this.name = name;
        this.nodeNames = nodeNames;
        this.n = new int[nodeNames.length];
        for (int i = 0; i < n.length; i++) {
            n[i] = -1;
        }
    }

    /** 设置某端节点的索引（地节点传 -1）。 */
    public void assignNodeIndex(int terminal, int index) {
        n[terminal] = index;
    }

    /** 设置支路电流未知量索引（由电路构建器分配）。 */
    public void assignBranchIndex(int index) {
        this.branchIndex = index;
    }

    /** 器件占用的额外支路电流未知量个数（电压源、电感为 1，其余为 0）。 */
    public int numExtraVars() {
        return 0;
    }

    /** 是否为非线性器件（BJT 返回 true）。 */
    public boolean nonlinear() {
        return false;
    }

    /**
     * 瞬态分析中每个时间步开始前调用，用于更新动态器件（电容/电感）的伴随模型记忆。
     *
     * @param t 当前时刻 (s)
     * @param h 时间步长 (s)
     */
    public void prepareStep(double t, double h) {
    }

    /**
     * 将器件的贡献写入 MNA 系统。
     *
     * @param sys MNA 系统
     * @param x   当前牛顿迭代的解向量（仅非线性器件使用）
     * @param dc  是否为直流工作点分析
     * @param t   当前时刻 (s)
     * @param h   时间步长 (s)
     */
    public abstract void stamp(MnaSystem sys, double[] x, boolean dc, double t, double h);

    /**
     * 根据解向量计算器件电流等输出量，供结果报告使用。
     *
     * @param x  解向量（节点电压 + 支路电流）
     * @param dc 是否为直流分析
     * @param t  当前时刻 (s)
     * @return 标签到数值的映射（保持插入顺序）
     */
    public abstract Map<String, Double> outputs(double[] x, boolean dc, double t);

    /** 读取节点电压（地节点返回 0）。 */
    protected double voltage(double[] x, int index) {
        return index < 0 ? 0.0 : x[index];
    }

    /** 读取支路电流（无支路未知量时返回 0）。 */
    protected double branchCurrent(double[] x) {
        return branchIndex < 0 ? 0.0 : x[branchIndex];
    }

    /** 新建一个保持插入顺序的结果映射。 */
    protected static Map<String, Double> newOutputs() {
        return new LinkedHashMap<>();
    }

    /**
     * 在节点 i、j 之间写入电导 g 的贡献（地节点自动跳过）。
     * <p>即： A[i][i]+=g, A[i][j]-=g, A[j][i]-=g, A[j][j]+=g。</p>
     */
    protected static void stampConductance(MnaSystem sys, int i, int j, double g) {
        if (i >= 0) {
            sys.add(i, i, g);
            if (j >= 0) {
                sys.add(i, j, -g);
            }
        }
        if (j >= 0) {
            sys.add(j, j, g);
            if (i >= 0) {
                sys.add(j, i, -g);
            }
        }
    }
}
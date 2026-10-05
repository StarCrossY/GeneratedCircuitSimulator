package simcore.analysis;

import simcore.circuit.Capacitor;
import simcore.circuit.Circuit;
import simcore.circuit.Device;
import simcore.circuit.Inductor;
import simcore.circuit.MnaSystem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 电路求解器，提供两种模拟：
 * <ul>
 *   <li>{@link #dc()} 直流工作点分析（与过程无关）：给定电源等具体数值，直接求解各节点
 *       电压与各支路电流；</li>
 *   <li>{@link #tran(double, double)} 瞬态分析（与过程有关）：给定输入信号后，按时间
 *       步进求解全过程及各时刻全部参数。</li>
 * </ul>
 *
 * <p>两类分析均基于 MNA + 牛顿迭代。为保证健壮性，采用 gmin（对地极小电导）与节点电压
 * 步长限制（阻尼）来帮助非线性迭代收敛。</p>
 */
public final class Simulator {

    /** 对地极小电导 (S)，防止矩阵奇异、辅助收敛。 */
    private static final double GMIN = 1e-12;
    /** 牛顿迭代最大次数。 */
    private static final int MAX_ITER = 200;
    /** 收敛的相对容差。 */
    private static final double TOL = 1e-10;
    /** 单次迭代允许的节点电压最大变化量 (V)，用于阻尼。 */
    private static final double V_LIMIT = 1.0;

    private final Circuit circuit;

    public Simulator(Circuit circuit) {
        this.circuit = circuit;
    }

    /** 直流工作点分析。 */
    public DcResult dc() {
        int n = circuit.numVariables();
        double[] x = solveNewton(new double[n], true, 0.0, 0.0);
        return collectDc(x);
    }

    /**
     * 瞬态分析。
     *
     * @param tstep 时间步长 (s)
     * @param tstop 终止时间 (s)
     * @return 各时间点的节点电压与器件电流序列
     */
    public TransientResult tran(double tstep, double tstop) {
        if (tstep <= 0 || tstop <= 0) {
            throw new IllegalArgumentException("transient analysis requires a positive step and stop time");
        }
        int n = circuit.numVariables();

        // 1. 先求 t=0 时刻的直流工作点，作为初始状态
        double[] x = solveNewton(new double[n], true, 0.0, 0.0);
        for (Device d : circuit.devices) {
            if (d instanceof Capacitor c) {
                c.setInitialCondition(c.voltageAcross(x));
            } else if (d instanceof Inductor ind) {
                ind.setInitialCondition(ind.currentThrough(x));
            }
        }

        // 2. 时间步进
        int steps = (int) Math.ceil(tstop / tstep);
        List<Double> timeList = new ArrayList<>(steps + 1);
        Map<String, List<Double>> vLists = new LinkedHashMap<>();
        Map<String, List<Double>> iLists = new LinkedHashMap<>();

        for (int k = 0; k <= steps; k++) {
            double t = k * tstep;
            if (k > 0) {
                // 更新动态器件伴随模型并求解当前时刻
                for (Device d : circuit.devices) {
                    d.prepareStep(t, tstep);
                }
                x = solveNewton(x, false, t, tstep);
                for (Device d : circuit.devices) {
                    if (d instanceof Capacitor c) {
                        c.commitStep(x);
                    } else if (d instanceof Inductor ind) {
                        ind.commitStep(x);
                    }
                }
            }
            record(timeList, vLists, iLists, x, t);
        }

        return collectTransient(timeList, vLists, iLists);
    }

    /** 记录一个时间点的结果。 */
    private void record(List<Double> timeList, Map<String, List<Double>> vLists,
                        Map<String, List<Double>> iLists, double[] x, double t) {
        timeList.add(t);
        for (int i = 0; i < circuit.numNodes(); i++) {
            String node = circuit.nodeNames.get(i);
            vLists.computeIfAbsent(node, k -> new ArrayList<>()).add(x[i]);
        }
        for (Device d : circuit.devices) {
            Map<String, Double> out = d.outputs(x, false, t);
            for (Map.Entry<String, Double> e : out.entrySet()) {
                iLists.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue());
            }
        }
    }

    /** 将列表形式结果转为数组。 */
    private TransientResult collectTransient(List<Double> timeList,
                                             Map<String, List<Double>> vLists,
                                             Map<String, List<Double>> iLists) {
        TransientResult r = new TransientResult();
        r.time = toArray(timeList);
        for (Map.Entry<String, List<Double>> e : vLists.entrySet()) {
            r.nodeVoltages.put(e.getKey(), toArray(e.getValue()));
        }
        for (Map.Entry<String, List<Double>> e : iLists.entrySet()) {
            r.currents.put(e.getKey(), toArray(e.getValue()));
        }
        return r;
    }

    /** 收集直流工作点结果。 */
    private DcResult collectDc(double[] x) {
        DcResult r = new DcResult();
        for (int i = 0; i < circuit.numNodes(); i++) {
            r.nodeVoltages.put(circuit.nodeNames.get(i), x[i]);
        }
        for (Device d : circuit.devices) {
            r.currents.putAll(d.outputs(x, true, 0.0));
        }
        return r;
    }

    /**
     * 牛顿迭代求解非线性 MNA 系统。
     *
     * @param x0 初始猜测（瞬态分析中传上一时刻解，加速收敛）
     * @param dc 是否直流分析
     * @param t  当前时刻 (s)
     * @param h  时间步长 (s)
     * @return 收敛后的解向量
     */
    private double[] solveNewton(double[] x0, boolean dc, double t, double h) {
        int n = circuit.numVariables();
        double[] x = x0.clone();

        for (int iter = 0; iter < MAX_ITER; iter++) {
            MnaSystem sys = new MnaSystem(n);
            for (Device d : circuit.devices) {
                d.stamp(sys, x, dc, t, h);
            }
            // gmin：对每个节点加对地极小电导
            for (int i = 0; i < circuit.numNodes(); i++) {
                sys.add(i, i, GMIN);
            }

            double[] xNew = sys.solve();

            // 阻尼：限制节点电压相对上次的最大变化量
            double lambda = 1.0;
            double maxDv = 0.0;
            for (int i = 0; i < circuit.numNodes(); i++) {
                double dv = Math.abs(xNew[i] - x[i]);
                if (dv > maxDv) {
                    maxDv = dv;
                }
            }
            if (maxDv > V_LIMIT) {
                lambda = V_LIMIT / maxDv;
            }

            double maxRel = 0.0;
            for (int i = 0; i < n; i++) {
                double step = lambda * (xNew[i] - x[i]);
                x[i] += step;
                double rel = Math.abs(step) / Math.max(1.0, Math.abs(x[i]));
                if (rel > maxRel) {
                    maxRel = rel;
                }
            }

            if (maxRel < TOL) {
                return x;
            }
        }
        throw new IllegalStateException("Newton iteration did not converge after " + MAX_ITER + " iterations");
    }

    private static double[] toArray(List<Double> list) {
        double[] a = new double[list.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = list.get(i);
        }
        return a;
    }
}
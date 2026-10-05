package simcore.circuit;

import java.util.Map;

/**
 * 电流控制电流源（CCCS，SPICE 中的 F 器件）。
 *
 * <p>语法：{@code F<名> n+ n- 被控支路名 gain}。输出电流
 * {@code i = gain·I(被控支路)}，由 n+ 端流出、从 n− 端流回。被控支路为某个带支路电流的
 * 器件（独立电压源、电感或受控电压源），其支路电流即 MNA 解向量中的对应未知量。</p>
 */
public final class Cccs extends Device {

    /** 增益。 */
    private final double gain;
    /** 被控器件名。 */
    private final String controlBranch;
    /** 被控支路电流在解向量中的索引（构建完成后解析）。 */
    private int controlIndex = -1;

    public Cccs(String name, String nPlus, String nMinus, String controlBranch, double gain) {
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
    public void stamp(MnaSystem sys, double[] x, boolean dc, double t, double h) {
        // 输出电流 i = gain·I(被控支路) 由 n+ 流出、注入 n−（与独立电流源约定一致）
        if (n[0] >= 0) {
            sys.add(n[0], controlIndex, -gain);
        }
        if (n[1] >= 0) {
            sys.add(n[1], controlIndex, gain);
        }
    }

    @Override
    public Map<String, Double> outputs(double[] x, boolean dc, double t) {
        double i = controlIndex >= 0 ? gain * x[controlIndex] : 0.0;
        Map<String, Double> out = newOutputs();
        out.put("I(" + name + ")", i);
        return out;
    }
}
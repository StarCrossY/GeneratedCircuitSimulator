package simcore.circuit;

import java.util.Map;

/**
 * 电压控制电流源（VCCS，SPICE 中的 G 器件）。
 *
 * <p>语法：{@code G<名> n+ n- nc+ nc- gm}。输出电流 {@code i = gm·(V(nc+) − V(nc-))}，
 * 约定该电流由 n+ 端流出、注入外部电路，并从 n− 端流回（与独立电流源 {@link CurrentSource}
 * 的符号约定一致）。该器件为线性元件，故直接写入恒定系数。</p>
 */
public final class Vccs extends Device {

    /** 跨导 gm (S)。 */
    private final double gm;

    /** 端顺序：0=n+（输出正端），1=n−（输出负端），2=nc+（控制正端），3=nc−（控制负端）。 */
    public Vccs(String name, String nPlus, String nMinus, String cPlus, String cMinus, double gm) {
        super(name, new String[] {nPlus, nMinus, cPlus, cMinus});
        this.gm = gm;
    }

    @Override
    public void stamp(MnaSystem sys, double[] x, boolean dc, double t, double h) {
        int nP = n[0];
        int nM = n[1];
        int cP = n[2];
        int cM = n[3];

        // 输出电流 i = gm·(V(cP) − V(cM)) 由 n+ 流出；在“流出节点”观下，
        // 节点 n+ 行写入 −i，节点 n− 行写入 +i。
        if (nP >= 0) {
            if (cP >= 0) {
                sys.add(nP, cP, -gm);
            }
            if (cM >= 0) {
                sys.add(nP, cM, gm);
            }
        }
        if (nM >= 0) {
            if (cP >= 0) {
                sys.add(nM, cP, gm);
            }
            if (cM >= 0) {
                sys.add(nM, cM, -gm);
            }
        }
    }

    @Override
    public Map<String, Double> outputs(double[] x, boolean dc, double t) {
        double i = gm * (voltage(x, n[2]) - voltage(x, n[3]));
        Map<String, Double> out = newOutputs();
        out.put("I(" + name + ")", i);
        return out;
    }
}
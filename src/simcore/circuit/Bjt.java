package simcore.circuit;

import simcore.netlist.Netlist;

import java.util.Map;

/**
 * 双极结型三极管（BJT），采用简化 Ebers-Moll 传输（注入）模型。
 *
 * <p>端顺序约定为 集电极 (c)、基极 (b)、发射极 (e)，端电流以“流入器件”为正方向。
 * 模型方程（以 NPN 为准，PNP 通过极性符号 p=±1 复用同一套公式）：</p>
 * <pre>
 *   I_C' = IS·(e^{Vbe'/Vt} − e^{Vbc'/Vt}) − (IS/BR)·(e^{Vbc'/Vt} − 1)
 *   I_B' = (IS/BF)·(e^{Vbe'/Vt} − 1) + (IS/BR)·(e^{Vbc'/Vt} − 1)
 *   I_E' = −(I_C' + I_B')
 * </pre>
 * <p>其中 Vbe' = p·Vbe、Vbc' = p·Vbc，端电流 I = p·I'。牛顿迭代利用上述方程对三个端
 * 节点电压的解析雅可比矩阵做线性化写入 MNA 系统。</p>
 */
public final class Bjt extends Device {

    private final Netlist.Model model;
    /** 极性：NPN=+1，PNP=−1。 */
    private final double p;

    public Bjt(String name, String collector, String base, String emitter, Netlist.Model model) {
        super(name, new String[] {collector, base, emitter});
        this.model = model;
        this.p = model.isNpn() ? 1.0 : -1.0;
    }

    @Override
    public boolean nonlinear() {
        return true;
    }

    @Override
    public void stamp(MnaSystem sys, double[] x, boolean dc, double t, double h) {
        double vt = model.vt;
        double is = model.is;
        double bf = model.bf;
        double br = model.br;

        // 节点电压（地节点为 0）
        double vc = voltage(x, n[0]);
        double vb = voltage(x, n[1]);
        double ve = voltage(x, n[2]);

        double vbePrime = p * (vb - ve) / vt;
        double vbcPrime = p * (vb - vc) / vt;

        // 限制指数参数，避免 exp 溢出
        double ebe = Math.exp(clampExp(vbePrime));
        double ebc = Math.exp(clampExp(vbcPrime));

        // 端电流（NPN 参考方向，含极性符号）
        double ic = is * (ebe - ebc) - (is / br) * (ebc - 1.0);
        double ib = (is / bf) * (ebe - 1.0) + (is / br) * (ebc - 1.0);
        double ie = -(ic + ib);

        double tc = p * ic; // 流入集电极的电流
        double tb = p * ib; // 流入基极的电流
        double te = p * ie; // 流入发射极的电流
        double[] term = {tc, tb, te};

        // 正向/反向跨导
        double aF = (is / vt) * ebe;
        double aR = (is / vt) * ebc;

        // 雅可比矩阵（对 [Vc, Vb, Ve]），PNP/NPN 相同（极性在求导中相消）
        double[][] jac = new double[3][3];
        // 行集电极 ic
        jac[0][0] = aR * (1.0 + 1.0 / br);
        jac[0][1] = aF - aR * (1.0 + 1.0 / br);
        jac[0][2] = -aF;
        // 行基极 ib
        jac[1][0] = -aR / br;
        jac[1][1] = aF / bf + aR / br;
        jac[1][2] = -aF / bf;
        // 行发射极 ie
        jac[2][0] = -aR;
        jac[2][1] = -aF * (1.0 + 1.0 / bf) + aR;
        jac[2][2] = aF * (1.0 + 1.0 / bf);

        // 写入牛顿线性化：A += J，b += J·x − t
        for (int r = 0; r < 3; r++) {
            int row = n[r];
            if (row < 0) {
                continue; // 地节点无方程
            }
            double offset = -term[r];
            for (int c = 0; c < 3; c++) {
                int col = n[c];
                if (col < 0) {
                    continue; // 地节点电压恒为 0，无未知量列
                }
                sys.add(row, col, jac[r][c]);
                offset += jac[r][c] * voltage(x, col);
            }
            sys.addRhs(row, offset);
        }
    }

    @Override
    public Map<String, Double> outputs(double[] x, boolean dc, double t) {
        double vt = model.vt;
        double vc = voltage(x, n[0]);
        double vb = voltage(x, n[1]);
        double ve = voltage(x, n[2]);

        double vbePrime = p * (vb - ve) / vt;
        double vbcPrime = p * (vb - vc) / vt;
        double ebe = Math.exp(clampExp(vbePrime));
        double ebc = Math.exp(clampExp(vbcPrime));

        double ic = p * (model.is * (ebe - ebc) - (model.is / model.br) * (ebc - 1.0));
        double ib = p * ((model.is / model.bf) * (ebe - 1.0) + (model.is / model.br) * (ebc - 1.0));
        double ie = -(ic + ib);

        Map<String, Double> out = newOutputs();
        out.put("Ic(" + name + ")", ic);
        out.put("Ib(" + name + ")", ib);
        out.put("Ie(" + name + ")", ie);
        return out;
    }

    /** 限制指数参数到 [-80, 80]，防止溢出。 */
    private static double clampExp(double v) {
        return Math.max(-80.0, Math.min(80.0, v));
    }
}
package simcore.circuit;

import java.util.Map;

/**
 * 独立电流源：约定 {@code I name n+ n- val} 表示大小为 val 的电流从 n+ 端流出、
 * 注入电路，并从 n− 端流回。即向节点 n+ 注入 +val、向节点 n− 注入 −val。
 */
public final class CurrentSource extends Device {

    private final Waveform waveform;

    public CurrentSource(String name, String nPlus, String nMinus, Waveform waveform) {
        super(name, new String[] {nPlus, nMinus});
        this.waveform = waveform;
    }

    @Override
    public void stamp(MnaSystem sys, double[] x, boolean dc, double t, double h) {
        double is = waveform.valueAt(dc ? 0.0 : t);
        if (n[0] >= 0) {
            sys.addRhs(n[0], is);
        }
        if (n[1] >= 0) {
            sys.addRhs(n[1], -is);
        }
    }

    @Override
    public Map<String, Double> outputs(double[] x, boolean dc, double t) {
        Map<String, Double> out = newOutputs();
        out.put("I(" + name + ")", waveform.valueAt(dc ? 0.0 : t));
        return out;
    }
}
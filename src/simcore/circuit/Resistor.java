package simcore.circuit;

import java.util.Map;

/**
 * 电阻器：两端线性电导器件。
 * <p>直流与瞬态贡献相同：节点间电导 g = 1/R。</p>
 */
public final class Resistor extends Device {

    private final double resistance;

    public Resistor(String name, String n1, String n2, double resistance) {
        super(name, new String[] {n1, n2});
        this.resistance = resistance;
    }

    @Override
    public void stamp(MnaSystem sys, double[] x, boolean dc, double t, double h) {
        stampConductance(sys, n[0], n[1], 1.0 / resistance);
    }

    @Override
    public Map<String, Double> outputs(double[] x, boolean dc, double t) {
        Map<String, Double> out = newOutputs();
        double current = (voltage(x, n[0]) - voltage(x, n[1])) / resistance;
        out.put("I(" + name + ")", current);
        return out;
    }
}
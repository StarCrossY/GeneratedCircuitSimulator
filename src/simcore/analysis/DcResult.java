package simcore.analysis;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 直流工作点（.op）分析结果。
 */
public final class DcResult {

    /** 节点电压：节点名 -> 电压值 (V)。 */
    public final Map<String, Double> nodeVoltages = new LinkedHashMap<>();

    /** 器件电流：标签（如 I(R1)、Ic(Q1)）-> 电流值 (A)。 */
    public final Map<String, Double> currents = new LinkedHashMap<>();
}
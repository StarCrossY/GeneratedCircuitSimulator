package simcore.analysis;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 瞬态（.tran）分析结果：各时间点全部节点电压与器件电流的序列。
 */
public final class TransientResult {

    /** 时间序列 (s)。 */
    public double[] time;

    /** 节点电压序列：节点名 -> 电压数组 (V)，与 time 同长度。 */
    public final Map<String, double[]> nodeVoltages = new LinkedHashMap<>();

    /** 器件电流序列：标签 -> 电流数组 (A)，与 time 同长度。 */
    public final Map<String, double[]> currents = new LinkedHashMap<>();
}
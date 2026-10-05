package simcore.circuit;

import simcore.Units;
import simcore.netlist.Netlist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 展平后的电路：包含器件列表与全局节点索引。
 *
 * <p>构建过程：</p>
 * <ol>
 *   <li>递归展平子电路实例（X），将局部节点名重命名为全局唯一名；</li>
 *   <li>为每个非地节点分配索引（地节点统一为 -1），地节点名接受 {@code 0} 与 {@code gnd}；</li>
 *   <li>为电压源/电感分配支路电流未知量索引（紧接在节点电压之后）。</li>
 * </ol>
 */
public final class Circuit {

    /** 展平后的器件列表。 */
    public final List<Device> devices = new ArrayList<>();

    /** 非地节点名列表（下标即解向量中的节点电压索引）。 */
    public final List<String> nodeNames = new ArrayList<>();

    /** 节点名到索引的映射（不含地节点）。 */
    private final Map<String, Integer> nodeIndex = new LinkedHashMap<>();

    private Circuit() {
    }

    /** 从网表构件展平电路。 */
    public static Circuit build(Netlist netlist) {
        Circuit circuit = new Circuit();

        // 1. 展平
        List<Netlist.Instance> flat = new ArrayList<>();
        Map<String, String> top = new HashMap<>();
        for (Netlist.Instance inst : netlist.instances) {
            expand(inst, netlist, "", top, flat);
        }

        // 2. 依据展平结果创建器件
        for (Netlist.Instance inst : flat) {
            circuit.devices.add(createDevice(inst, netlist));
        }

        // 3. 分配节点索引
        for (Device d : circuit.devices) {
            for (int t = 0; t < d.nodeNames.length; t++) {
                String node = d.nodeNames[t];
                int idx;
                if (isGround(node)) {
                    idx = -1;
                } else {
                    idx = circuit.nodeIndex.computeIfAbsent(node, k -> {
                        circuit.nodeNames.add(k);
                        return circuit.nodeNames.size() - 1;
                    });
                }
                d.assignNodeIndex(t, idx);
            }
        }

        // 4. 分配支路电流索引
        int branch = circuit.nodeNames.size();
        for (Device d : circuit.devices) {
            if (d.numExtraVars() > 0) {
                d.assignBranchIndex(branch);
                branch += d.numExtraVars();
            }
        }

        // 5. 解析电流控制源（F/H）的被控支路电流索引
        Map<String, Integer> branchByName = new HashMap<>();
        for (Device d : circuit.devices) {
            if (d.numExtraVars() > 0) {
                branchByName.put(d.name, d.branchIndex);
            }
        }
        for (Device d : circuit.devices) {
            if (d instanceof Cccs cccs) {
                cccs.resolveControl(branchByName);
            } else if (d instanceof Ccvs ccvs) {
                ccvs.resolveControl(branchByName);
            }
        }

        return circuit;
    }

    /** 非地节点数（即节点电压未知量个数）。 */
    public int numNodes() {
        return nodeNames.size();
    }

    /** 附加支路电流未知量总数（电压源 + 电感）。 */
    public int numBranchVars() {
        int sum = 0;
        for (Device d : devices) {
            sum += d.numExtraVars();
        }
        return sum;
    }

    /** 解向量总长度。 */
    public int numVariables() {
        return numNodes() + numBranchVars();
    }

    /** 判断节点名是否为地。 */
    public static boolean isGround(String node) {
        return node.equals("0") || node.equalsIgnoreCase("gnd");
    }

    /**
     * 递归展平单个实例。
     *
     * @param inst    待展平的实例
     * @param netlist 网表（用于查找子电路定义与模型）
     * @param prefix  内部节点名前缀（由实例路径构成）
     * @param rename  当前作用域的节点重命名表（端口名 -> 已解析的父级节点名）
     * @param out     展平结果收集器
     */
    private static void expand(Netlist.Instance inst, Netlist netlist, String prefix,
                               Map<String, String> rename, List<Netlist.Instance> out) {
        if ("X".equals(inst.type)) {
            Netlist.Subckt sc = netlist.subckts.get(inst.value);
            if (sc == null) {
                throw new IllegalArgumentException("undefined subcircuit: " + inst.value);
            }
            if (sc.ports.size() != inst.nodes.size()) {
                throw new IllegalArgumentException("subcircuit " + inst.value
                        + " port count mismatch: " + sc.ports.size()
                        + " ports declared, " + inst.nodes.size() + " pins provided");
            }
            // 端口映射：子电路端口 -> 父作用域解析后的节点
            Map<String, String> child = new HashMap<>();
            for (int k = 0; k < sc.ports.size(); k++) {
                String pin = inst.nodes.get(k);
                child.put(sc.ports.get(k), rename.getOrDefault(pin, pin));
            }
            String childPrefix = prefix.isEmpty() ? inst.name : prefix + "/" + inst.name;
            for (Netlist.Instance bodyInst : sc.body) {
                expand(bodyInst, netlist, childPrefix, child, out);
            }
            return;
        }

        // 普通器件：解析节点名（端口名走映射，内部节点加前缀）
        List<String> resolved = new ArrayList<>();
        for (String node : inst.nodes) {
            if (rename.containsKey(node)) {
                resolved.add(rename.get(node));
            } else {
                resolved.add(prefix.isEmpty() ? node : prefix + "/" + node);
            }
        }
        String newName = prefix.isEmpty() ? inst.name : prefix + "/" + inst.name;
        String newValue = "F".equals(inst.type) || "H".equals(inst.type)
                ? renameControlRef(inst.value, prefix) : inst.value;
        out.add(new Netlist.Instance(inst, newName, resolved, newValue));
    }

    /** 根据展平实例创建具体器件对象。 */
    private static Device createDevice(Netlist.Instance inst, Netlist netlist) {
        switch (inst.type) {
            case "R" -> {
                return new Resistor(inst.name, inst.nodes.get(0), inst.nodes.get(1),
                        Units.parse(inst.value));
            }
            case "C" -> {
                return new Capacitor(inst.name, inst.nodes.get(0), inst.nodes.get(1),
                        Units.parse(inst.value));
            }
            case "L" -> {
                return new Inductor(inst.name, inst.nodes.get(0), inst.nodes.get(1),
                        Units.parse(inst.value));
            }
            case "V" -> {
                return new VoltageSource(inst.name, inst.nodes.get(0), inst.nodes.get(1),
                        Waveform.parse(inst.value));
            }
            case "I" -> {
                return new CurrentSource(inst.name, inst.nodes.get(0), inst.nodes.get(1),
                        Waveform.parse(inst.value));
            }
            case "G" -> {
                return new Vccs(inst.name, inst.nodes.get(0), inst.nodes.get(1),
                        inst.nodes.get(2), inst.nodes.get(3), Units.parse(inst.value));
            }
            case "E" -> {
                return new Vcvs(inst.name, inst.nodes.get(0), inst.nodes.get(1),
                        inst.nodes.get(2), inst.nodes.get(3), Units.parse(inst.value));
            }
            case "F" -> {
                return new Cccs(inst.name, inst.nodes.get(0), inst.nodes.get(1),
                        controlRefName(inst.value), Units.parse(controlRefValue(inst.value)));
            }
            case "H" -> {
                return new Ccvs(inst.name, inst.nodes.get(0), inst.nodes.get(1),
                        controlRefName(inst.value), Units.parse(controlRefValue(inst.value)));
            }
            case "Q" -> {
                Netlist.Model model = netlist.models.get(inst.value);
                if (model == null) {
                    throw new IllegalArgumentException("undefined model: " + inst.value);
                }
                return new Bjt(inst.name, inst.nodes.get(0), inst.nodes.get(1),
                        inst.nodes.get(2), model);
            }
            default -> throw new IllegalArgumentException("unknown device type: " + inst.type);
        }
    }

    /** 对电流控制源（F/H）取值 "&lt;refname&gt; &lt;gain&gt;" 中的 refname 加前缀（增益部分保留）。 */
    private static String renameControlRef(String value, String prefix) {
        if (prefix.isEmpty()) {
            return value;
        }
        int sp = value.indexOf(' ');
        if (sp < 0) {
            return prefix + "/" + value;
        }
        return prefix + "/" + value.substring(0, sp) + value.substring(sp);
    }

    /** 提取电流控制源取值中的被控支路名（首个 token）。 */
    private static String controlRefName(String value) {
        int sp = value.indexOf(' ');
        return sp < 0 ? value : value.substring(0, sp);
    }

    /** 提取电流控制源取值中的增益部分。 */
    private static String controlRefValue(String value) {
        int sp = value.indexOf(' ');
        return sp < 0 ? "" : value.substring(sp + 1).trim();
    }
}
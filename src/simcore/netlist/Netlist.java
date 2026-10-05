package simcore.netlist;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析后的网表数据模型，仅存放结构化数据，不包含求解逻辑。
 *
 * <p>包含标题、器件模型 ({@link Model})、子电路定义 ({@link Subckt})、
 * 顶层器件实例 ({@link Instance}) 与待执行的分析指令 ({@link Analysis})。</p>
 */
public final class Netlist {

    /** 网表标题（来自 .title 行，可选）。 */
    public String title = "";

    /** 器件模型，键为模型名。 */
    public final Map<String, Model> models = new LinkedHashMap<>();

    /** 子电路定义，键为子电路名。 */
    public final Map<String, Subckt> subckts = new LinkedHashMap<>();

    /** 顶层器件实例（含子电路实例 X）。 */
    public final List<Instance> instances = new ArrayList<>();

    /** 分析指令（.op / .tran）。 */
    public final List<Analysis> analyses = new ArrayList<>();

    /**
     * 半导体器件模型参数（此处为简化 Ebers-Moll 三极管模型）。
     *
     * <p>字段含义：IS 饱和电流、BF 正向电流增益、BR 反向电流增益、VT 热电压。
     * 除 IS/BF/BR 外，VT 可由温度换算，默认取 300K 下的约 25.85mV。</p>
     */
    public static final class Model {
        /** 模型名。 */
        public final String name;
        /** 器件类型：npn 或 pnp。 */
        public final String type;
        /** 饱和电流 IS (A)，默认 1e-15。 */
        public double is = 1e-15;
        /** 正向电流增益 BF，默认 100。 */
        public double bf = 100;
        /** 反向电流增益 BR，默认 1。 */
        public double br = 1;
        /** 热电压 VT (V)，默认 0.025852。 */
        public double vt = 0.025852;

        /**
         * 构造模型并设置器件类型。
         *
         * @param name 模型名
         * @param type 器件类型字符串
         */
        public Model(String name, String type) {
            this.name = name;
            this.type = type.toLowerCase();
        }

        /**
         * 解析模型参数（如 bf=150 is=2e-15）。
         * <p>未知参数会被静默忽略，便于扩展。</p>
         *
         * @param params 参数键值对
         */
        public void apply(Map<String, String> params) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                String key = e.getKey();
                String val = e.getValue();
                try {
                    switch (key) {
                        case "is" -> is = simcore.Units.parse(val);
                        case "bf" -> bf = simcore.Units.parse(val);
                        case "br" -> br = simcore.Units.parse(val);
                        case "vt" -> vt = simcore.Units.parse(val);
                        default -> {
                            // 未知参数：忽略
                        }
                    }
                } catch (NumberFormatException ignore) {
                    // 参数解析失败时保留默认值
                }
            }
        }

        /** 是否 NPN 型。 */
        public boolean isNpn() {
            return "npn".equals(type);
        }
    }

    /**
     * 子电路定义：名称、端口列表与内部器件行。
     *
     * <p>内部器件节点名是相对该子电路的局部名，需在展平时映射到全局名。</p>
     */
    public static final class Subckt {
        /** 子电路名。 */
        public final String name;
        /** 端口（引脚）名，顺序与实例化时引脚顺序一一对应。 */
        public final List<String> ports = new ArrayList<>();
        /** 子电路内部器件实例。 */
        public final List<Instance> body = new ArrayList<>();

        public Subckt(String name) {
            this.name = name;
        }
    }

    /**
     * 单条器件实例（R/C/L/V/I/Q/X）。
     *
     * <p>各类型字段说明：</p>
     * <ul>
     *   <li>R/C/L：{@code nodes} 两个节点，{@code value} 为数值字符串（含单位后缀）。</li>
     *   <li>V/I：{@code nodes} 两个节点（正/负），{@code value} 为直流数值或波形串如
     *       {@code sin(0 0.01 1k)}、{@code pulse(0 5 0 1n 1n 0.5m 1m)}。</li>
     *   <li>Q：{@code nodes} 为 集电极/基极/发射极 三节点，{@code value} 为模型名。</li>
     *   <li>X：{@code nodes} 为与子电路端口一一对应的引脚，{@code value} 为子电路名。</li>
     * </ul>
     */
    public static final class Instance {
        /** 器件实例名（如 R1、Q1、X1）。 */
        public final String name;
        /** 器件类型首字母（R/C/L/V/I/Q/X）。 */
        public final String type;
        /** 连接的节点名列表。 */
        public final List<String> nodes = new ArrayList<>();
        /** 取值：R/C/L 为数值、V/I 为波形、Q 为模型名、X 为子电路名。 */
        public final String value;

        public Instance(String name, String type, List<String> nodes, String value) {
            this.name = name;
            this.type = type;
            this.nodes.addAll(nodes);
            this.value = value;
        }

        /** 拷贝构造并替换节点名与取值（用于子电路展平时的重命名）。 */
        public Instance(Instance other, String newName, List<String> renamedNodes, String value) {
            this.name = newName;
            this.type = other.type;
            this.nodes.addAll(renamedNodes);
            this.value = value;
        }
    }

    /**
     * 分析指令。
     */
    public static final class Analysis {
        /** 分析类型。 */
        public enum Kind { OP, TRAN }

        /** 分析类型。 */
        public final Kind kind;
        /** 瞬态分析的时间步长 (s)，仅 TRAN 有效。 */
        public final double tstep;
        /** 瞬态分析的终止时间 (s)，仅 TRAN 有效。 */
        public final double tstop;

        private Analysis(Kind kind, double tstep, double tstop) {
            this.kind = kind;
            this.tstep = tstep;
            this.tstop = tstop;
        }

        /** 构造直流工作点分析 {@code .op}。 */
        public static Analysis op() {
            return new Analysis(Kind.OP, 0, 0);
        }

        /** 构造瞬态分析 {@code .tran tstep tstop}。 */
        public static Analysis tran(double tstep, double tstop) {
            return new Analysis(Kind.TRAN, tstep, tstop);
        }
    }
}
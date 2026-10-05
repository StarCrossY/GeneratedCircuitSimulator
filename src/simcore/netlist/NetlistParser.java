package simcore.netlist;

import simcore.Units;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网表文本解析器，将自定义 SPICE 风格网表解析为 {@link Netlist}。
 *
 * <p>支持的语法（子集）：</p>
 * <ul>
 *   <li>{@code .title 标题}</li>
 *   <li>{@code R/C/L 名称 n1 n2 数值}</li>
 *   <li>{@code V/I 名称 n+ n- 数值|sin(...)|pulse(...)}</li>
 *   <li>{@code E/G 名称 n+ n- nc+ nc- 增益}（E 压控电压源 VCVS / G 压控电流源 VCCS）</li>
 *   <li>{@code F/H 名称 n+ n- 被控支路名 增益}（F 电流控电流源 CCCS / H 电流控电压源 CCVS）</li>
 *   <li>{@code Q 名称 集电极 基极 发射极 模型名}</li>
 *   <li>{@code X 名称 引脚... 子电路名}（子电路实例）</li>
 *   <li>{@code .model 名 npn|pnp(key=value ...)}</li>
 *   <li>{@code .subckt 名 端口...} ... {@code .ends}</li>
 *   <li>{@code .op}、{@code .tran tstep tstop}、{@code .end}</li>
 * </ul>
 *
 * <p>注释：以 {@code *} 开头的整行、以及行内 {@code ;} 之后的内容均视为注释。</p>
 */
public final class NetlistParser {

    /** 当前正在解析的子电路（.subckt 内部），栈顶为当前作用域；null 表示顶层。 */
    private Netlist.Subckt currentSubckt = null;

    /** 解析网表文本。 */
    public Netlist parse(String text) {
        Netlist netlist = new Netlist();
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");

        for (String raw : lines) {
            String line = stripComment(raw).trim();
            if (line.isEmpty()) {
                continue;
            }
            parseLine(netlist, line);
        }
        return netlist;
    }

    /** 去除行首 '*' 注释与行内 ';' 注释。 */
    private String stripComment(String line) {
        if (line.startsWith("*")) {
            return "";
        }
        int semi = line.indexOf(';');
        if (semi >= 0) {
            line = line.substring(0, semi);
        }
        return line;
    }

    /** 解析单行指令。 */
    private void parseLine(Netlist netlist, String line) {
        String[] tokens = line.split("\\s+");
        if (tokens.length == 0) {
            return;
        }
        String first = tokens[0].toLowerCase();

        switch (first) {
            case ".title" -> {
                if (!currentSubcktDefined() && tokens.length > 1) {
                    netlist.title = joinFrom(tokens, 1);
                }
            }
            case ".model" -> parseModel(netlist, tokens);
            case ".subckt" -> parseSubckt(netlist, tokens);
            case ".ends" -> currentSubckt = null;
            case ".op" -> netlist.analyses.add(Netlist.Analysis.op());
            case ".tran" -> parseTran(netlist, tokens);
            case ".end" -> {
                // 结束，忽略后续
            }
            default -> {
                if (first.startsWith(".")) {
                    // 其它未支持的指令（.print/.plot/.option 等）：忽略
                    return;
                }
                parseElement(netlist, line, tokens);
            }
        }
    }

    private boolean currentSubcktDefined() {
        return currentSubckt != null;
    }

    /** 解析 .model 名 类型(参数...)。类型与参数可能相连（如 npn(bf=...)）。 */
    private void parseModel(Netlist netlist, String[] tokens) {
        if (tokens.length < 3) {
            return;
        }
        String name = tokens[1];
        String rest = joinFrom(tokens, 2).trim();

        String type;
        String paramsPart = "";
        int paren = rest.indexOf('(');
        if (paren >= 0) {
            // 类型与参数以 '(' 分界，例如 "npn(bf=150 ...)" 或 "npn (bf=150 ...)"
            type = rest.substring(0, paren).trim().toLowerCase();
            paramsPart = rest.substring(paren);
        } else {
            // 无括号形式：首个 token 为类型，其余为参数，例如 "npn bf=150 is=2e-15"
            String[] parts = rest.split("\\s+");
            type = parts[0].toLowerCase();
            if (parts.length > 1) {
                paramsPart = joinFrom(parts, 1);
            }
        }

        Netlist.Model model = new Netlist.Model(name, type);
        model.apply(extractParams(paramsPart));
        netlist.models.put(name, model);
    }

    /** 解析 .subckt 名 端口...。 */
    private void parseSubckt(Netlist netlist, String[] tokens) {
        if (tokens.length < 2) {
            return;
        }
        Netlist.Subckt subckt = new Netlist.Subckt(tokens[1]);
        for (int i = 2; i < tokens.length; i++) {
            subckt.ports.add(tokens[i]);
        }
        netlist.subckts.put(subckt.name, subckt);
        currentSubckt = subckt;
    }

    /** 解析 .tran tstep tstop。 */
    private void parseTran(Netlist netlist, String[] tokens) {
        if (tokens.length < 3) {
            return;
        }
        try {
            double tstep = Units.parse(tokens[1]);
            double tstop = Units.parse(tokens[2]);
            netlist.analyses.add(Netlist.Analysis.tran(tstep, tstop));
        } catch (NumberFormatException ignore) {
            // 解析失败时忽略该指令
        }
    }

    /** 解析器件实例行。name 首字母决定类型。 */
    private void parseElement(Netlist netlist, String line, String[] tokens) {
        if (tokens.length < 3) {
            return;
        }
        String name = tokens[0];
        char typeChar = Character.toUpperCase(name.charAt(0));
        String type = String.valueOf(typeChar);

        // 收集节点与取值
        List<String> nodes = new ArrayList<>();
        String value;

        switch (type) {
            case "R", "C", "L" -> {
                // 两个节点 + 数值
                if (tokens.length < 4) {
                    return;
                }
                nodes.add(tokens[1]);
                nodes.add(tokens[2]);
                value = tokens[3];
            }
            case "V", "I" -> {
                // 两个节点 + 直流数值或波形串（波形串可能含空格，需拼接）
                if (tokens.length < 4) {
                    return;
                }
                nodes.add(tokens[1]);
                nodes.add(tokens[2]);
                value = joinFrom(tokens, 3);
            }
            case "E", "G" -> {
                // 四个节点（n+ n- nc+ nc-）+ 增益值（E 电压控电压 / G 电压控电流）
                if (tokens.length < 6) {
                    return;
                }
                nodes.add(tokens[1]);
                nodes.add(tokens[2]);
                nodes.add(tokens[3]);
                nodes.add(tokens[4]);
                value = tokens[5];
            }
            case "F", "H" -> {
                // 两个节点 + 被控支路名 + 增益（F 电流控电流 / H 电流控电压）
                if (tokens.length < 5) {
                    return;
                }
                nodes.add(tokens[1]);
                nodes.add(tokens[2]);
                value = joinFrom(tokens, 3);
            }
            case "Q" -> {
                // 三节点（c b e）+ 模型名
                if (tokens.length < 5) {
                    return;
                }
                nodes.add(tokens[1]);
                nodes.add(tokens[2]);
                nodes.add(tokens[3]);
                value = tokens[4];
            }
            case "X" -> {
                // 引脚... + 子电路名（最后一个 token 为子电路名）
                if (tokens.length < 3) {
                    return;
                }
                for (int i = 1; i < tokens.length - 1; i++) {
                    nodes.add(tokens[i]);
                }
                value = tokens[tokens.length - 1];
            }
            default -> {
                // 未知器件类型：忽略
                return;
            }
        }

        Netlist.Instance inst = new Netlist.Instance(name, type, nodes, value);
        if (currentSubckt != null) {
            currentSubckt.body.add(inst);
        } else {
            netlist.instances.add(inst);
        }
    }

    /** 从 token 数组的 start 位置起拼接为空格分隔字符串。 */
    private static String joinFrom(String[] tokens, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < tokens.length; i++) {
            if (i > start) {
                sb.append(' ');
            }
            sb.append(tokens[i]);
        }
        return sb.toString();
    }

    /**
     * 从形如 "bf=150 br=5 is=2e-15" 的字符串中提取参数键值对。
     * 支持等号两侧出现空格。
     */
    private static Map<String, String> extractParams(String text) {
        Map<String, String> params = new LinkedHashMap<>();
        // 去掉包裹的括号
        String s = text.trim();
        while (s.startsWith("(")) {
            s = s.substring(1).trim();
        }
        while (s.endsWith(")")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        // 按空白切分，每个片段形如 key=value
        for (String part : s.split("\\s+")) {
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq <= 0 || eq == part.length() - 1) {
                continue;
            }
            String key = part.substring(0, eq).trim().toLowerCase();
            String val = part.substring(eq + 1).trim();
            params.put(key, val);
        }
        return params;
    }
}
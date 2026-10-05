package simapp;

import simcore.analysis.DcResult;
import simcore.analysis.Simulator;
import simcore.analysis.TransientResult;
import simcore.circuit.Circuit;
import simcore.netlist.Netlist;
import simcore.netlist.NetlistParser;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 电路模拟器图形界面。
 *
 * <p>这是“可视化结果的外部调用层”：负责编辑网表、调用核心库 {@link Simulator}
 * 执行两种模拟，并将结果以表格/波形形式展示。核心库 {@code simcore} 与此层完全解耦。</p>
 */
public final class SimulatorFrame extends JFrame {

    /** 网表编辑器。 */
    private final JTextArea netlistArea = new JTextArea(30, 36);
    /** 直流工作点结果显示区。 */
    private final JTextArea dcArea = new JTextArea();
    /** 日志/状态区。 */
    private final JTextArea logArea = new JTextArea();
    /** 波形绘制组件。 */
    private final WaveformChart chart = new WaveformChart();
    /** 瞬态曲线选择列表模型。 */
    private final DefaultListModel<String> curveModel = new DefaultListModel<>();
    /** 瞬态曲线选择列表。 */
    private final JList<String> curveList = new JList<>(curveModel);
    /** 瞬态结果缓存（切换曲线选择时重绘）。 */
    private TransientResult transientResult;
    /** 最近一次解析的网表（用于读取分析指令参数）。 */
    private Netlist lastNetlist;
    /** 瞬态摘要标签（显示增益等信息）。 */
    private final JLabel summaryLabel = new JLabel("尚未运行瞬态分析");

    public SimulatorFrame() {
        super("电路模拟器 —— 共射放大电路");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1120, 720);
        setLocationRelativeTo(null);
        buildUi();
        loadBjtExample();
    }

    /** 构建界面。 */
    private void buildUi() {
        setLayout(new BorderLayout(6, 6));

        // 顶部控制栏
        JPanel control = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        JButton bjtBtn = new JButton("三极管示例");
        JButton ctrlSrcBtn = new JButton("受控源示例");
        JButton openBtn = new JButton("打开网表文件...");
        JButton clearBtn = new JButton("清除结果");
        JButton dcBtn = new JButton("直流工作点 (.op)");
        JButton tranBtn = new JButton("瞬态分析 (.tran)");

        bjtBtn.addActionListener(e -> loadBjtExample());
        ctrlSrcBtn.addActionListener(e -> loadControlledSourceExample());
        openBtn.addActionListener(e -> openFile());
        clearBtn.addActionListener(e -> clearResultsAndNotify());
        dcBtn.addActionListener(e -> runDc());
        tranBtn.addActionListener(e -> runTran());

        control.add(bjtBtn);
        control.add(ctrlSrcBtn);
        control.add(openBtn);
        control.add(clearBtn);
        control.add(dcBtn);
        control.add(tranBtn);
        add(control, BorderLayout.NORTH);

        // 左侧：网表编辑器
        netlistArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane editor = new JScrollPane(netlistArea);
        editor.setBorder(BorderFactory.createTitledBorder("网表 (自定义 SPICE 风格)"));

        // 右侧：结果标签页
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("直流工作点", buildDcPanel());
        tabs.addTab("瞬态波形", buildTranPanel());
        tabs.addTab("日志", buildLogPanel());

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editor, tabs);
        split.setResizeWeight(0.35);
        split.setDividerLocation(380);
        add(split, BorderLayout.CENTER);
    }

    /** 直流工作点面板。 */
    private JPanel buildDcPanel() {
        dcArea.setEditable(false);
        dcArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JScrollPane(dcArea), BorderLayout.CENTER);
        return p;
    }

    /** 瞬态波形面板：左侧曲线选择，中间波形图，顶部摘要。 */
    private JPanel buildTranPanel() {
        curveList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        curveList.addListSelectionListener(e -> refreshChart());
        JScrollPane curvePane = new JScrollPane(curveList);
        curvePane.setPreferredSize(new Dimension(180, 0));

        JPanel plotPanel = new JPanel(new BorderLayout());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(summaryLabel);
        plotPanel.add(top, BorderLayout.NORTH);
        plotPanel.add(chart, BorderLayout.CENTER);

        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, curvePane, plotPanel);
        sp.setResizeWeight(0.18);

        JPanel p = new JPanel(new BorderLayout());
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    /** 日志面板。 */
    private JPanel buildLogPanel() {
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JScrollPane(logArea), BorderLayout.CENTER);
        return p;
    }

    /** 载入内置三极管（非线性 BJT）示例网表。 */
    private void loadBjtExample() {
        clearResults();
        netlistArea.setText(Examples.CE_AMPLIFIER);
        netlistArea.setCaretPosition(0);
        log("已载入三极管示例");
    }

    /** 载入内置受控源（hybrid-π 等效）示例网表。 */
    private void loadControlledSourceExample() {
        clearResults();
        netlistArea.setText(Examples.SMALL_SIGNAL_CE_AMPLIFIER);
        netlistArea.setCaretPosition(0);
        log("已载入受控源示例");
    }

    /** 从文件打开网表。 */
    private void openFile() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                String text = Files.readString(fc.getSelectedFile().toPath(), StandardCharsets.UTF_8);
                clearResults();
                netlistArea.setText(text);
                netlistArea.setCaretPosition(0);
                log("已加载文件: " + fc.getSelectedFile().getName());
            } catch (IOException ex) {
                error("读取文件失败: " + ex.getMessage());
            }
        }
    }

    /** 解析并构建电路；失败返回 null。 */
    private Circuit buildCircuit() {
        try {
            lastNetlist = new NetlistParser().parse(netlistArea.getText());
            Circuit circuit = Circuit.build(lastNetlist);
            log("电路构建成功：节点 " + circuit.numNodes()
                    + " 个，器件 " + circuit.devices.size() + " 个");
            return circuit;
        } catch (Exception ex) {
            error("电路构建失败: " + ex.getMessage());
            return null;
        }
    }

    /** 从网表读取瞬态分析指令（.tran），缺省时返回空数组。 */
    private double[] tranParams() {
        for (Netlist.Analysis a : lastNetlist.analyses) {
            if (a.kind == Netlist.Analysis.Kind.TRAN) {
                return new double[] {a.tstep, a.tstop};
            }
        }
        return null;
    }

    /** 清除上一次运行的结果（直流工作点、瞬态波形与网表缓存）。 */
    private void clearResults() {
        transientResult = null;
        lastNetlist = null;
        dcArea.setText("");
        curveModel.clear();
        chart.setData(null, null);
        summaryLabel.setText("尚未运行瞬态分析");
    }

    /** “清除结果”按钮回调：清除结果并写一条日志。 */
    private void clearResultsAndNotify() {
        clearResults();
        log("已清除所有运行结果");
    }

    /** 运行直流工作点分析。 */
    private void runDc() {
        Circuit circuit = buildCircuit();
        if (circuit == null) {
            return;
        }
        try {
            DcResult dc = new Simulator(circuit).dc();
            StringBuilder sb = new StringBuilder();
            sb.append("=== 节点电压 ===\n");
            for (Map.Entry<String, Double> e : dc.nodeVoltages.entrySet()) {
                sb.append(String.format("  V(%s) = %.6f V%n", e.getKey(), e.getValue()));
            }
            sb.append("\n=== 器件电流 ===\n");
            for (Map.Entry<String, Double> e : dc.currents.entrySet()) {
                sb.append(String.format("  %s = %s%n", e.getKey(), formatCurrent(e.getValue())));
            }
            dcArea.setText(sb.toString());
            dcArea.setCaretPosition(0);
            log("直流工作点分析完成");
        } catch (Exception ex) {
            error("直流工作点分析失败: " + ex.getMessage());
        }
    }

    /** 运行瞬态分析。 */
    private void runTran() {
        Circuit circuit = buildCircuit();
        if (circuit == null) {
            return;
        }
        try {
            double[] params = tranParams();
            if (params == null) {
                error("网表中缺少 .tran 指令，无法进行瞬态分析");
                return;
            }
            transientResult = new Simulator(circuit).tran(params[0], params[1]);

            // 填充可选曲线：节点电压以 V(节点) 命名，器件的电流沿用其标签
            curveModel.clear();
            for (String node : transientResult.nodeVoltages.keySet()) {
                curveModel.addElement("V(" + node + ")");
            }
            for (String label : transientResult.currents.keySet()) {
                curveModel.addElement(label);
            }

            // 默认选中 V(in) 与 V(out)
            int inIdx = curveModel.indexOf("V(in)");
            int outIdx = curveModel.indexOf("V(out)");
            if (inIdx >= 0 && outIdx >= 0) {
                curveList.setSelectedIndices(new int[] {inIdx, outIdx});
            } else if (curveModel.size() > 0) {
                curveList.setSelectedIndex(0);
            }

            updateSummary();
            refreshChart();
            log("瞬态分析完成：时间点数 " + transientResult.time.length);
        } catch (Exception ex) {
            error("瞬态分析失败: " + ex.getMessage());
        }
    }

    /** 更新瞬态摘要（如增益）。 */
    private void updateSummary() {
        if (transientResult == null) {
            return;
        }
        double[] vin = transientResult.nodeVoltages.get("in");
        double[] vout = transientResult.nodeVoltages.get("out");
        if (vin == null || vout == null) {
            summaryLabel.setText("共 " + transientResult.time.length + " 个时间点");
            return;
        }
        double tStart = transientResult.time[transientResult.time.length - 1] * 0.5;
        double inPp = peakToPeak(vin, tStart);
        double outPp = peakToPeak(vout, tStart);
        if (inPp < 1e-12) {
            summaryLabel.setText(String.format("共 %d 个时间点（输入无明显交流信号，无法计算增益）",
                    transientResult.time.length));
            return;
        }
        double gain = outPp / inPp;
        summaryLabel.setText(String.format("共 %d 个时间点，电压增益 |Av| ≈ %.2f（输出与输入反相）",
                transientResult.time.length, gain));
    }

    /** 依据当前曲线选择重绘波形。 */
    private void refreshChart() {
        if (transientResult == null) {
            return;
        }
        Map<String, double[]> curves = new LinkedHashMap<>();
        for (String label : curveList.getSelectedValuesList()) {
            if (label.startsWith("V(")) {
                String node = label.substring(2, label.length() - 1);
                curves.put(label, transientResult.nodeVoltages.get(node));
            } else {
                curves.put(label, transientResult.currents.get(label));
            }
        }
        chart.setData(transientResult.time, curves);
    }

    /** 计算 t>=start 之后序列的峰峰值。 */
    private double peakToPeak(double[] values, double start) {
        double[] time = transientResult.time;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < time.length; i++) {
            if (time[i] < start) {
                continue;
            }
            min = Math.min(min, values[i]);
            max = Math.max(max, values[i]);
        }
        return max - min;
    }

    /** 电流格式化：自动换算 mA / µA。 */
    private static String formatCurrent(double amp) {
        double a = Math.abs(amp);
        String sign = amp < 0 ? "-" : "";
        if (a >= 1e-1) {
            return String.format("%s%.6f A", sign, a);
        } else if (a >= 1e-4) {
            return String.format("%s%.3f mA", sign, a * 1e3);
        } else if (a >= 1e-7) {
            return String.format("%s%.3f µA", sign, a * 1e6);
        } else {
            return String.format("%s%.3f nA", sign, a * 1e9);
        }
    }

    private void log(String msg) {
        logArea.append("[信息] " + msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void error(String msg) {
        logArea.append("[错误] " + msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
        JOptionPane.showMessageDialog(this, msg, "错误", JOptionPane.ERROR_MESSAGE);
    }

    /** 程序入口。 */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignore) {
                // 系统外观不可用时使用默认外观
            }
            new SimulatorFrame().setVisible(true);
        });
    }
}
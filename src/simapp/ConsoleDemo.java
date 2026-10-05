package simapp;

import simcore.analysis.DcResult;
import simcore.analysis.Simulator;
import simcore.analysis.TransientResult;
import simcore.circuit.Circuit;
import simcore.netlist.Netlist;
import simcore.netlist.NetlistParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 控制台演示程序：展示如何调用核心库完成直流工作点与瞬态分析。
 *
 * <p>用法：{@code java -cp out simapp.ConsoleDemo [网表文件]}
 * 未指定文件时使用内置的共射放大电路示例网表。</p>
 */
public final class ConsoleDemo {

    public static void main(String[] args) throws IOException {
        String text;
        if (args.length > 0) {
            text = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        } else {
            text = builtinExample();
        }

        Netlist netlist = new NetlistParser().parse(text);
        System.out.println("标题: " + netlist.title);
        System.out.println("顶层器件数: " + netlist.instances.size()
                + ", 子电路数: " + netlist.subckts.size()
                + ", 模型数: " + netlist.models.size());
        System.out.println();

        Circuit circuit = Circuit.build(netlist);
        System.out.println("展平后节点: " + circuit.nodeNames);
        System.out.println("器件数: " + circuit.devices.size());
        System.out.println();

        Simulator sim = new Simulator(circuit);

        // 直流工作点分析（与过程无关）
        DcResult dc = sim.dc();
        System.out.println("=== 直流工作点 (.op) ===");
        for (var e : dc.nodeVoltages.entrySet()) {
            System.out.printf("  V(%s) = %.6f V%n", e.getKey(), e.getValue());
        }
        System.out.println("  --- 支路/器件电流 ---");
        for (var e : dc.currents.entrySet()) {
            System.out.printf("  %s = %.6e A%n", e.getKey(), e.getValue());
        }
        System.out.println();

        // 瞬态分析（与过程有关）：参数取自网表 .tran 指令
        Netlist.Analysis tran = null;
        for (Netlist.Analysis a : netlist.analyses) {
            if (a.kind == Netlist.Analysis.Kind.TRAN) {
                tran = a;
                break;
            }
        }
        if (tran == null) {
            System.out.println("网表中缺少 .tran 指令，跳过瞬态分析");
        } else {
            TransientResult res = sim.tran(tran.tstep, tran.tstop);
            double[] time = res.time;
            double[] vin = res.nodeVoltages.get("in");
            double[] vout = res.nodeVoltages.get("out");

            System.out.println("=== 瞬态分析 (.tran) ===");
            System.out.println("时间点数: " + time.length);
            // 求最后 20% 时间的稳定波形峰峰值与电压增益
            double tStart = tran.tstop * 0.8;
            double inPp = peakToPeak(vin, time, tStart);
            double outPp = peakToPeak(vout, time, tStart);
            System.out.printf("  Vin 峰峰值 = %.6f V%n", inPp);
            System.out.printf("  Vout 峰峰值 = %.6f V%n", outPp);
            System.out.printf("  电压增益 |Av| = %.3f%n", outPp / inPp);
            System.out.println();

            System.out.println("=== 若干采样点 V(in)/V(out) ===");
            for (int i = 0; i < time.length; i += Math.max(1, time.length / 8)) {
                System.out.printf("  t=%.5f s  V(in)=%.6f V  V(out)=%.6f V%n",
                        time[i], vin[i], vout[i]);
            }
        }
    }

    /** 计算 t>=start 之后序列的峰峰值。 */
    private static double peakToPeak(double[] values, double[] time, double start) {
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

    /** 联网表示例网表（与 examples/ce_amplifier.net 一致）。 */
    private static String builtinExample() {
        return Examples.CE_AMPLIFIER;
    }
}